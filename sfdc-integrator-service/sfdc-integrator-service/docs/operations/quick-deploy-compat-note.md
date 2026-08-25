# Quick Deploy Legacy Compatibility Note

**Status:** Characterized — contract locked  
**Routes:** `POST /quickdeploy`, `POST /quickdeploy/stop`  
**Controller:** `JobExecutionController`  
**Test class:** `JobExecutionControllerCompatibilityTest`  
**Machine-readable contract:** `fixtures/contracts/legacy-release/legacy-contract-inventory.json` (LEGACY-001, LEGACY-002)

---

## What behavior is preserved

This compatibility suite locks the following observable behaviors of the legacy quick deploy boundary. These behaviors must not change without a deliberate, reviewed migration step and updated consumer guidance.

### POST /quickdeploy — start

| Behavior | Preserved value |
|---|---|
| HTTP success status | `200 OK` |
| HTTP success body | Plain string `SUCCESS` (no JSON envelope) |
| HTTP error status for missing/blank `deploymentRequestId` | `400 Bad Request` |
| HTTP error body for missing/blank `deploymentRequestId` | Plain string `deploymentRequestId is required` |
| HTTP error body for malformed JSON | Plain string `Malformed request body` (from `SfdcExceptionHandler`) |
| Service delegation when valid | `QuickDeployService.start()` called exactly once |
| Service delegation when invalid `deploymentRequestId` | `QuickDeployService.start()` **not** called |
| `fallbackTaskId` normalization | `null` → `""` before `start()` is called |
| `fallbackTaskId` when present | Value is passed through unchanged |

### POST /quickdeploy/stop — cancellation

| Behavior | Preserved value |
|---|---|
| HTTP success status | `200 OK` |
| HTTP success body | Plain string `SUCCESS` (no JSON envelope) |
| HTTP error body for malformed JSON | Plain string `Malformed request body` |
| Service delegation | `QuickDeployService.stop()` called exactly once |

---

## Why these tests exist

Internal pipeline consumers call `/quickdeploy` and `/quickdeploy/stop` with the current request shape and depend on:
- The plain `"SUCCESS"` acknowledgement string (not a JSON body).
- The exact `400` rejection message to detect invalid submission without parsing a structured error envelope.
- The fact that acknowledgement is synchronous — background Salesforce execution proceeds after the response is returned.

Changes to shared validation paths, logging helpers, async dispatch abstractions, or exception handler chain can silently alter these behaviors if not guarded. This suite makes such drift visible in CI.

---

## How to change a preserved behavior

1. Identify which downstream consumers depend on the specific behavior being changed.
2. Coordinate with those consumers to migrate to the new contract before or alongside the change.
3. Update `fixtures/contracts/legacy-release/legacy-contract-inventory.json` to reflect the new characterization.
4. Update the corresponding test assertions in `JobExecutionControllerCompatibilityTest` and `JobExecutionControllerTest`.
5. Include migration notes in the PR description and, where a v2 endpoint is being introduced, in the v2 API changelog.

**Do not remove or weaken compatibility tests without completing all of the above steps.**

---

## Runtime requirements

The compatibility tests require no external services. They run entirely within the Spring MVC test slice (`@WebMvcTest`) with mocked service dependencies. They are safe for local development and CI environments without Salesforce credentials, Kafka brokers, Hazelcast, Kubernetes access, or package manager secrets.

---

## Related documents

- `docs/operations/release-workflow-slos.md` — SLO policy including legacy compatibility guardrail (SLO-06)
- `ops/slo/release-workflow-guardrails.yaml` — machine-readable threshold for legacy compatibility (slo-06-legacy-compatibility)
- `fixtures/contracts/legacy-release/legacy-contract-inventory.json` — machine-readable contract record (LEGACY-001, LEGACY-002)
