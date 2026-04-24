# Local Kubernetes Operations (Helm and Argo CD)

## Scope

This document is only for local Kubernetes operations on minikube.

## Helm Vs Argo CD Decision Guide

| If your primary goal is... | Prefer |
| --- | --- |
| Fast iteration on chart values and release debugging | Helm routine |
| Testing GitOps reconciliation behavior and ownership | Argo CD routine |
| Reproducing production-style app-of-apps flow | Argo CD routine |
| Isolating chart upgrade behavior without GitOps layer | Helm routine |

## Prerequisites

- `minikube`, `kubectl`, `helm`, `docker`
- Docker Desktop running
- Git remote configured for Argo CD bootstrap (`ARGOCD_REPO_URL`)

## Routine Quickstarts

### Helm Routine

```bash
make routine-up-helm
make routine-status-helm
```

### Argo CD Routine

```bash
make routine-up-argocd
make routine-status-argocd
```

### Stop Either Kubernetes Routine

```bash
make routine-down-helm
# or
make routine-down-argocd
```

## What Each Routine Does

### `routine-up-helm`

1. Starts minikube and sets context.
2. Builds local images in minikube Docker.
3. Ensures namespaces and `grafana-admin` secret.
4. Installs Helm releases for core, monitoring, Loki, and Alloy.
5. Applies observability manifests under `k8s/observability`.

### `routine-up-argocd`

1. Runs the same minikube/bootstrap preparation.
2. Installs Argo CD in `argocd` namespace.
3. Bootstraps app-of-apps and child apps from `argocd/`.

## Verification Commands

```bash
kubectl get pods -n data-platform
kubectl get pods -n monitoring
kubectl get pods -n argocd
helm list -A
kubectl get applications.argoproj.io -n argocd
```

## Port-Forward Quick Reference

| UI | Command | URL |
| --- | --- | --- |
| Argo CD | `kubectl -n argocd port-forward svc/argocd-server 18081:443` | <https://localhost:18081> |
| Grafana | `kubectl -n monitoring port-forward svc/kube-prometheus-stack-grafana 33000:80` | <http://localhost:33000> |
| Kafka UI | `kubectl -n data-platform port-forward svc/kafka-ui 18080:8080` | <http://localhost:18080> |
| MinIO Console | `kubectl -n data-platform port-forward svc/minio 19001:9001` | <http://localhost:19001> |

## Common Local UI Access

### Argo CD UI

```bash
kubectl -n argocd port-forward svc/argocd-server 18081:443
```

Open <https://localhost:18081>.

Get initial password:

```bash
make k8s-argocd-password
```

### Grafana UI

```bash
kubectl -n monitoring port-forward svc/kube-prometheus-stack-grafana 33000:80
```

Open <http://localhost:33000>.

### Kafka UI

```bash
kubectl -n data-platform port-forward svc/kafka-ui 18080:8080
```

Open <http://localhost:18080>.

## Troubleshooting

### Quick Failure Signature Map

| Symptom | Most likely cause | First action |
| --- | --- | --- |
| `Application` stays `OutOfSync/Syncing` | Stale operation state or drift not in Git | Re-sync and verify desired spec is committed |
| Argo install fails on CRDs | Annotation size/merge conflict on CRDs | Use server-side apply install flow |
| Grafana login loop/failure | Admin secret mismatch or stale browser cookie | Verify `grafana-admin`, retry with private window |
| Pods pending after bootstrap | Resource pressure in local profile | Check `kubectl describe pod` and minikube resources |

### Argo CD CRD Annotation Size Error

If install fails with CRD annotation length issues:

```bash
kubectl create namespace argocd --dry-run=client -o yaml | kubectl apply -f -
kubectl apply --server-side=true --force-conflicts -n argocd \
  -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
make k8s-argocd-bootstrap
```

### Helm And Argo CD Ownership Conflict

Do not have Helm and Argo CD manage the same resources simultaneously. Pick one owner per component set.

### Grafana Login/Availability Issues

Use runbook playbook: [docs/operations-runbook.md](docs/operations-runbook.md).

## Clean Re-Bootstrap Workflow

Use this when local state is too drifted to trust.

```bash
make routine-down-argocd
make routine-up-argocd
make routine-status-argocd
```

Then verify:

```bash
kubectl -n argocd get applications.argoproj.io
```

All core applications should converge to `Synced` and `Healthy`.
