package com.example.spark;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import java.util.Collections;
import org.springframework.stereotype.Component;

@Component
public class SchemaRegistrySchemaProvider {
  private final SparkJobProperties properties;

  public SchemaRegistrySchemaProvider(SparkJobProperties properties) {
    this.properties = properties;
  }

  public String fetchLatestSchema() throws Exception {
    CachedSchemaRegistryClient schemaRegistryClient =
        new CachedSchemaRegistryClient(properties.getSchemaRegistryUrl(), 10, Collections.emptyMap());
    try {
      return schemaRegistryClient.getLatestSchemaMetadata(properties.getSchemaSubject()).getSchema();
    } finally {
      schemaRegistryClient.close();
    }
  }
}
