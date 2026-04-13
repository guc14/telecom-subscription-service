# Databricks Notebook — Telecom Subscription Analytics
# Language: Python (PySpark)
#
# Purpose:
#   Read subscription data exported by Azure Data Factory from Blob Storage,
#   run PySpark analytics, and produce business-level insights:
#     - Subscription count and revenue by plan
#     - Monthly activation trend
#     - Churn rate (CANCELLED vs ACTIVE)
#     - Top plans by active subscriber count
#
# How to use in Databricks:
#   1. Create a new Notebook in your Databricks workspace
#   2. Copy-paste each cell (separated by # COMMAND ----------)
#   3. Set storage_account_name and storage_account_key in Cell 1
#   4. Run All

# COMMAND ----------
# Cell 1: Configure Azure Blob Storage access

storage_account_name = "<your-storage-account>"   # e.g. telecomstorage001
storage_account_key  = "<your-storage-account-key>"
container_name       = "subscription-data"

spark.conf.set(
    f"fs.azure.account.key.{storage_account_name}.blob.core.windows.net",
    storage_account_key
)

blob_path = f"wasbs://{container_name}@{storage_account_name}.blob.core.windows.net/raw/subscriptions.csv"
print(f"Reading from: {blob_path}")

# COMMAND ----------
# Cell 2: Load data into Spark DataFrame

df = spark.read.format("csv") \
    .option("header", "true") \
    .option("inferSchema", "true") \
    .load(blob_path)

print(f"Total records loaded: {df.count()}")
df.printSchema()
df.show(5)

# COMMAND ----------
# Cell 3: Subscription count and monthly revenue by plan

from pyspark.sql.functions import col, count, sum as spark_sum, round as spark_round

plan_summary = df.groupBy("plan_name") \
    .agg(
        count("*").alias("total_subscribers"),
        count(col("status").eqNullSafe("ACTIVE")).alias("active_subscribers"),
        spark_round(spark_sum("monthly_fee_cents") / 100, 2).alias("monthly_revenue_cad")
    ) \
    .orderBy(col("total_subscribers").desc())

print("=== Subscription Summary by Plan ===")
plan_summary.show()

# COMMAND ----------
# Cell 4: Monthly activation trend

from pyspark.sql.functions import date_format, to_timestamp

activation_trend = df \
    .withColumn("activated_month", date_format(to_timestamp("activated_at"), "yyyy-MM")) \
    .groupBy("activated_month") \
    .agg(count("*").alias("new_subscriptions")) \
    .orderBy("activated_month")

print("=== Monthly Activation Trend ===")
activation_trend.show()

# COMMAND ----------
# Cell 5: Churn rate analysis

from pyspark.sql.functions import when, lit

churn_df = df.withColumn(
    "is_churned", when(col("status") == "CANCELLED", 1).otherwise(0)
)

total = churn_df.count()
churned = churn_df.filter(col("status") == "CANCELLED").count()
churn_rate = round(churned / total * 100, 2)

print(f"=== Churn Analysis ===")
print(f"Total Subscriptions : {total}")
print(f"Cancelled           : {churned}")
print(f"Churn Rate          : {churn_rate}%")

# COMMAND ----------
# Cell 6: Write processed results back to Blob Storage

output_path = f"wasbs://{container_name}@{storage_account_name}.blob.core.windows.net/processed/plan_summary"

plan_summary.write \
    .format("csv") \
    .option("header", "true") \
    .mode("overwrite") \
    .save(output_path)

print(f"Results written to: {output_path}")
print("Analysis complete.")
