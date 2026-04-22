package com.example.spark;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.springframework.util.StringUtils;

@Component
@Validated
@ConfigurationProperties(prefix = "app.spark-job")
public class SparkJobProperties {
  @NotBlank
  private String schemaRegistryUrl;
  @NotBlank
  private String schemaSubject;
  @NotBlank
  private String kafkaBootstrapServers;
  @NotBlank
  private String kafkaTopic;
  @NotBlank
  private String kafkaConsumerGroupId;
  @NotBlank
  private String checkpointLocation;

  public String getSchemaRegistryUrl() {
    return schemaRegistryUrl;
  }

  public void setSchemaRegistryUrl(String schemaRegistryUrl) {
    this.schemaRegistryUrl = schemaRegistryUrl;
  }

  public String getSchemaSubject() {
    return schemaSubject;
  }

  public void setSchemaSubject(String schemaSubject) {
    this.schemaSubject = schemaSubject;
  }

  public String getKafkaBootstrapServers() {
    return kafkaBootstrapServers;
  }

  public void setKafkaBootstrapServers(String kafkaBootstrapServers) {
    this.kafkaBootstrapServers = kafkaBootstrapServers;
  }

  public String getKafkaTopic() {
    return kafkaTopic;
  }

  public void setKafkaTopic(String kafkaTopic) {
    this.kafkaTopic = kafkaTopic;
  }

  public String getKafkaConsumerGroupId() {
    return kafkaConsumerGroupId;
  }

  public void setKafkaConsumerGroupId(String kafkaConsumerGroupId) {
    this.kafkaConsumerGroupId = kafkaConsumerGroupId;
  }

  public String getCheckpointLocation() {
    return checkpointLocation;
  }

  public void setCheckpointLocation(String checkpointLocation) {
    this.checkpointLocation = checkpointLocation;
  }

  @PostConstruct
  void validateFailFast() {
    if (!StringUtils.hasText(schemaRegistryUrl)) {
      throw new IllegalStateException("Invalid config: app.spark-job.schema-registry-url must not be blank");
    }
    if (!StringUtils.hasText(schemaSubject)) {
      throw new IllegalStateException("Invalid config: app.spark-job.schema-subject must not be blank");
    }
    if (!StringUtils.hasText(kafkaBootstrapServers)) {
      throw new IllegalStateException("Invalid config: app.spark-job.kafka-bootstrap-servers must not be blank");
    }
    if (!StringUtils.hasText(kafkaTopic)) {
      throw new IllegalStateException("Invalid config: app.spark-job.kafka-topic must not be blank");
    }
    if (!StringUtils.hasText(kafkaConsumerGroupId)) {
      throw new IllegalStateException("Invalid config: app.spark-job.kafka-consumer-group-id must not be blank");
    }
    if (!StringUtils.hasText(checkpointLocation)) {
      throw new IllegalStateException("Invalid config: app.spark-job.checkpoint-location must not be blank");
    }
  }
}
