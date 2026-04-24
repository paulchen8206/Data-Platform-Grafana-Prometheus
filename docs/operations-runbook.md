# Operations Runbook

## Scope

Day-2 operations, verification, and incident playbooks for all three routines.

## Incident Triage Model

Use this sequence before deep troubleshooting:

1. Confirm active routine (`docker`, `helm`, or `argocd`).
2. Confirm control plane health (Docker daemon or Kubernetes API).
3. Confirm workload health (pods/services/applications).
4. Confirm telemetry path (Prometheus/Loki reachable).
5. Apply targeted playbook and re-verify.

## First 10-Minute Checks

```bash
make routine-status-docker || true
make routine-status-helm || true
make routine-status-argocd || true
kubectl get pods -n data-platform
kubectl get pods -n monitoring
kubectl get pods -n argocd
```

## Routine Operations

### Docker Routine

Start and verify:

```bash
make routine-up-docker
make routine-status-docker
```

Stop:

```bash
make routine-down-docker
```

### Kubernetes Helm Routine

Start and verify:

```bash
make routine-up-helm
make routine-status-helm
```

Stop:

```bash
make routine-down-helm
```

### Kubernetes Argo CD Routine

Start and verify:

```bash
make routine-up-argocd
make routine-status-argocd
```

Stop:

```bash
make routine-down-argocd
```

## Daily Health Checks

```bash
make health
make rules-prometheus
make rules-loki
make check-loki
make check-kafka-alert-series
```

For Kubernetes:

```bash
kubectl get pods -n data-platform
kubectl get pods -n monitoring
kubectl get pods -n argocd
```

## Data Validation Checks

```bash
make verify-iceberg
make verify-warehouse-k8s
```

## Incident Playbooks

### 1) Grafana Login Fails

1. Confirm Grafana pod is running:

```bash
kubectl -n monitoring get pods -l app.kubernetes.io/name=grafana
```

2. Verify admin secret values:

```bash
kubectl -n monitoring get secret grafana-admin -o jsonpath='{.data.admin-user}' | base64 -d; echo
kubectl -n monitoring get secret grafana-admin -o jsonpath='{.data.admin-password}' | base64 -d; echo
```

3. Recreate local access tunnel:

```bash
kubectl -n monitoring port-forward svc/kube-prometheus-stack-grafana 33000:80
```

4. Retry in a private browser window to avoid stale cookie/session issues.

### 2) Kafka UI Shows No Topic/Schema

1. Verify topic exists:

```bash
kubectl -n data-platform exec deploy/kafka -- \
  kafka-topics --bootstrap-server kafka:9092 --list
```

2. Create missing topic if needed:

```bash
kubectl -n data-platform exec deploy/kafka -- \
  kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists \
  --topic platform-events --partitions 3 --replication-factor 1
```

3. Verify schema subject:

```bash
kubectl -n data-platform exec deploy/schema-registry -- \
  curl -s http://localhost:8081/subjects
```

4. Reconnect Kafka UI tunnel:

```bash
kubectl -n data-platform port-forward svc/kafka-ui 18080:8080
```

### 3) Argo CD UI Not Reachable

1. Confirm pods:

```bash
kubectl -n argocd get pods
```

2. Start tunnel:

```bash
kubectl -n argocd port-forward svc/argocd-server 18081:443
```

3. Get initial password:

```bash
make k8s-argocd-password
```

### 4) data-platform-core Is Progressing (spark-extraction Crash Loop)

Typical symptom: `data-platform-core` is `Synced` but stays `Progressing`, and `spark-extraction` keeps restarting.

1. Confirm the failing workload:

```bash
kubectl -n data-platform get deploy spark-extraction
kubectl -n data-platform get pods -l app=spark-extraction
```

2. Confirm offset/checkpoint data-loss error in logs:

```bash
kubectl -n data-platform logs deploy/spark-extraction --tail=120
```

Look for `OffsetOutOfRangeException` / `Cannot fetch offset` messages.

3. Clear stale Spark checkpoint state from `spark-checkpoints` PVC:

```bash
kubectl -n data-platform run ckpt-cleaner --image=busybox:1.36 --restart=Never \
  --overrides='{"spec":{"containers":[{"name":"ckpt-cleaner","image":"busybox:1.36","command":["sh","-c","rm -rf /mnt/* && echo cleaned && sleep 2"],"volumeMounts":[{"name":"ckpt","mountPath":"/mnt"}]}],"volumes":[{"name":"ckpt","persistentVolumeClaim":{"claimName":"spark-checkpoints"}}]}}'
kubectl -n data-platform wait --for=condition=Ready pod/ckpt-cleaner --timeout=60s || true
kubectl -n data-platform logs ckpt-cleaner
kubectl -n data-platform delete pod ckpt-cleaner --ignore-not-found
```

4. Restart and verify `spark-extraction`:

```bash
kubectl -n data-platform rollout restart deploy/spark-extraction
kubectl -n data-platform rollout status deploy/spark-extraction --timeout=180s
```

5. Verify Argo CD app health recovers:

```bash
kubectl -n argocd get application data-platform-core \
  -o jsonpath='Sync:{.status.sync.status}{"\n"}Health:{.status.health.status}{"\n"}Phase:{.status.operationState.phase}{"\n"}'
```

### 5) Argo CD App Stuck In OutOfSync/Syncing

Typical symptom: app remains non-converged even after repeated sync attempts.

1. Inspect current app status and operation state:

```bash
kubectl -n argocd get application data-platform-monitoring -o yaml
```

2. Ensure desired state is committed in Git and pushed.

3. Refresh/sync parent app (`data-platform-root`) and child app from Argo CD UI or CLI.

4. If stale operation state persists, recreate only the affected child `Application` object from Git manifests.

5. Re-verify both parent and child status are `Synced` + `Healthy`.

### 6) Grafana Pod Healthy But Login Still Fails

1. Confirm `grafana-admin` values are expected.
2. Confirm no stale browser session (private window).
3. Restart Grafana workload and re-check logs:

```bash
kubectl -n monitoring rollout restart deploy/kube-prometheus-stack-grafana
kubectl -n monitoring rollout status deploy/kube-prometheus-stack-grafana --timeout=180s
kubectl -n monitoring logs deploy/kube-prometheus-stack-grafana --tail=120
```

## Recovery Utilities

```bash
make spark-rebuild
make reset-checkpoints
make monitoring-recreate
```

## Closure Checklist

After resolving an incident:

1. Verify routine status command is green.
2. Verify core user paths (Grafana, Kafka UI, data flow) are restored.
3. Verify Argo CD applications are converged where applicable.
4. Commit any configuration fix to Git (avoid live-only drift).
5. Update this runbook when a new pattern is discovered.

## Compatibility Commands (Legacy)

```bash
make routine-up ROUTINE=docker
make routine-status ROUTINE=helm
make routine-down ROUTINE=argocd
```
