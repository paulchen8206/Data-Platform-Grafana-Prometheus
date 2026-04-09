# Data Platform Monitoring (Grafana + Prometheus + Docker)

This project provides a simple data platform stack and monitoring setup using Docker:

- Java producer application publishes events to Kafka.
- Spark Structured Streaming Java job consumes Kafka events and aggregates counts by event type.
- Prometheus scrapes runtime metrics.
- Prometheus evaluates alert rules and sends alerts to Alertmanager.
- Grafana visualizes system and Kafka metrics with a pre-provisioned dashboard.

## Stack Components

- Kafka + Zookeeper
- Java producer service
- Spark master + worker + Spark streaming job
- Prometheus
- Alertmanager
- Grafana
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

## Project Structure

~~~text
.
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
└── grafana/
    ├── dashboards/data-platform-overview.json
    └── provisioning/
        ├── dashboards/dashboard.yml
        └── datasources/datasource.yml
~~~

## Run the Platform

1. Make sure Docker Desktop is running.
2. From this folder, start all services:

~~~bash
docker compose up -d --build
~~~

3. Check status:

~~~bash
docker compose ps
~~~

## Access UIs

- Grafana: http://localhost:3000
  - Username: admin
  - Password: admin
  - Dashboard: Data Platform / Data Platform Monitoring Overview
- Prometheus: http://localhost:9090
- Alertmanager: http://localhost:9093
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

## Useful Commands

- Tail producer logs:

~~~bash
docker compose logs -f producer
~~~

- Tail Spark job logs:

~~~bash
docker compose logs -f spark-job
~~~

- Stop and remove containers:

~~~bash
docker compose down
~~~

- Stop and remove containers + volumes:

~~~bash
docker compose down -v
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

- The producer sends one event per second to the Kafka topic platform-events.
- The Spark job continuously computes event counts and prints output to logs.
- Metrics are collected from exporters and available in Prometheus for Grafana dashboards.
- Alert rules are defined in prometheus/alert_rules.yml and routed by Alertmanager.
- On macOS (Docker Desktop), node-exporter uses `/:/host:ro` volume mount without `rslave` to avoid mount propagation errors.
- The Spark job sets `spark.jars.ivy=/tmp/.ivy2` to avoid Ivy cache write errors such as missing `/home/spark/.ivy2/...`.
