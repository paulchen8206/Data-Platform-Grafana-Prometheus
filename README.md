# Data Platform Observability with Prometheus and Grafana

A reference project for operating a small event-driven data platform together with a complete observability stack.

## At A Glance

| Area | Stack |
| --- | --- |
| Data path | `source-ingestion` -> Kafka -> `spark-extraction` -> Iceberg on MinIO |
| Metrics | Prometheus + exporters + Alertmanager |
| Logs | Alloy -> Loki |
| Visualization | Grafana dashboards + Explore |
| Deployment routines | Docker, Kubernetes Helm, Kubernetes Argo CD |

## Choose A Routine

- Use Docker when you want the fastest local development feedback loop.
- Use Kubernetes Helm when you want direct release-level control.
- Use Kubernetes Argo CD when you want GitOps reconciliation and drift correction.

## Navigation

- Architecture and design model: [docs/architecture.md](docs/architecture.md)
- Local Kubernetes + Helm/Argo CD operations: [docs/local-kubernetes-argocd.md](docs/local-kubernetes-argocd.md)
- Production Kubernetes blueprint: [docs/production-kubernetes.md](docs/production-kubernetes.md)
- Day-2 runbook and incident playbooks: [docs/operations-runbook.md](docs/operations-runbook.md)

## Terminology Glossary

| Term | Meaning |
| --- | --- |
| Routine | A predefined operational path (`docker`, `helm`, or `argocd`) exposed through Make targets |
| Control plane | The system that applies and reconciles desired state (Docker Compose, Helm, or Argo CD) |
| Desired state | The target configuration represented in Git and manifests/values |
| Drift | Difference between desired state and live cluster state |
| Sync | Argo CD operation to reconcile live resources to desired state |
| Health | Runtime status of a workload or application after reconciliation |
| Self-heal | Automatic re-application of desired state when drift is detected |

## Command Formatting Conventions

- Use fenced `bash` blocks for runnable shell commands.
- Show commands without shell prompts (`$`) so they can be copied directly.
- Use one command per line unless a pipeline is required.
- Use inline code formatting only for short command references in prose.

## What This Repository Contains

- Data pipeline: `source-ingestion` -> Kafka -> `spark-extraction` -> Iceberg on MinIO.
- Metrics and alerting: Prometheus, Alertmanager, exporters.
- Logs and alerting: Alloy -> Loki (+ Loki rules).
- Visualization: Grafana dashboards and Explore.
- Deployment modes: Docker routine, Kubernetes Helm routine, Kubernetes Argo CD routine.

## Quick Start By Routine

### Docker Routine

```bash
make routine-up-docker
make routine-status-docker
```

Stop:

```bash
make routine-down-docker
```

### Kubernetes Routine (Helm)

```bash
make routine-up-helm
make routine-status-helm
```

Stop:

```bash
make routine-down-helm
```

### Kubernetes Routine (Argo CD)

```bash
make routine-up-argocd
make routine-status-argocd
```

Stop:

```bash
make routine-down-argocd
```

### Compatibility Wrappers

If you prefer the old interface:

```bash
make routine-up ROUTINE=docker
make routine-status ROUTINE=helm
make routine-down ROUTINE=argocd
```

## Primary Local Endpoints

### Docker Routine Endpoints

- Grafana: <http://localhost:3000>
- Prometheus: <http://localhost:9090>
- Alertmanager: <http://localhost:9093>
- Loki: <http://localhost:3100>
- Kafka UI: <http://localhost:8085>
- Spark Master UI: <http://localhost:8080>
- MinIO Console: <http://localhost:9001>

### Kubernetes Routine Endpoints

Use `kubectl port-forward` from the appropriate namespace. Operational examples are in docs.

## Quick Operational Checks

```bash
make health
make rules-prometheus
make rules-loki
```

For Kubernetes routines:

```bash
kubectl get pods -n data-platform
kubectl get pods -n monitoring
kubectl get pods -n argocd
```

## Documentation Map

- Architecture and design model: [docs/architecture.md](docs/architecture.md)
- Local Kubernetes + Helm/Argo CD operations: [docs/local-kubernetes-argocd.md](docs/local-kubernetes-argocd.md)
- Production Kubernetes blueprint: [docs/production-kubernetes.md](docs/production-kubernetes.md)
- Day-2 runbook and incident playbooks: [docs/operations-runbook.md](docs/operations-runbook.md)

## Repository Layout

- `docker-compose.yml`: local service topology.
- `Makefile`: routines and lifecycle commands.
- `k8s/`: Helm values and local deployment assets.
- `argocd/`: Argo CD app definitions.
- `observability/`: ServiceMonitors, rules, dashboard manifests.
- `prometheus/`, `loki/`, `grafana/`, `alloy/`: observability configs.

## Notes

- For local reproducibility, Grafana provisioning and SQLite repair logic are codified in `k8s/values-kube-prometheus-stack-prod.yaml`.
- Avoid mixed ownership of the same Kubernetes resources between Helm and Argo CD.
- For incident response and recovery workflows, start from `docs/operations-runbook.md`.
