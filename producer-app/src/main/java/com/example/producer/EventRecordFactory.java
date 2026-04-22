package com.example.producer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.UUID;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.springframework.stereotype.Component;

@Component
public class EventRecordFactory {
  private static final String[] EVENT_TYPES = {
    "order-created",
    "payment-authorized",
    "shipment-scheduled",
    "refund-issued"
  };

  private final EventSchemaLoader eventSchemaLoader;
  private final Random random;

  public EventRecordFactory(EventSchemaLoader eventSchemaLoader) {
    this.eventSchemaLoader = eventSchemaLoader;
    this.random = new Random();
  }

  public EventEnvelope nextEvent() {
    String eventType = EVENT_TYPES[random.nextInt(EVENT_TYPES.length)];
    Instant eventTimestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    int metricValue = random.nextInt(1000);

    GenericRecord payload = new GenericData.Record(eventSchemaLoader.getSchema());
    payload.put("event_id", UUID.randomUUID().toString());
    payload.put("event_type", eventType);
    payload.put("event_ts", eventTimestamp.toEpochMilli());
    payload.put("value", metricValue);

    return new EventEnvelope(eventType, eventTimestamp, metricValue, payload);
  }

  public static final class EventEnvelope {
    private final String eventType;
    private final Instant eventTimestamp;
    private final int metricValue;
    private final GenericRecord payload;

    public EventEnvelope(
        String eventType,
        Instant eventTimestamp,
        int metricValue,
        GenericRecord payload) {
      this.eventType = eventType;
      this.eventTimestamp = eventTimestamp;
      this.metricValue = metricValue;
      this.payload = payload;
    }

    public String getEventType() {
      return eventType;
    }

    public Instant getEventTimestamp() {
      return eventTimestamp;
    }

    public int getMetricValue() {
      return metricValue;
    }

    public GenericRecord getPayload() {
      return payload;
    }
  }
}
