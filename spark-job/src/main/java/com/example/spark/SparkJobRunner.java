package com.example.spark;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.streaming.StreamingQuery;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class SparkJobRunner implements CommandLineRunner {
  private final SchemaRegistrySchemaProvider schemaProvider;
  private final IcebergTableInitializer tableInitializer;
  private final KafkaToIcebergStreamProcessor streamProcessor;

  public SparkJobRunner(
      SchemaRegistrySchemaProvider schemaProvider,
      IcebergTableInitializer tableInitializer,
      KafkaToIcebergStreamProcessor streamProcessor) {
    this.schemaProvider = schemaProvider;
    this.tableInitializer = tableInitializer;
    this.streamProcessor = streamProcessor;
  }

  @Override
  public void run(String... args) throws Exception {
    SparkSession spark = SparkSession.builder().appName("Spark Kafka Iceberg Writer").getOrCreate();

    String valueSchema = schemaProvider.fetchLatestSchema();
    tableInitializer.initialize(spark);

    Dataset<Row> enrichedEvents = streamProcessor.buildEnrichedEvents(spark, valueSchema);
    StreamingQuery query = streamProcessor.startWrite(enrichedEvents);
    query.awaitTermination();
  }
}
