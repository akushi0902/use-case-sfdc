# Runbook: Kafka Connectivity Down

**Alert:** `SfdcKafkaConnectivityDown`  
**Severity:** critical  
**Owner:** sre-release-ops

## What this means

The `kafkaConnectivity` Spring Boot health indicator reports DOWN. Release jobs accepted while Kafka is unavailable will fail to dispatch on the async path; checkpoint events will not be published.

## Triage steps

1. **Verify the health indicator** — GET `/actuator/health/readiness` on the sfdc-integrator-service instance. If `kafkaConnectivity` is DOWN, the readiness probe will also be unhealthy.

2. **Check Kafka broker health** — Engage the platform/infrastructure team to verify Kafka cluster health (broker availability, partition leadership, replication).

3. **Check bootstrap-servers configuration** — Verify `spring.kafka.bootstrap-servers` in the sfdc-integrator-service ConfigMap points to the correct broker addresses for this environment. A misconfiguration after a cluster migration is a common cause.

4. **Check for network policy changes** — If a recent network policy or firewall rule change is suspected, escalate to the platform team.

## Resolution actions

- **Kafka cluster down:** Engage Kafka SRE / infrastructure team. The sfdc-integrator-service will reconnect automatically when the broker recovers.
- **Configuration mismatch:** Update `spring.kafka.bootstrap-servers` in the ConfigMap and restart the service.
- **Network policy:** Revert or correct the network policy change.

## Safe incident practices

Log entries referencing Kafka should include only: topic name, partition number, error type. Do not log Kafka message payloads, offset values tied to specific customer jobs, or broker authentication credentials.
