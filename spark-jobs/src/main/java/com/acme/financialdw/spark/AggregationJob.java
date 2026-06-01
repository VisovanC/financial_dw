package com.acme.financialdw.spark;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.functions;

import static org.apache.spark.sql.functions.*;

/**
 * Spark Use Case A — Aggregation Job
 *
 * <p>Reads the {@code time_series} collection from MongoDB, extracts the year
 * from each {@code businessDate}, and computes per-partition-per-year summary
 * statistics for the {@code close} indicator:
 * <ul>
 *   <li>count of data points</li>
 *   <li>minimum close price</li>
 *   <li>maximum close price</li>
 *   <li>average close price</li>
 * </ul>
 *
 * <p>Results are written to the {@code analytics_totals} MongoDB collection.
 *
 * <h3>Run via spark-submit</h3>
 * <pre>
 * spark-submit \
 *   --class com.acme.financialdw.spark.AggregationJob \
 *   --master local[*] \
 *   --conf spark.mongodb.read.connection.uri=mongodb://admin:secret@mongo:27017/financial_dw?authSource=admin \
 *   --conf spark.mongodb.write.connection.uri=mongodb://admin:secret@mongo:27017/financial_dw?authSource=admin \
 *   /opt/spark-jobs/spark-jobs-1.0.0-shaded.jar
 * </pre>
 */
public class AggregationJob {

    public static void main(String[] args) {

        // ── Connection URI (override via --conf or environment variable) ───────
        String mongoUri = System.getenv().getOrDefault(
                "MONGO_URI",
                "mongodb://admin:secret@mongo:27017/financial_dw?authSource=admin");

        // ── Build SparkSession ─────────────────────────────────────────────────
        SparkSession spark = SparkSession.builder()
                .appName("FinancialDW-AggregationJob")
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

        // ── 1. Load time_series collection ────────────────────────────────────
        Dataset<Row> raw = spark.read()
                .format("mongodb")
                .option("collection", "time_series")
                .load();

        // ── 2. Filter out soft-deleted records ────────────────────────────────
        Dataset<Row> active = raw.filter(
                col("deleted").isNull().or(col("deleted").equalTo(false)));

        // ── 3. Extract year from businessDate
        //       businessDate is stored as a LocalDate → comes out as a string
        //       (YYYY-MM-DD) or date type depending on codec; year() handles both.
        // businessDate is stored as a Mongo date and read back as a Spark timestamp,
        // so cast straight to date (avoids ANSI parse errors from a "yyyy-MM-dd HH:mm:ss" string).
        Dataset<Row> withYear = active.withColumn("year",
                year(col("businessDate").cast("date")));

        // ── 4. Resolve the close price
        //       The indicators are stored as a nested document / map.
        //       Try the two most common field names used by the Nasdaq provider.
        // Bitfinex indicators are lowercase: open/close/high/low/volume.
        // (Spark struct access is strict — referencing a non-existent field errors,
        //  so we point straight at the field that exists rather than coalescing.)
        Dataset<Row> withClose = withYear
                .withColumn("close_price", col("indicators.close").cast("double"));

        // Drop rows where we couldn't resolve a close price
        Dataset<Row> withValidClose = withClose.filter(col("close_price").isNotNull());

        // ── 5. Aggregate per (assetId, dataSourceId, year) ───────────────────
        Dataset<Row> aggregated = withValidClose
                .groupBy("assetId", "dataSourceId", "year")
                .agg(
                        count("close_price").alias("count"),
                        min("close_price").alias("minClose"),
                        max("close_price").alias("maxClose"),
                        avg("close_price").alias("avgClose")
                )
                .withColumn("computedAt", current_timestamp());

        aggregated.show(20, false);   // helpful in the Spark logs

        // ── 6. Write results to analytics_totals ──────────────────────────────
        aggregated.write()
                .format("mongodb")
                .option("collection", "analytics_totals")
                .mode(SaveMode.Overwrite)
                .save();

        System.out.println("[AggregationJob] Done. Rows written: " + aggregated.count());
    }
}
