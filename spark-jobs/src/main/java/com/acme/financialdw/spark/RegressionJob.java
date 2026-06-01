package com.acme.financialdw.spark;

import org.apache.spark.ml.Pipeline;
import org.apache.spark.ml.PipelineModel;
import org.apache.spark.ml.PipelineStage;
import org.apache.spark.ml.evaluation.RegressionEvaluator;
import org.apache.spark.ml.feature.Normalizer;
import org.apache.spark.ml.feature.VectorAssembler;
import org.apache.spark.ml.regression.LinearRegression;
import org.apache.spark.ml.regression.LinearRegressionModel;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.SparkSession;

import static org.apache.spark.sql.functions.*;

/**
 * Spark Use Case B — Linear Regression Job (Spark MLlib, Java API)
 *
 * <p>Reads the {@code time_series} collection from MongoDB and trains a
 * {@link LinearRegression} model to predict the <em>close price</em> from
 * five numeric features:
 * <ol>
 *   <li>open price</li>
 *   <li>low price</li>
 *   <li>high price</li>
 *   <li>dayOfYear — ordinal day within the year (1–366)</li>
 *   <li>year — calendar year extracted from businessDate</li>
 * </ol>
 *
 * <h3>Pipeline</h3>
 * <ol>
 *   <li>{@link VectorAssembler} → raw feature vector</li>
 *   <li>{@link Normalizer} (L2) → normalised feature vector</li>
 *   <li>{@link LinearRegression} → trained model</li>
 * </ol>
 *
 * <h3>Outputs (written to MongoDB)</h3>
 * <ul>
 *   <li>{@code regression_dataset}  — cleaned, feature-enriched dataset used for training</li>
 *   <li>{@code regression_predictions} — test-set rows with {@code prediction} column appended</li>
 *   <li>{@code regression_results}  — single-row metrics document (RMSE, MAE, R²,
 *       coefficients, intercept)</li>
 * </ul>
 *
 * <h3>Run via spark-submit</h3>
 * <pre>
 * spark-submit \
 *   --class com.acme.financialdw.spark.RegressionJob \
 *   --master local[*] \
 *   --conf spark.mongodb.read.connection.uri=mongodb://admin:secret@mongo:27017/financial_dw?authSource=admin \
 *   --conf spark.mongodb.write.connection.uri=mongodb://admin:secret@mongo:27017/financial_dw?authSource=admin \
 *   /opt/spark-jobs/spark-jobs-1.0.0-shaded.jar
 * </pre>
 */
public class RegressionJob {

    // Train / test split ratio
    private static final double TRAIN_RATIO = 0.8;
    private static final long   RANDOM_SEED = 42L;

    public static void main(String[] args) {

        String mongoUri = System.getenv().getOrDefault(
                "MONGO_URI",
                "mongodb://admin:secret@mongo:27017/financial_dw?authSource=admin");

        SparkSession spark = SparkSession.builder()
                .appName("FinancialDW-RegressionJob")
                .config("spark.mongodb.read.connection.uri",  mongoUri)
                .config("spark.mongodb.write.connection.uri", mongoUri)
                .getOrCreate();

        spark.sparkContext().setLogLevel("WARN");

        try {
            run(spark);
        } finally {
            spark.stop();
        }
    }

    static void run(SparkSession spark) {

        // ── 1. Load & clean ───────────────────────────────────────────────────
        Dataset<Row> raw = spark.read()
                .format("mongodb")
                .option("collection", "time_series")
                .load();

        Dataset<Row> active = raw.filter(
                col("deleted").isNull().or(col("deleted").equalTo(false)));

        // Bitfinex indicators are lowercase: open/close/high/low/volume.
        // (Spark struct access is strict — referencing a non-existent field errors,
        //  so we point straight at the fields that exist.)
        Dataset<Row> resolved = active
                .withColumn("close_price", col("indicators.close").cast("double"))
                .withColumn("open_price",  col("indicators.open").cast("double"))
                .withColumn("low_price",   col("indicators.low").cast("double"))
                .withColumn("high_price",  col("indicators.high").cast("double"));

        // Parse businessDate (stored as LocalDate → string "YYYY-MM-DD" in Spark)
        // businessDate is a Mongo date read back as a Spark timestamp → cast straight to date.
        Dataset<Row> withDates = resolved
                .withColumn("biz_date",   col("businessDate").cast("date"))
                .withColumn("dayOfYear",  dayofyear(col("biz_date")).cast("double"))
                .withColumn("year",       year(col("biz_date")).cast("double"));

        // Keep only rows with all five features + label present
        Dataset<Row> clean = withDates
                .select(
                        col("assetId"),
                        col("dataSourceId"),
                        col("biz_date"),
                        col("open_price"),
                        col("low_price"),
                        col("high_price"),
                        col("dayOfYear"),
                        col("year"),
                        col("close_price").alias("label"))
                .filter(col("label").isNotNull()
                        .and(col("open_price").isNotNull())
                        .and(col("low_price").isNotNull())
                        .and(col("high_price").isNotNull()));

        if (clean.isEmpty()) {
            System.err.println("[RegressionJob] No usable rows found — check indicator field names.");
            return;
        }

        // ── 2. Write the cleaned dataset ──────────────────────────────────────
        clean.write()
                .format("mongodb")
                .option("collection", "regression_dataset")
                .mode(SaveMode.Overwrite)
                .save();

        // ── 3. Train / test split ─────────────────────────────────────────────
        Dataset<Row>[] splits = clean.randomSplit(
                new double[]{TRAIN_RATIO, 1.0 - TRAIN_RATIO}, RANDOM_SEED);
        Dataset<Row> trainSet = splits[0];
        Dataset<Row> testSet  = splits[1];

        System.out.printf("[RegressionJob] Train rows: %d  Test rows: %d%n",
                trainSet.count(), testSet.count());

        // ── 4. Build ML pipeline ──────────────────────────────────────────────
        String[] featureCols = {"open_price", "low_price", "high_price", "dayOfYear", "year"};

        VectorAssembler assembler = new VectorAssembler()
                .setInputCols(featureCols)
                .setOutputCol("rawFeatures");

        Normalizer normalizer = new Normalizer()
                .setInputCol("rawFeatures")
                .setOutputCol("features")
                .setP(2.0);   // L2 norm

        LinearRegression lr = new LinearRegression()
                .setFeaturesCol("features")
                .setLabelCol("label")
                .setPredictionCol("prediction")
                .setMaxIter(100)
                .setRegParam(0.01)
                .setElasticNetParam(0.0);  // Ridge (L2 only)

        Pipeline pipeline = new Pipeline()
                .setStages(new PipelineStage[]{assembler, normalizer, lr});

        // ── 5. Fit ────────────────────────────────────────────────────────────
        PipelineModel model = pipeline.fit(trainSet);

        // ── 6. Evaluate on test set ───────────────────────────────────────────
        Dataset<Row> predictions = model.transform(testSet);

        RegressionEvaluator evaluator = new RegressionEvaluator()
                .setLabelCol("label")
                .setPredictionCol("prediction");

        double rmse = evaluator.setMetricName("rmse").evaluate(predictions);
        double mae  = evaluator.setMetricName("mae").evaluate(predictions);
        double r2   = evaluator.setMetricName("r2").evaluate(predictions);

        System.out.printf("[RegressionJob] RMSE=%.4f  MAE=%.4f  R²=%.4f%n", rmse, mae, r2);

        // ── 7. Extract model parameters ───────────────────────────────────────
        LinearRegressionModel lrModel =
                (LinearRegressionModel) model.stages()[2];

        double   intercept    = lrModel.intercept();
        double[] coefficients = lrModel.coefficients().toArray();

        // ── 8. Write predictions ──────────────────────────────────────────────
        predictions
                .select("assetId", "dataSourceId", "biz_date",
                        "open_price", "low_price", "high_price",
                        "dayOfYear", "year", "label", "prediction")
                .write()
                .format("mongodb")
                .option("collection", "regression_predictions")
                .mode(SaveMode.Overwrite)
                .save();

        // ── 9. Write metrics summary ──────────────────────────────────────────
        // Build a single-row dataframe with the model metrics
        org.apache.spark.sql.types.StructType metricsSchema =
                new org.apache.spark.sql.types.StructType()
                        .add("rmse",           org.apache.spark.sql.types.DataTypes.DoubleType)
                        .add("mae",            org.apache.spark.sql.types.DataTypes.DoubleType)
                        .add("r2",             org.apache.spark.sql.types.DataTypes.DoubleType)
                        .add("intercept",      org.apache.spark.sql.types.DataTypes.DoubleType)
                        .add("coef_open",      org.apache.spark.sql.types.DataTypes.DoubleType)
                        .add("coef_low",       org.apache.spark.sql.types.DataTypes.DoubleType)
                        .add("coef_high",      org.apache.spark.sql.types.DataTypes.DoubleType)
                        .add("coef_dayOfYear", org.apache.spark.sql.types.DataTypes.DoubleType)
                        .add("coef_year",      org.apache.spark.sql.types.DataTypes.DoubleType);

        java.util.List<Row> metricsRows = java.util.List.of(
                org.apache.spark.sql.RowFactory.create(
                        rmse, mae, r2, intercept,
                        coefficients[0], coefficients[1],
                        coefficients[2], coefficients[3],
                        coefficients[4]
                )
        );

        Dataset<Row> metrics = spark.createDataFrame(metricsRows, metricsSchema);

        metrics.withColumn("computedAt", current_timestamp())
                .write()
                .format("mongodb")
                .option("collection", "regression_results")
                .mode(SaveMode.Overwrite)
                .save();

        System.out.println("[RegressionJob] Done.");
    }
}
