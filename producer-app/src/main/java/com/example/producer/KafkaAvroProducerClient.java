package com.example.producer;

import jakarta.annotation.PreDestroy;
import java.util.Properties;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Component;

@Component
public class KafkaAvroProducerClient {
  private final KafkaProducer<String, GenericRecord> producer;

  public KafkaAvroProducerClient(ProducerProperties producerProperties) {
    this.producer = new KafkaProducer<>(buildProducerProperties(producerProperties));
  }

  public void publish(String topic, EventRecordFactory.EventEnvelope eventEnvelope) {
    ProducerRecord<String, GenericRecord> record =
        new ProducerRecord<>(topic, eventEnvelope.getEventType(), eventEnvelope.getPayload());
    producer.send(record);
    producer.flush();
  }

  @PreDestroy
  public void close() {
    producer.close();
  }

  private Properties buildProducerProperties(ProducerProperties producerProperties) {
    Properties properties = new Properties();
    properties.put("bootstrap.servers", producerProperties.getBootstrapServers());
    properties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    properties.put("value.serializer", "io.confluent.kafka.serializers.KafkaAvroSerializer");
    properties.put("schema.registry.url", producerProperties.getSchemaRegistryUrl());
    properties.put("auto.register.schemas", "true");
    properties.put("acks", "all");
    properties.put("retries", "5");
    return properties;
  }
}
