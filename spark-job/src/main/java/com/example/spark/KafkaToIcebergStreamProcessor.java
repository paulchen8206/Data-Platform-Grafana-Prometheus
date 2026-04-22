package com.example.spark;

import java.nio.ByteBuffer;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.streaming.StreamingQuery;
import org.apache.spark.sql.types.DataTypes;
import org.springframework.stereotype.Component;

@Component
public class KafkaToIcebergStreamProcessor {
  private static final String STRIP_UDF_NAME = "stripSchemaRegistryHeader";

  private static final UDF1<byte[], byte[]> STRIP_SCHEMA_REGISTRY_HEADER =
      value -> {
        if (value == null || value.length <= 5) {
          return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(value);
        buffer.get();
        buffer.getInt();
        byte[] payload = new byte[buffer.remaining()];
        buffer.get(payload);
        return payload;
      };

  private final SparkJobProperties properties;

  public KafkaToIcebergStreamProcessor(SparkJobProperties properties) {
    this.properties = properties;
  }

  public Dataset<Row> buildEnrichedEvents(SparkSession spark, String avroSchema) {
    spark.udf().register(STRIP_UDF_NAME, STRIP_SCHEMA_REGISTRY_HEADER, DataTypes.BinaryType);

    Dataset<Row> kafkaRows =
        spark.readStream()
            .format("kafka")
            .option("kafka.bootstrap.servers", properties.getKafkaBootstrapServers())
            .option("subscribe", properties.getKafkaTopic())
        .option("kafka.group.id", properties.getKafkaConsumerGroupId())
        .option("kafka.enable.auto.commit", "true")
        .option("kafka.auto.commit.interval.ms", "1000")
            .option("startingOffsets", "latest")
            .load();

    return kafkaRows
        .select(
            org.apache.spark.sql.avro.functions.from_avro(
                    org.apache.spark.sql.functions.callUDF(
                        STRIP_UDF_NAME, org.apache.spark.sql.functions.col("value")),
                    avroSchema)
                .alias("event"),
            org.apache.spark.sql.functions.expr("CAST(stripSchemaRegistryHeader(value) AS STRING)")
                .alias("raw_value"),
            org.apache.spark.sql.functions.col("timestamp").alias("kafka_timestamp"),
            org.apache.spark.sql.functions.col("partition").alias("kafka_partition"),
            org.apache.spark.sql.functions.col("offset").alias("kafka_offset"))
        .withColumn("processed_at", functions.current_timestamp())
        .select(
            org.apache.spark.sql.functions.col("raw_value"),
            org.apache.spark.sql.functions.col("event.event_id").alias("event_id"),
            org.apache.spark.sql.functions.col("event.event_type").alias("event_type"),
            org.apache.spark.sql.functions.col("event.event_ts").cast("timestamp").alias("event_ts"),
            org.apache.spark.sql.functions.col("event.value").alias("value"),
            functions.col("kafka_timestamp"),
            functions.col("kafka_partition"),
            functions.col("kafka_offset"),
            functions.col("processed_at"));
  }

  public StreamingQuery startWrite(Dataset<Row> enrichedEvents) throws Exception {
    return enrichedEvents
        .writeStream()
        .queryName("platform-events-avro-iceberg-writer")
        .outputMode("append")
        .format("iceberg")
        .option("checkpointLocation", properties.getCheckpointLocation())
        .option("fanout-enabled", "true")
        .toTable(IcebergTableInitializer.ICEBERG_TABLE);
  }
}
