# Runbook: Kubernetes Readiness Probe Down

**Alert:** `SfdcKubernetesReadinessProbeDown`  
**Severity:** critical  
**Owner:** sre-release-ops

## What this means

The `/actuator/health/readiness` probe is reporting NOT READY. Kubernetes has stopped routing new requests to this pod. At least one readiness-group health indicator (`db`, `kafkaConnectivity`, `hazelcastCluster`, or `jobDispatchReadiness`) is DOWN or UNKNOWN.

## Triage steps

1. GET `/actuator/health/readiness` on the affected pod. This returns the component-level breakdown.
2. Identify the first DOWN or UNKNOWN component.
3. Follow the corresponding runbook:
   - `kafkaConnectivity` DOWN → `kafka-connectivity-down.md`
   - `hazelcastCluster` DOWN → `hazelcast-cluster-down.md`
   - `jobDispatchReadiness` UNKNOWN → `dispatch-readiness-unknown.md`
   - `db` DOWN → engage the database operations team

## Resolution

Once the failing dependency recovers, the Spring Boot readiness group will automatically re-evaluate and return READY. No pod restart is needed in most cases.

## Safe incident practices

Do not log pod IP addresses, node names, or namespace identifiers associated with customer workloads in incident tickets. Reference only the alert name, environment, affected health component, and onset time.
