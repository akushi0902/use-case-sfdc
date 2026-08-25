# Runbook: SLO-04 Stale Active-Job Checkpoint (PENDING)

**Alert:** `SfdcActiveJobCheckpointStale`  
**Severity:** critical  
**Owner:** sre-release-ops  
**Status:** PENDING — alert is disabled until Kafka checkpoint persistence is operational

## What this means (once active)

One or more actively-running release jobs have not emitted a checkpoint for more than 60 seconds. This breaches SLO-04. The job may be stalled, orphaned, or the checkpoint consumer may have fallen behind.

## Prerequisite

This alert and runbook become active when the Kafka checkpoint listener and durable job checkpoint persistence are implemented. Until then, use the `SfdcKafkaCheckpointConsumerLagHigh` alert and the `kafka-consumer-lag.md` runbook as a proxy signal.

## Triage steps (once active)

1. Check Kafka consumer lag on the `sfdc-job-checkpoints` topic. Growing lag → page Kafka SRE.
2. If lag is stable but checkpoints are stale, check Kubernetes worker pod status for the affected job. Use internal job references only — do not log org IDs or customer identifiers.
3. If no worker activity for > 5 minutes, treat the job as orphaned and initiate the checkpoint recovery procedure.
4. Check JVM heap pressure on the sfdc-integrator-service pod — high GC pause may delay checkpoint consumption.

## Safe incident practices

Reference only internal job identifiers (not customer-facing org IDs or account names) in incident tickets. Do not include raw Kafka message payloads or Salesforce payload fragments in ticket descriptions.
