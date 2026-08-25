# Runbook: SLO-07 Sensitive Data Log Pattern Detected

**Alert:** `SfdcSensitiveDataLogPatternDetected`  
**Severity:** critical  
**Owner:** security-sre  
**SLO:** Triage within 30 minutes of alert firing (ops/slo/release-workflow-guardrails.yaml#slo-07)

## What this means

A log scanning rule matched a pattern associated with sensitive data in sfdc-integrator-service logs. This may indicate raw request payloads, Salesforce credential fragments, customer identifiers, authorization header values, or bearer tokens appearing in log output.

**This is a P0 security incident. Triage must begin within 30 minutes of the alert firing time.**

## Triage steps

1. **Assess log sink exposure** — Determine which downstream log sinks (SIEM, log archive, third-party observability) received the event. If the log is accessible in a sink that is visible beyond the service team, initiate the log scrub procedure immediately.

2. **Identify the log emitter without accessing the sensitive content** — Use structured log fields (operation, controller class name, log level) to identify the source. Do not open or copy the raw log line containing potentially sensitive content.

3. **Classify the pattern match:**
   - Known-safe false positive (e.g. log scanner matching "token" in a metric name) → document reasoning, update scanner exclusion, close incident.
   - Confirmed credential, bearer token, or Salesforce payload → initiate P0 incident.
   - Customer org identifier or account name → initiate P1 incident.

4. **If confirmed sensitive data exposure:**
   - Open P0 incident and notify the security team immediately.
   - Do NOT paste the log content into the incident ticket, Slack, or email.
   - Reference only: service name, pattern identifier, timestamp, and environment.
   - Security team will determine whether the credential must be rotated.

## Resolution actions

- **Code fix:** Identify the log call site. The `SafeStructuredLogger` must not receive raw request DTOs, authorization headers, Salesforce credentials, or payload fragments. Fix the log call to use only safe fields (operation, stepId, correlationId, outcome).
- **Credential rotation:** If a live credential appeared in logs, treat it as compromised and rotate before restoring service.
- **Post-mortem:** Required within 3 business days of confirmed sensitive data exposure. Document: root cause, log sink exposure scope, remediation, and prevention controls added.

## Safe incident practices

Do NOT include log lines containing sensitive data in: incident tickets, post-mortem documents, Slack messages, or code comments. Reference only timestamp, service name, pattern identifier, and alert firing time. Sensitive log content is handled exclusively through the security team's evidence-handling process.
