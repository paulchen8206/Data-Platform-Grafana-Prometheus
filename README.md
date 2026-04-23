# Data Platform Monitoring (Grafana + Prometheus + Docker)

This project provides a simple data platform stack and monitoring setup using Docker:

- Java producer application publishes events to Kafka.
- Spark Structured Streaming Java job consumes Kafka events, enriches them with processing metadata, and writes Iceberg tables into MinIO.
- Prometheus scrapes runtime metrics.
- Prometheus evaluates alert rules and sends alerts to Alertmanager.
- Grafana visualizes system and Kafka metrics with a pre-provisioned dashboard.

## Stack Components

- Kafka + Zookeeper
- Schema Registry
- MinIO object storage
- Java producer service
- Spark master + worker + Spark streaming job
- Prometheus
- Alertmanager
- Grafana
- Loki
- Promtail
- Kafka UI
- Kafka exporter
- cAdvisor
- Node exporter

## Architecture Component Diagram

~~~mermaid
flowchart LR
  Producer[Java Producer Service]
  Kafka[(Kafka)]
  Spark[Spark Streaming Job]

  subgraph Monitoring[Monitoring and Alerting]
    Prometheus[(Prometheus)]
    Alertmanager[(Alertmanager)]
    Grafana[(Grafana)]
  end

  subgraph Exporters[Infrastructure and Runtime Exporters]
    KExp[Kafka Exporter]
    CAdvisor[cAdvisor]
    NodeExp[Node Exporter]
  end

  Producer -->|publishes events| Kafka
  Kafka -->|consumed stream| Spark

  Kafka -->|broker metrics| KExp
  KExp -->|scrape| Prometheus
  CAdvisor -->|container metrics| Prometheus
  NodeExp -->|host metrics| Prometheus

  Prometheus -->|alerts| Alertmanager
  Prometheus -->|query| Grafana
~~~

## Grafana-Loki-Promtail End-to-End Diagram

~~~mermaid
flowchart LR
  subgraph Workloads[Application and Platform Containers]
    ProducerC[producer-app]
    SparkC[spark-job]
    KafkaC[kafka]
    OtherC[other docker services]
  end

  subgraph Collection[Log Collection]
    DockerLogs[/Docker JSON logs/]
    Promtail[promtail]
  end

  subgraph Storage[Log Storage and Rules]
    Loki[(loki)]
    LokiRules[Loki ruler]
  end

  subgraph Visualization[Visualization and Alerting]
    Grafana[(grafana)]
    Alertmanager[(alertmanager)]
  end

  ProducerC --> DockerLogs
  SparkC --> DockerLogs
  KafkaC --> DockerLogs
  OtherC --> DockerLogs

  DockerLogs -->|scrape + relabel + labels| Promtail
  Promtail -->|push log streams| Loki

  Grafana -->|Loki datasource query| Loki
  Grafana -->|Explore / Dashboards / Log panels| Users[Operators]

  LokiRules -->|evaluate log rules| Loki
  LokiRules -->|fire alerts| Alertmanager
~~~

### Runbook Sequence Diagram (Event-by-Event)

~~~mermaid
sequenceDiagram
  autonumber
  participant App as Producer/Spark Container
  participant Docker as Docker Log Driver
  participant Promtail as Promtail
  participant Loki as Loki
  participant Grafana as Grafana
  participant Operator as On-call Operator
  participant Ruler as Loki Ruler
  participant AM as Alertmanager

  App->>Docker: Write stdout/stderr log line
  Docker-->>Promtail: Expose container JSON log file
  Promtail->>Promtail: Apply scrape config and relabels
  Promtail->>Loki: Push labeled log stream
  Loki-->>Promtail: 204 accepted

  Operator->>Grafana: Open Explore with service/level filters
  Grafana->>Loki: Query logs (LogQL)
  Loki-->>Grafana: Return matching log lines
  Grafana-->>Operator: Show timeline + context

  Ruler->>Loki: Evaluate LogQL alert rule window
  Loki-->>Ruler: Return query result

  alt Rule condition met
    Ruler->>AM: Send firing alert payload
    AM-->>Operator: Dispatch notification (Slack/Email)
    Operator->>Grafana: Pivot from alert to logs
    Grafana->>Loki: Query around alert timestamp
    Loki-->>Grafana: Return correlated log evidence
  else Rule condition not met
    Ruler->>Ruler: Keep monitoring next interval
  end
~~~

## Project Structure

~~~text
.
├── Makefile
├── docker-compose.yml
├── producer-app/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/example/producer/EventProducerApplication.java
├── spark-job/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/example/spark/SparkKafkaJob.java
├── prometheus/
│   ├── alert_rules.yml
│   └── prometheus.yml
├── alertmanager/
│   └── alertmanager.yml
├── loki/
│   ├── loki-config.yml
│   └── rules/
├── promtail/
│   └── promtail-config.yml
└── grafana/
    ├── dashboards/data-platform-overview.json
    └── provisioning/
        ├── dashboards/dashboard.yml
        └── datasources/datasource.yml
~~~

## Unified Operations (Makefile)

Use the Makefile to run the platform with one consistent operational workflow.

~~~bash
make help
~~~

Run one-shot platform health checks (services + Prometheus rules + Loki labels + Kafka topics/groups):

~~~bash
make health
~~~

## Run the Platform

1. Make sure Docker Desktop is running.
2. From this folder, start all services:

~~~bash
make up
~~~

3. Check status:

~~~bash
make ps
~~~

4. Optional split startup:

~~~bash
make up-core
make up-monitoring
~~~

## Access UIs

- Grafana: http://localhost:3000
  - Username: admin
  - Password: admin
  - Dashboard: Data Platform / Data Platform Monitoring Overview
- Kafka UI: http://localhost:8085
- Schema Registry: http://localhost:8086
- MinIO API: http://localhost:9000
- MinIO Console: http://localhost:9001
  - Username: minioadmin
  - Password: minioadmin
- Prometheus: http://localhost:9090
- Alertmanager: http://localhost:9093
- Loki API: http://localhost:3100
- Spark Master UI: http://localhost:8080

## Configure Notifications

Alertmanager is preconfigured with receiver routing:

- `severity=critical` -> Slack critical channel + email on-call
- `severity=warning` -> Slack warning channel

Before using notifications, update placeholders in `alertmanager/alertmanager.yml`:

- Replace `smtp.example.com:587`, `alerts@example.com`, and `REPLACE_WITH_SMTP_PASSWORD`.
- Replace Slack webhook URLs:
  - `https://hooks.slack.com/services/REPLACE/CRITICAL/WEBHOOK`
  - `https://hooks.slack.com/services/REPLACE/WARNING/WEBHOOK`
- Replace email receiver `oncall@example.com`.

Then restart Alertmanager:

~~~bash
docker compose up -d alertmanager
~~~

## Operations Procedure

Common daily procedure:

1. Start stack:

~~~bash
make up
~~~

2. Check services:

~~~bash
make ps
~~~

Optional one-shot operational health check:

~~~bash
make health
~~~

3. Follow key logs:

~~~bash
make logs-spark
make logs-producer
~~~

4. Validate data path:

~~~bash
make verify-iceberg
~~~

5. Validate monitoring path:

~~~bash
make health
make rules-prometheus
make rules-loki
make check-loki
~~~

6. Stop stack:

~~~bash
make down
~~~

## Useful Commands

- Tail producer logs:

~~~bash
make logs-producer
~~~

- Tail Spark job logs:

~~~bash
make logs-spark
~~~

- Run Iceberg verification query (decoded fields):

~~~bash
make verify-iceberg
~~~

- Show Prometheus and Loki rules:

~~~bash
make rules-prometheus
make rules-loki
~~~

- Run one-shot health checks:

~~~bash
make health
~~~

- Check Kafka topics and consumer groups:

~~~bash
make kafka-topics
make kafka-consumer-groups
~~~

- Check lag series for alert filter topic:

~~~bash
make check-kafka-alert-series
~~~

- Stop and remove containers:

~~~bash
make down
~~~

- Stop and remove containers + volumes:

~~~bash
make down-v
~~~

- Reset Spark checkpoints (keep `.gitkeep`):

~~~bash
make reset-checkpoints
~~~

## Troubleshooting

### Kafka panels in Grafana show no data (Kafka Brokers, Topic Partitions, Event Count)

**Symptom:** The Kafka-related Grafana panels are empty even though the producer is publishing events.

**Cause:** `kafka-exporter` started before Kafka's broker port was ready, hit a connection refused error, and exited. Prometheus then had no Kafka metrics to scrape.

**Fix (already applied in `docker-compose.yml`):**
- A healthcheck was added to the `kafka` service so that dependent services wait for the broker to be ready.
- `kafka-exporter` now uses `depends_on: kafka: condition: service_healthy` and `restart: unless-stopped`.

**If you see this on a fresh run**, force-recreate the exporter:

~~~bash
docker compose up -d --force-recreate kafka-exporter
~~~

Then verify Kafka metrics are present in Prometheus:

~~~bash
curl -sG 'http://localhost:9090/api/v1/query' \
  --data-urlencode 'query=kafka_brokers' | jq '.data.result'
~~~

A result with `"value": [..., "1"]` confirms the exporter is scraping correctly. Grafana panels will populate within one scrape interval (10 seconds).

## Notes

- The producer sends one Avro-encoded event per second to the Kafka topic platform-events and auto-registers its schema in Schema Registry under `platform-events-value`.
- Spark Structured Streaming tracks offsets in checkpoint files under `spark-job/checkpoints/` rather than committing offsets as a regular Kafka consumer group, so a Spark group may not appear in Kafka UI.
- The Spark job writes pass-through event records to the Iceberg table `lakehouse.platform.platform_events` in the MinIO bucket `platform-warehouse`.
- Each output row is decoded from the registered Avro schema and enriched with `processed_at`, plus Kafka timestamp, partition, and offset metadata.
- The Spark job stores Structured Streaming checkpoints under `spark-job/checkpoints/` so it can recover offsets and state across container restarts.
- The Spark image preloads Spark Kafka/Avro/Iceberg/S3 runtime jars during build, so startup does not download dependencies at runtime.
- The `iceberg-reader` profile runs a one-shot Spark SQL query to show decoded event fields (`event_id`, `event_type`, `event_ts`, `value`) from Iceberg.
- `schema-registry` has a healthcheck and both `producer` and `spark-job` wait for it to be healthy before starting, reducing startup race failures.
- Metrics are collected from exporters and available in Prometheus for Grafana dashboards.
- Alert rules are defined in prometheus/alert_rules.yml and routed by Alertmanager.
- Includes a Kafka alert `KafkaTopicNoEvents2m` that fires when topic `platform-events` has no new events for 2 minutes.
- Includes a Kafka lag alert `KafkaConsumerLagHigh` scoped to topic `platform-events`.
- Includes a Loki log-based alert `SparkJobErrorOrExceptionLogs` that fires when spark-job logs contain `error` or `exception` in a 2 minute window.
- Includes a stricter Loki alert `SparkJobStackTraceCritical` that fires for stack-trace patterns (`ERROR`, `Exception`, `Caused by:`) for 5+ minutes.
- Loki and Promtail include ingestion/batching tuning to reduce transient 429 rate-limit drops.
- On macOS (Docker Desktop), node-exporter uses `/:/host:ro` volume mount without `rslave` to avoid mount propagation errors.

If you need to reset the Spark streaming state and re-read from the configured starting offsets, stop the job and remove the checkpoint contents under `spark-job/checkpoints/` before starting it again.

Iceberg metadata and data files are stored in MinIO under `s3a://platform-warehouse/iceberg`.
