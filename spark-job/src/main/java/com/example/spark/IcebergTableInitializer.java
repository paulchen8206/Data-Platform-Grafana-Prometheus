package com.example.spark;

import org.apache.spark.sql.SparkSession;
import org.springframework.stereotype.Component;

@Component
public class IcebergTableInitializer {
  public static final String ICEBERG_NAMESPACE = "lakehouse.platform";
  public static final String ICEBERG_TABLE = ICEBERG_NAMESPACE + ".platform_events";

  private static final String CREATE_TABLE_STATEMENT =
      "CREATE TABLE IF NOT EXISTS %s ("
          + "raw_value STRING, "
          + "event_id STRING, "
          + "event_type STRING, "
          + "event_ts TIMESTAMP, "
          + "value INT, "
          + "kafka_timestamp TIMESTAMP, "
          + "kafka_partition INT, "
          + "kafka_offset BIGINT, "
          + "processed_at TIMESTAMP"
          + ") USING iceberg "
          + "PARTITIONED BY (days(processed_at))";

  public void initialize(SparkSession spark) {
    spark.sql("CREATE NAMESPACE IF NOT EXISTS " + ICEBERG_NAMESPACE);
    spark.sql(String.format(CREATE_TABLE_STATEMENT, ICEBERG_TABLE));
    ensureColumns(spark);
  }

  private void ensureColumns(SparkSession spark) {
    try {
      spark.sql(
          "ALTER TABLE "
              + ICEBERG_TABLE
              + " ADD COLUMNS (event_id STRING, event_ts TIMESTAMP, value INT)");
    } catch (Exception exception) {
      if (!exception.getMessage().contains("already exists")) {
        throw new IllegalStateException("Failed to evolve Iceberg table schema", exception);
      }
    }
  }
}
