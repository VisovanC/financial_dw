#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# run_jobs.sh — convenience wrapper to submit both Spark jobs
#
# Usage (from the project root, with docker compose running):
#
#   # Build the fat JAR first:
#   cd spark-jobs && mvn clean package -q && cd ..
#
#   # Submit both jobs:
#   ./spark-jobs/run_jobs.sh
#
#   # Submit a single job:
#   JOB=aggregation ./spark-jobs/run_jobs.sh
#   JOB=regression  ./spark-jobs/run_jobs.sh
# ---------------------------------------------------------------------------
set -euo pipefail

SPARK_MASTER="${SPARK_MASTER:-local[*]}"
MONGO_URI="${MONGO_URI:-mongodb://admin:secret@localhost:27017/financial_dw?authSource=admin}"
JAR="${JAR:-$(dirname "$0")/target/spark-jobs-1.0.0-shaded.jar}"
JOB="${JOB:-all}"

if [ ! -f "$JAR" ]; then
  echo "[run_jobs.sh] Fat JAR not found at: $JAR"
  echo "             Run: cd spark-jobs && mvn clean package"
  exit 1
fi

submit() {
  local class="$1"
  local label="$2"
  echo ""
  echo "======================================================================"
  echo " Submitting: $label"
  echo "======================================================================"
  /opt/spark/bin/spark-submit \
    --class "$class" \
    --master "$SPARK_MASTER" \
    --conf "spark.mongodb.read.connection.uri=$MONGO_URI" \
    --conf "spark.mongodb.write.connection.uri=$MONGO_URI" \
    --conf "spark.driver.extraJavaOptions=-Dfile.encoding=UTF-8" \
    "$JAR"
}

case "$JOB" in
  aggregation)
    submit "com.acme.financialdw.spark.AggregationJob" "Aggregation Job (Use Case A)"
    ;;
  regression)
    submit "com.acme.financialdw.spark.RegressionJob" "Regression Job (Use Case B)"
    ;;
  all|*)
    submit "com.acme.financialdw.spark.AggregationJob" "Aggregation Job (Use Case A)"
    submit "com.acme.financialdw.spark.RegressionJob"  "Regression Job (Use Case B)"
    ;;
esac

echo ""
echo "======================================================================"
echo " All Spark jobs completed successfully."
echo " Results written to MongoDB collections:"
echo "   analytics_totals       — aggregation output"
echo "   regression_dataset     — cleaned feature dataset"
echo "   regression_predictions — test-set predictions"
echo "   regression_results     — model metrics (RMSE, MAE, R², coefficients)"
echo "======================================================================"
