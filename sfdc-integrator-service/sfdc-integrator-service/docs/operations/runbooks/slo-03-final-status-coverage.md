# Runbook: SLO-03 Job Final-Status Coverage Below 99% (PENDING)

**Alert:** `SfdcJobFinalStatusCoverageBelow99Percent`  
**Severity:** critical  
**Owner:** sre-release-ops  
**Status:** PENDING — alert is disabled until durable lifecycle persistence (PostgreSQL) is operational

## What this means (once active)

Fewer than 99% of accepted release jobs within the 1-hour cohort window have reached a terminal state (`SUCCEEDED`, `FAILED`, `CANCELLED`). Uncovered jobs may be orphaned, stalled in an intermediate state, or pending a checkpoint that never arrived.

## Prerequisite

This alert becomes active when PostgreSQL job lifecycle persistence is implemented and the `sfdc_job_terminal_count` and `sfdc_job_accepted_count` metrics are emitted. Until then, monitor active workflow counts via panel p-006 on the release operations dashboard as a manual proxy.

## Triage steps (once active)

1. Query the PostgreSQL lifecycle table for jobs accepted in the last 1-hour cohort with no terminal record. Use internal job references only — do not expose customer org IDs or account names in queries or results.
2. Check if uncovered jobs share a common operation type (`QUICK_DEPLOY`, `DEPLOY`, `VALIDATE`) or dispatch path (legacy vs. worker).
3. Check Kafka consumer lag — jobs accepted during a Kafka outage may need checkpoint recovery.
4. If uncovered rate exceeds 2%, escalate to release engineering.

## Safe incident practices

Use internal job reference IDs only in queries and incident descriptions. Do not expose Salesforce org identifiers, customer account names, or deploy request payload contents in incident documentation.
