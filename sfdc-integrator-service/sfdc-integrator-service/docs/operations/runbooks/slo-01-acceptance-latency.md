# Runbook: SLO-01 Release API Acceptance Latency P95 Breach

**Alert:** `SfcdReleaseAcceptanceLatencyP95Breach`  
**Severity:** critical  
**Owner:** sre-release-ops  
**SLO:** p95 acceptance latency < 2000 ms (ops/slo/release-workflow-guardrails.yaml#slo-01)

## What this means

The p95 acceptance latency for release workflow routes (`/quickdeploy`, `/deploy`, `/validate`, `/api/v2/sfdc/release-jobs`) has exceeded 2000 ms for more than 5 minutes. Release commands are still being accepted but operators and users will experience degraded response times.

## Triage steps

1. **Check dependency health first** — GET `/actuator/health`. If `kafkaConnectivity` or `db` is DOWN, follow the corresponding dependency runbook. Latency breaches frequently cascade from dependency degradation.

2. **Check JVM heap usage** — If heap is above 85%, GC pauses may be causing latency spikes. Check `jvm_memory_used_bytes / jvm_memory_max_bytes` on the Dependency Health dashboard section.

3. **Check for recent deployments** — Correlate alert onset with deployment timestamps. A latency regression that begins within minutes of a deployment is strong evidence that a rollback is needed.

4. **Check Spring MVC thread pool** — If `executor_pool_size` is at maximum and `executor_queue_size` is growing, the thread pool is saturated. Investigate which request handler is holding threads.

5. **Check Kafka producer latency** — If release commands synchronously publish to Kafka before returning 202, a slow broker will inflate acceptance latency. Check Kafka broker health and `sfdc-job-checkpoints` topic partition leaders.

## Resolution actions

- **Dependency down:** Follow the dependency runbook (kafka-connectivity-down.md, hazelcast-cluster-down.md).
- **Deployment regression:** Roll back the most recent deployment; confirm latency recovers before re-deploying.
- **Thread pool saturation:** Increase `server.tomcat.threads.max` (or executor pool size) via ConfigMap if load has genuinely increased. File a ticket to evaluate horizontal scaling if saturation persists.
- **JVM heap pressure:** Trigger a heap dump for offline analysis. Restart the pod as a temporary measure after capturing diagnostics.

## Safe incident practices

Do NOT paste request payloads, Salesforce credentials, customer org identifiers, or full stack traces into incident tickets. Attach only: alert name, environment, onset time, duration, affected URI label values, and p95 metric value.
