package com.example.producer;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.springframework.util.StringUtils;

@Component
@Validated
@ConfigurationProperties(prefix = "app.producer")
public class ProducerProperties {
  @NotBlank
  private String bootstrapServers;
  @NotBlank
  private String topic;
  @NotBlank
  private String schemaRegistryUrl;
  @Positive
  private long publishIntervalMs;

  public String getBootstrapServers() {
    return bootstrapServers;
  }

  public void setBootstrapServers(String bootstrapServers) {
    this.bootstrapServers = bootstrapServers;
  }

  public String getTopic() {
    return topic;
  }

  public void setTopic(String topic) {
    this.topic = topic;
  }

  public String getSchemaRegistryUrl() {
    return schemaRegistryUrl;
  }

  public void setSchemaRegistryUrl(String schemaRegistryUrl) {
    this.schemaRegistryUrl = schemaRegistryUrl;
  }

  public long getPublishIntervalMs() {
    return publishIntervalMs;
  }

  public void setPublishIntervalMs(long publishIntervalMs) {
    this.publishIntervalMs = publishIntervalMs;
  }

  @PostConstruct
  void validateFailFast() {
    if (!StringUtils.hasText(bootstrapServers)) {
      throw new IllegalStateException("Invalid config: app.producer.bootstrap-servers must not be blank");
    }
    if (!StringUtils.hasText(topic)) {
      throw new IllegalStateException("Invalid config: app.producer.topic must not be blank");
    }
    if (!StringUtils.hasText(schemaRegistryUrl)) {
      throw new IllegalStateException("Invalid config: app.producer.schema-registry-url must not be blank");
    }
    if (publishIntervalMs <= 0) {
      throw new IllegalStateException("Invalid config: app.producer.publish-interval-ms must be > 0");
    }
  }
}
