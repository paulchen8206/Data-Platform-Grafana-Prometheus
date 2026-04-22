COMPOSE ?= docker compose
TOPIC ?= platform-events
CONSUMER_GROUP ?= platform-events-spark-job

.PHONY: help up build up-core up-monitoring down down-v restart ps \
	logs logs-producer logs-spark logs-promtail logs-prometheus logs-loki \
	verify-iceberg rules-prometheus rules-loki check-loki check-kafka-alert-series health \
	kafka-topics kafka-consumer-groups spark-rebuild reset-checkpoints

help:
	@echo "Data Platform operations"
	@echo ""
	@echo "Lifecycle"
	@echo "  make up                Build and start all services"
	@echo "  make build             Build all images"
	@echo "  make up-core           Start core data services"
	@echo "  make up-monitoring     Start monitoring services"
	@echo "  make down              Stop and remove containers"
	@echo "  make down-v            Stop and remove containers, volumes, and orphans"
	@echo "  make restart           Restart all services"
	@echo "  make ps                Show service status"
	@echo ""
	@echo "Logs"
	@echo "  make logs              Tail logs for all services"
	@echo "  make logs-producer     Tail producer logs"
	@echo "  make logs-spark        Tail spark-job logs"
	@echo "  make logs-promtail     Tail promtail logs"
	@echo "  make logs-prometheus   Tail prometheus logs"
	@echo "  make logs-loki         Tail loki logs"
	@echo ""
	@echo "Verification"
	@echo "  make health                    Run service/rules/loki/kafka health checks"
	@echo "  make verify-iceberg            Run Iceberg verification query"
	@echo "  make rules-prometheus          Show Prometheus alert rules"
	@echo "  make rules-loki                Show Loki ruler rules"
	@echo "  make check-loki                Check Loki labels and broad docker stream"
	@echo "  make check-kafka-alert-series  Check lag series for configured topic/group"
	@echo "  make kafka-topics              List Kafka topics"
	@echo "  make kafka-consumer-groups     List Kafka consumer groups"
	@echo ""
	@echo "Maintenance"
	@echo "  make spark-rebuild      Rebuild and restart spark-job"
	@echo "  make reset-checkpoints  Remove Spark checkpoints except .gitkeep"

up:
	$(COMPOSE) up -d --build

build:
	$(COMPOSE) build

up-core:
	$(COMPOSE) up -d zookeeper kafka kafka-init schema-registry minio minio-init spark-master spark-worker producer spark-job kafka-ui

up-monitoring:
	$(COMPOSE) up -d prometheus alertmanager grafana loki promtail kafka-exporter cadvisor node-exporter

down:
	$(COMPOSE) down

down-v:
	$(COMPOSE) down -v --remove-orphans

restart:
	$(COMPOSE) restart

ps:
	$(COMPOSE) ps

logs:
	$(COMPOSE) logs -f

logs-producer:
	$(COMPOSE) logs -f producer

logs-spark:
	$(COMPOSE) logs -f spark-job

logs-promtail:
	$(COMPOSE) logs -f promtail

logs-prometheus:
	$(COMPOSE) logs -f prometheus

logs-loki:
	$(COMPOSE) logs -f loki

verify-iceberg:
	$(COMPOSE) --profile verify run --rm iceberg-reader

rules-prometheus:
	curl -s http://localhost:9090/api/v1/rules

rules-loki:
	curl -s http://localhost:3100/loki/api/v1/rules

check-loki:
	@echo '=== Promtail Status ==='
	$(COMPOSE) ps promtail
	@echo '=== Loki service labels ==='
	curl -sG http://localhost:3100/loki/api/v1/label/service/values
	@echo
	@echo '=== Broad stream query {job="docker"} ==='
	curl -sG http://localhost:3100/loki/api/v1/query --data-urlencode 'query={job="docker"}' --data-urlencode 'limit=20'
	@echo

check-kafka-alert-series:
	@echo "=== Configured topic/group ==="
	@echo "TOPIC=$(TOPIC)"
	@echo "CONSUMER_GROUP=$(CONSUMER_GROUP)"
	@echo "=== Prometheus lag series for configured alert filter ==="
	curl -sG http://localhost:9090/api/v1/series --data-urlencode 'match[]=kafka_consumergroup_lag{topic="$(TOPIC)",consumergroup="$(CONSUMER_GROUP)"}'
	@echo

health:
	@echo "=== Service status ==="
	$(COMPOSE) ps
	@echo
	@echo "=== Prometheus Kafka alert rules ==="
	curl -s http://localhost:9090/api/v1/rules | grep -E 'KafkaTopicNoEvents2m|KafkaConsumerLagHigh|data-platform-alerts|platform-events|platform-events-spark-job' || true
	@echo
	@echo "=== Loki service labels ==="
	curl -sG http://localhost:3100/loki/api/v1/label/service/values
	@echo
	@echo "=== Kafka topics ==="
	$(COMPOSE) exec -T kafka kafka-topics --bootstrap-server kafka:9092 --list
	@echo
	@echo "=== Kafka consumer groups ==="
	$(COMPOSE) exec -T kafka kafka-consumer-groups --bootstrap-server kafka:9092 --list

kafka-topics:
	$(COMPOSE) exec -T kafka kafka-topics --bootstrap-server kafka:9092 --list

kafka-consumer-groups:
	$(COMPOSE) exec -T kafka kafka-consumer-groups --bootstrap-server kafka:9092 --list

spark-rebuild:
	$(COMPOSE) up -d --build spark-job

reset-checkpoints:
	find spark-job/checkpoints -mindepth 1 ! -name '.gitkeep' -exec rm -rf {} +
