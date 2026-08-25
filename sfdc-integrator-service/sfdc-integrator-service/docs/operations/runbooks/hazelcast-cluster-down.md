# Runbook: Hazelcast Cluster Down

**Alert:** `SfdcHazelcastClusterDown`  
**Severity:** critical  
**Owner:** sre-release-ops

## What this means

The `hazelcastCluster` health indicator reports DOWN. Status lookups will fall through to PostgreSQL, potentially increasing SLO-02 latency. New release acceptance is unaffected; in-progress status queries may degrade.

## Triage steps

1. Check Hazelcast cluster member count via the Hazelcast management console or metrics.
2. Check JVM heap usage — Hazelcast eviction and cluster exit can be triggered by memory pressure. If heap is above 90%, a GC-induced eviction may have partitioned the cluster.
3. Check for network partitions between sfdc-integrator-service pods.
4. Review recent ConfigMap or Secret changes to the Hazelcast configuration.

## Resolution actions

- **Memory pressure:** Increase JVM heap limits in the Kubernetes Deployment spec.
- **Network partition:** Restore network connectivity between pods; Hazelcast will re-merge the cluster automatically.
- **Configuration change:** Roll back the configuration change.

## Safe incident practices

Hazelcast cache entries for release jobs must not contain raw Salesforce credentials, bearer tokens, or full request payloads. If cache content investigation is needed, reference only internal job identifiers (not customer-visible org IDs or account names).
