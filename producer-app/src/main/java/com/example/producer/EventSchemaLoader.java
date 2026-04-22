package com.example.producer;

import java.io.IOException;
import java.io.InputStream;
import org.apache.avro.Schema;
import org.springframework.stereotype.Component;

@Component
public class EventSchemaLoader {
  private static final String SCHEMA_PATH = "/avro/platform-event.avsc";
  private final Schema schema;

  public EventSchemaLoader() {
    this.schema = loadSchema();
  }

  public Schema getSchema() {
    return schema;
  }

  private Schema loadSchema() {
    try (InputStream inputStream = EventSchemaLoader.class.getResourceAsStream(SCHEMA_PATH)) {
      if (inputStream == null) {
        throw new IllegalStateException("Schema resource " + SCHEMA_PATH + " was not found");
      }
      return new Schema.Parser().parse(inputStream);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to load Avro schema", exception);
    }
  }
}
