# Architecture

## How To Read This Document

- Sections 1-3 explain design intent and observability component roles.
- Sections 4-6 describe telemetry/data flow and SRE signal mapping.
- Sections 7-10 describe ownership, GitOps behavior, and runtime topology.
- Section 11 links to canonical external references.

## 1. Design Idea

### Observability Vs Monitoring

**Monitoring** answers known questions: "Is this service up? Is disk below 90%?" It detects known unknowns.

**Observability** goes further: given only the data a system produces, can you understand its internal state and debug problems you didn't anticipate — the unknown unknowns? It doesn't just tell you a pipeline is slow; it helps you trace which Spark stage, which Kafka partition, or which schema mismatch started the degradation and when.

This project is structured around observability, not just monitoring:

- Run a realistic data pipeline and observability stack using repeatable routines.
- Keep deployment ownership explicit so routine execution is deterministic.
- Keep observability declarative so dashboards, datasources, and alerts survive restarts and resets.

### Three Pillars

Observability is built on three signal types. This project covers two fully and leaves the third as a future extension:

| Pillar | Tooling | Status |
| --- | --- | --- |
| Metrics | Prometheus + exporters | Implemented |
| Logs | Alloy → Loki | Implemented |
| Traces | OpenTelemetry → Tempo | Future extension |

### Design Principles

1. **Telemetry-first operations**: metrics, logs, and alerts are first-class deliverables, not afterthoughts.
2. **Pull model for metrics**: Prometheus scrapes targets on a schedule; services expose `/metrics`. This makes liveness detection implicit — a failed scrape is itself a signal.
3. **Deterministic routines**: Docker, Helm, and Argo CD routines are explicit and testable.
4. **Clear ownership boundaries**: each deployment path has a clearly defined owner; no resource is managed by two control planes simultaneously.
5. **Alert on symptoms, not causes**: alert when users are impacted (error rates, latency SLOs), not when a CPU hits 80%. Pager fatigue undermines operations.
6. **Docs-as-operations**: architecture, routines, and runbook stay synchronized.

### Non-Goals

- This project does not currently implement full distributed tracing in the runtime stack.
- This project does not prescribe one mandatory production deployment topology.
- This document does not replace the incident runbook for operational execution.

## 2. Prometheus Ecosystem Roles

Prometheus is not a single application; it is an ecosystem of four cooperating components:

```mermaid
flowchart LR
  subgraph Targets[Monitored targets]
    App[source-ingestion :9404]
    KEx[kafka-exporter :9308]
    NEx[node-exporter :9100]
    cAdv[cadvisor :8080]
    SpEx[spark-extraction :4040]
  end

  subgraph PG[Pushgateway]
    PGW[pushgateway :9091\nbatch job drop-box]
  end

  subgraph PS[Prometheus Server — The Brain]
    Scrape[Scrape engine\npull /metrics every 15s]
    TSDB[TSDB\ntime-series storage]
    PromQL[PromQL API\nquery & rule evaluation]
    Rules[Alerting rules\nrecording rules]
    Scrape --> TSDB
    TSDB --> PromQL
    TSDB --> Rules
  end

  subgraph AM[Alertmanager — The Dispatcher]
    Dedup[Deduplication]
    Group[Grouping]
    Route[Routing → Slack / PagerDuty]
    Dedup --> Group --> Route
  end

  subgraph GF[Grafana — The Face]
    DS[Data Sources]
    Dash[Dashboards]
    Panel[Panels + PromQL queries]
    Var[Variables / templating]
    DS --> Dash --> Panel --> Var
  end

  Targets -- pull scrape --> Scrape
  PGW -- pull scrape --> Scrape
  Rules -- fire alert --> AM
  PromQL -- query --> GF
```

| Component | Nickname | Responsibility |
| --- | --- | --- |
| Prometheus Server | The Brain | Pull-scrapes targets, stores TSDB, evaluates PromQL rules |
| Exporters | The Translators | Convert non-native metrics (Kafka, JVM, OS) into `/metrics` |
| Alertmanager | The Dispatcher | Deduplicates, groups, and routes alert notifications |
| Pushgateway | The Drop Box | Holds metrics from short-lived batch jobs until Prometheus scrapes |

> **Pushgateway note**: use it only for service-level batch jobs (e.g., a nightly Iceberg compaction run). Do not use it for general application monitoring.

## 3. Grafana Building Blocks

Grafana does not store data. It connects to data sources and renders results. Understanding the hierarchy prevents confusion:

```mermaid
flowchart TB
  DS[Data Source\nPrometheus URL + auth] --> Dash
  subgraph Dash[Dashboard — the screen]
    Row[Rows / sections]
    subgraph Panel[Panel — atomic visualization]
      Q[Query — PromQL / LogQL expression]
      Viz[Visualization type\nTime Series · Stat · Gauge · Table · Heatmap]
      Q --> Viz
    end
    Row --> Panel
  end
  Var[Variables / Templating\n$namespace · $job · $instance] -.-> Panel
```

| Building block | Role |
| --- | --- |
| Data Source | Configured connection (URL, credentials) to Prometheus or Loki |
| Dashboard | Top-level container; exportable as JSON, importable across environments |
| Panel | Single chart or table inside a dashboard |
| Query | PromQL or LogQL expression sent to the data source |
| Variable | Dropdown placeholder that makes one dashboard work across many targets |

## 4. System Context

```mermaid
flowchart LR
  subgraph DataPlane[Data Platform]
    P[source-ingestion]
    K[Kafka]
    S[spark-extraction]
    M[MinIO Iceberg warehouse]
  end

  subgraph ObsPlane[Observability Platform]
    E[Exporters + Alloy]
    PGW[Pushgateway]
    PR[Prometheus]
    L[Loki]
    G[Grafana]
    A[Alertmanager]
  end

  P --> K --> S --> M
  K --> E
  S --> E
  P -. batch metrics .-> PGW
  S -. batch metrics .-> PGW
  E -- /metrics --> PR
  PGW -- /metrics --> PR
  E -- log push --> L
  PR --> G
  L --> G
  PR --> A
  L --> A
```

## 5. Runtime Telemetry Flow (Pull Model)

```mermaid
sequenceDiagram
  participant Producer as source-ingestion
  participant Kafka as Kafka
  participant Spark as spark-extraction
  participant MinIO as MinIO/Iceberg
  participant Exporters as Exporters+Alloy
  participant Prom as Prometheus
  participant Loki as Loki
  participant Grafana as Grafana
  participant AM as Alertmanager

  Producer->>Kafka: publish platform-events
  Spark->>Kafka: consume platform-events
  Spark->>MinIO: write Iceberg data files/metadata

  note over Exporters,Prom: Pull model — Prometheus scrapes every 15 s
  Prom->>Exporters: GET /metrics (kafka-exporter, node-exporter, cadvisor)
  Exporters-->>Prom: metric families (counter, gauge, histogram)

  Exporters->>Loki: push log streams (Alloy)

  Prom->>Grafana: PromQL query response
  Loki->>Grafana: LogQL query response
  Prom->>AM: fire alert (rule threshold breached)
  Loki->>AM: fire alert (log rule matched)
```

## 6. Golden Signals Applied To This Platform

Google SRE defines four golden signals that should be the starting point for any observability setup. This project maps them as follows:

| Golden Signal | Definition | Platform metric examples |
| --- | --- | --- |
| **Latency** | Time to serve a request | Kafka producer latency, Spark stage duration, schema-registry request duration |
| **Traffic** | Demand on the system | `kafka_topic_partition_current_offset` rate, Spark records-per-second |
| **Errors** | Rate of failed requests | Kafka consumer lag spikes, Spark task failure rate, schema-registry error responses |
| **Saturation** | How full the system is | JVM heap usage, Kafka broker disk, MinIO storage utilization, CPU/memory from node-exporter |

> **Cardinality warning**: do not add high-variance labels (user IDs, full URLs, session tokens) to Prometheus metrics. Each unique label combination creates a separate time series; cardinality explosion will exhaust Prometheus memory.

## 7. Deployment Ownership Model

```mermaid
flowchart TB
  subgraph DockerRoutine[Docker routine — owner: docker compose]
    DC[docker-compose services]
  end

  subgraph HelmRoutine[Kubernetes Helm routine — owner: Helm]
    HCore[data-platform-core release]
    HMon[kube-prometheus-stack release]
    HLoki[loki release]
    HAlloy[alloy release]
  end

  subgraph ArgoRoutine[Kubernetes Argo CD routine — owner: Argo CD]
    AApp[app-of-apps]
    ACore[core app]
    AMon[monitoring app]
    ALoki[loki app]
    AAlloy[alloy app]
  end

  note1[Rule: no resource is dual-owned between Helm and Argo CD]

  DockerRoutine --- note1
  HelmRoutine --- note1
  ArgoRoutine --- note1
```

## 8. Argo CD GitOps Routine Flow

```mermaid
flowchart LR
  Dev[Developer]
  Git[GitHub repository]

  subgraph ArgoCD[argocd namespace]
    Root[data-platform-root\napp-of-apps]
    Core[data-platform-core]
    Mon[data-platform-monitoring]
    Loki[data-platform-loki]
    Alloy[data-platform-alloy]
    RS[argocd-repo-server]
    AC[argocd-application-controller]
  end

  subgraph Cluster[Kubernetes cluster]
    DP[data-platform namespace resources]
    MON[monitoring namespace resources]
  end

  Dev -->|git push| Git
  Git -->|desired manifests| RS
  RS --> Root
  Root --> Core
  Root --> Mon
  Root --> Loki
  Root --> Alloy

  AC -->|sync/apply| Core
  AC -->|sync/apply| Mon
  AC -->|sync/apply| Loki
  AC -->|sync/apply| Alloy

  Core --> DP
  Mon --> MON
  Loki --> MON
  Alloy --> MON

  DP -. drift detection + self-heal .-> AC
  MON -. drift detection + self-heal .-> AC
```

## 9. Routine-To-Deployment Mapping

| Routine target | Control plane | Expected deployment behavior |
| --- | --- | --- |
| `routine-up-docker` | Docker Compose | Starts local containers from `docker-compose.yml` |
| `routine-up-helm` | Minikube + Helm | Builds local images and installs Helm releases |
| `routine-up-argocd` | Minikube + Argo CD | Installs Argo CD and syncs app-of-apps |
| `routine-status-*` | Routine-specific | Reports health for the active routine |
| `routine-down-*` | Routine-specific | Stops local stack or minikube profile |

## 10. Kubernetes Deployments By Namespace

```mermaid
flowchart LR
  subgraph DP[data-platform namespace]
    zk[zookeeper]
    kafka[kafka]
    sr[schema-registry]
    producer[source-ingestion]
    spark[spark-extraction + spark master/worker]
    minio[minio]
    kui[kafka-ui]
  end

  subgraph MON[monitoring namespace]
    prom[prometheus]
    graf[grafana]
    am[alertmanager]
    loki[loki]
    alloy[alloy]
    exp[kafka-exporter / node-exporter / cadvisor]
  end

  subgraph ARGO[argocd namespace]
    ac[argocd-application-controller]
    as[argocd-server]
    ar[argocd-repo-server]
    ad[argocd-dex]
  end
```

## 11. Reference URLs

- Prometheus docs: <https://prometheus.io/docs/introduction/overview/>
- Grafana docs: <https://grafana.com/docs/grafana/latest/>
- Loki docs: <https://grafana.com/docs/loki/latest/>
- Alloy docs: <https://grafana.com/docs/alloy/latest/>
- Argo CD docs: <https://argo-cd.readthedocs.io/en/stable/>
- kube-prometheus-stack chart: <https://github.com/prometheus-community/helm-charts/tree/main/charts/kube-prometheus-stack>
