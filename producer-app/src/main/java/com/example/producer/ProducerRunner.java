package com.example.producer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ProducerRunner implements CommandLineRunner {
  private static final Logger LOGGER = LoggerFactory.getLogger(ProducerRunner.class);

  private final EventRecordFactory eventRecordFactory;
  private final KafkaAvroProducerClient producerClient;
  private final ProducerProperties producerProperties;

  public ProducerRunner(
      EventRecordFactory eventRecordFactory,
      KafkaAvroProducerClient producerClient,
      ProducerProperties producerProperties) {
    this.eventRecordFactory = eventRecordFactory;
    this.producerClient = producerClient;
    this.producerProperties = producerProperties;
  }

  @Override
  public void run(String... args) throws Exception {
    while (true) {
      EventRecordFactory.EventEnvelope eventEnvelope = eventRecordFactory.nextEvent();
      producerClient.publish(producerProperties.getTopic(), eventEnvelope);
      LOGGER.info(
          "Produced event_id={} event_type={} event_ts={} value={}",
          eventEnvelope.getPayload().get("event_id"),
          eventEnvelope.getPayload().get("event_type"),
          eventEnvelope.getEventTimestamp(),
          eventEnvelope.getMetricValue());
      Thread.sleep(producerProperties.getPublishIntervalMs());
    }
  }
}
