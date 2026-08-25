# Runbook: Kafka Checkpoint Consumer Lag High

**Alert:** `SfdcKafkaCheckpointConsumerLagHigh`  
**Severity:** warning  
**Owner:** sre-release-ops

## What this means

The Kafka consumer group consuming from the `sfdc-job-checkpoints` topic has accumulated more than 1000 unprocessed messages for more than 5 minutes. This is a proxy signal for checkpoint staleness until the SLO-04 metric is operational.

## Triage steps

1. Check Kafka broker health and the `sfdc-job-checkpoints` topic partition health.
2. Check sfdc-integrator-service logs for `KafkaCheckpointListener` deserialization or persistence errors. Review safe structured fields only (topic, partition, offset) — do not log message payload content.
3. If the consumer is paused or restarting repeatedly, check JVM heap pressure — a GC storm can cause consumer group rebalancing.
4. Check if the sfdc-integrator-service pod was recently restarted — a restart causes temporary lag growth as the consumer catches up.

## Resolution actions

- **Temporary catch-up lag:** Monitor for 10 minutes; if lag decreases steadily, the consumer is healthy and catching up. No action needed.
- **Persistent growing lag + broker healthy:** Investigate consumer group thread allocation and consider increasing Kafka consumer concurrency.
- **Broker unhealthy:** Engage Kafka SRE / infrastructure team.
- **Deserialization errors:** A schema mismatch between producer and consumer. Escalate to release engineering.

## Safe incident practices

Do not log Kafka message payloads. Reference only: topic name, partition number, consumer group ID (not customer identifiers), and offset values.
