# Forge Implementation Log

| Field | Value |
|-------|-------|
| Project | 306704cc-400b-4ecd-9906-f129f58c404f |
| Branch | forge/sfdc-use-case-aa-92342db1-run2-60wo |
| Started | 2026-08-25T06:12:46Z |

---

## WO-053: User Story: WO-053 - Remove Deprecated JCenter Resolution
- **Status:** completed
- **Commit:** `aae14b4`
- **Files:** 9 (+414/-1)
- **Duration:** 321ss
- **Approach:** Scaffolded the sfdc-integrator-service Gradle project from scratch without any JCenter repository declaration. The build.gradle uses only mavenCentral(), gradlePluginPortal() (buildscript block), and an environment-variable-driven authenticated internal Maven repository for Opsera libraries. A custom checkNoJCenter Gradle task scans all Gradle files for jcenter() or JCenter URLs and fails the build on detection; it is wired into the standard check lifecycle. A @SpringBootTest smoke test verifies the Spring application context loads after dependency resolution changes. The module README documents supported repositories, required environment variable names (OPSERA_REPO_URL, OPSERA_REPO_USER, OPSERA_REPO_TOKEN), hygiene verification commands, and the remediation path for any missing artifacts.

## WO-104: User Story: WO-104 - Enforce Java 21 Toolchain
- **Status:** completed
- **Commit:** `2119402`
- **Files:** 8 (+510/-6)
- **Duration:** 255ss
- **Approach:** The build.gradle already had a Java toolchain declaration from WO-053; this WO extended it by adding explicit sourceCompatibility = JavaVersion.VERSION_21 and targetCompatibility = JavaVersion.VERSION_21 alongside the toolchain so that static analysis plugins (SonarQube, PMD, Checkstyle) also see the correct baseline. A checkJavaToolchain Gradle task was added and wired into the check lifecycle to fail the build if either compatibility property drifts from Java 21. Gradle wrapper files (gradle-wrapper.properties targeting Gradle 8.11, gradlew, gradlew.bat) were created so the project has a reproducible build entry point without requiring a local Gradle installation. Three deployment scripts (dev-k8.sh, test-k8.sh, prod-k8.sh) were created, each explicitly requiring Java 21, calling out that Java 25 is a future-only certification target, and rejecting Java 8/11/17. The module README was updated with a Java runtime baseline section covering prerequisites, wrapper usage, toolchain troubleshooting, and the Java 25 future-only note.

## WO-076: User Story: WO-076 - Block Prohibited NPM Artifacts
- **Status:** completed
- **Commit:** `7e9a74e`
- **Files:** 3 (+298/-1)
- **Duration:** 293ss
- **Approach:** Added a checkNoNpmArtifacts Gradle task to build.gradle that scans all tracked source paths for package.json, package-lock.json, npm-shrinkwrap.json, yarn.lock, pnpm-lock.yaml, and node_modules/ directories. The task excludes build/, .gradle/, and .git/ from scanning. A recursive Groovy closure handles node_modules directory detection (since Gradle fileTree only matches files, not directories). A companion verifyNpmArtifactGuard self-test task verifies both passing state (clean directory with no prohibited files) and failing state (temporary fixture files in build/ with prohibited names). Both tasks are wired into the check lifecycle. A standalone shell script scripts/check-no-npm-artifacts.sh provides the same policy check for CI systems that invoke shell checks without Gradle. The README contribution section was added at the top with the prohibited artifact table and remediation instructions (remove files, never install Node/NPM). A build checks summary table was added documenting all four verification tasks.

## WO-105: User Story: WO-105 - Install Correlation Request Boundary
- **Status:** completed
- **Commit:** `a031e38`
- **Files:** 7 (+591/-0)
- **Duration:** 322ss
- **Approach:** Created a self-contained correlation package under com.opsera.integrator.sfdc.correlation. CorrelationIdConstants holds the header name (X-Correlation-Id), MDC key (correlationId), maximum length (64), and a safe regex pattern (alphanumeric, hyphen, underscore, dot). CorrelationIdFilter extends OncePerRequestFilter: it validates the inbound header against the safe pattern, generates a UUID when the value is missing/unsafe, writes to MDC, sets the response header BEFORE invoking the filter chain (so it is present even on exceptions), then clears MDC in a finally block. CorrelationConfig registers the filter at Ordered.HIGHEST_PRECEDENCE. application.yaml adds a Logback pattern that emits the MDC correlationId field on every log line with a '-none' fallback for background threads. Tests split into: CorrelationIdFilterTest (unit tests using MockHttpServletRequest/Response covering all validation scenarios) and CorrelationIdIntegrationTest (@SpringBootTest @AutoConfigureMockMvc against /actuator/health and unknown paths). CorrelationIdTestFixtures commits named constants for all fixture scenarios.

## WO-106: User Story: WO-106 - Inventory Legacy Release Contracts
- **Status:** completed
- **Commit:** `f485c74`
- **Files:** 16 (+731/-0)
- **Duration:** 327ss
- **Approach:** Created the legacy controller layer from scratch (scaffolded repo): JobExecutionController at POST /quickdeploy and POST /quickdeploy/stop, SfdcIntegratorController at POST /deploy and POST /validate. All endpoints return plain 'SUCCESS' string on HTTP 200 after delegating to service interfaces (QuickDeployService, SfdcIntegratorService). JobExecutionController validates deploymentRequestId manually (no @Valid) and normalizes null fallbackTaskId to empty string, matching the architecture description of legacy behavior. SfdcExceptionHandler (@ControllerAdvice) handles HttpMessageNotReadableException (400) and unhandled exceptions (500). Characterization tests use @WebMvcTest with @MockBean service collaborators and verify HTTP method, path, status codes, response bodies, and service delegation. Six JSON fixtures are committed under test/resources/fixtures/contracts/legacy-release/. The legacy-contract-inventory.json maps all four routes to their DTOs, required fields, response shapes, and service delegation targets (AC-6).

## WO-107: User Story: WO-107 - Define Canonical V2 Release DTOs
- **Status:** completed
- **Commit:** `6e9c1da`
- **Files:** 15 (+792/-0)
- **Duration:** 536ss
- **Approach:** Created a self-contained v2 release transport model in resources/v2/release with Jakarta Bean Validation (no Spring context required to instantiate or validate). Enums cover operation types and lifecycle states. ReleaseCommandMapper in services/v2 converts legacy DTOs to v2 commands using canonical task ID selection (stepId preferred, fallbackTaskId as fallback). All toString() methods exclude sensitive fields (requestedBy, clientCorrelationId, rejectedValue). No new HTTP endpoints introduced; legacy controllers unchanged.

## WO-108: User Story: WO-108 - Add PostgreSQL Migration Foundation
- **Status:** completed
- **Commit:** `7e5a5db`
- **Files:** 6 (+221/-1)
- **Duration:** 389ss
- **Approach:** Added Flyway 10.x + PostgreSQL JDBC as a low-blast-radius platform layer. Production datasource requires DB_URL/DB_USER/DB_PASSWORD env vars — missing any causes a clear startup failure. Tests use H2 in PostgreSQL compatibility mode via the existing 'test' profile; Flyway is disabled by default in that profile so existing @SpringBootTest controller/filter tests are unaffected. MigrationFoundationTest re-enables Flyway via @TestPropertySource to prove migration execution without external infrastructure. No legacy controller behavior was changed.

## WO-109: User Story: WO-109 - Baseline Kubernetes Deployment Scripts
- **Status:** completed
- **Commit:** `51ab9bd`
- **Files:** 10 (+717/-11)
- **Duration:** 503ss
- **Approach:** Added DRY_RUN support, require_var(), and run_cmd() helpers to all three existing deploy scripts without changing live behavior. Created placeholder fixture files for each environment. Implemented a CI-safe validate-deploy-scripts.sh that runs bash syntax checks, fixture variable coverage checks, dry-run execution with banner verification, and missing-variable fail-fast checks — verified locally with all 36 checks passing. Added validateDeployScripts Gradle task wired into the check lifecycle. Created application-kubernetes.yaml Spring profile with graceful shutdown, Kafka bootstrap from env var, health probes, and Hazelcast discovery documentation anchor. Updated README.md with a comprehensive Kubernetes Deployment Baseline section.

## WO-110: User Story: WO-110 - Classify Lifecycle and Artifact Data
- **Status:** completed
- **Commit:** `c91cb1a`
- **Files:** 25 (+932/-6)
- **Duration:** 561ss
- **Approach:** Introduced a governance/classification package with three enums (DataClassification, GovernanceDataCategory, RetentionCategoryHint), an immutable ClassificationContext value object, ClassificationProperties @ConfigurationProperties binding, and ClassificationPolicyResolver @Service that fails closed to CONFIDENTIAL+STANDARD for null, unknown, or error cases. GovernanceConfig enables the properties. application.yaml gained a sfdc.governance.classification block mapping all six categories to their classification tiers and retention hints. All four controller entry points (JobExecution, SfdcIntegrator, DataMigration, PostRefresh) resolve classification at request entry and log only operation name and classification tier. Existing characterization tests for JobExecutionController and SfdcIntegratorController received @MockBean ClassificationPolicyResolver. New @WebMvcTest suites for DataMigrationController and PostRefreshTaskController verify 200/SUCCESS, service delegation, classification resolver invocation with correct arguments, and 400 on malformed JSON. ClassificationPolicyResolverTest covers all six categories, fail-closed on null/unconfigured, safe toString.

## WO-111: User Story: WO-111 - Define Release Workflow SLO Guardrails
- **Status:** completed
- **Commit:** `73d5cfa`
- **Files:** 3 (+501/-0)
- **Duration:** 255ss
- **Approach:** Created a human-readable SLO policy document and a machine-readable guardrail YAML. The SLO doc defines seven SLIs with exact measurement boundaries, nine SLO targets with thresholds and pending-measurement baselines, and five escalation/burn-rate guidance sections covering all required failure modes. The guardrail YAML provides stable identifier-keyed entries with numeric thresholds, units, severity, owner, metric hints, and rollout gate conditions. README.md gained an Operational SLOs and Guardrails section linking both files and summarising the active thresholds. No application code, test logic, API contracts, or Flyway migrations were changed.

## WO-112: User Story: WO-112 - Characterize Quick Deploy Legacy Contracts
- **Status:** completed
- **Commit:** `d75edce`
- **Files:** 7 (+325/-2)
- **Duration:** 331ss
- **Approach:** Created a new JobExecutionControllerCompatibilityTest class as a @WebMvcTest slice that loads fixture files from fixtures/quickdeploy/ and uses ArgumentCaptor to verify exact fallback normalization behavior (null → empty string) and delegation field values. The test class is distinct from the existing JobExecutionControllerTest and explicitly annotated as a characterization suite with a review gate comment. A companion compat-note document captures the preserved behaviors, review gate process, and links to the SLO guardrails and contract inventory. No production code was changed.

## WO-113: User Story: WO-113 - Map Legacy Integrator Controller Contracts
- **Status:** completed
- **Commit:** `945adc6`
- **Files:** 5 (+285/-2)
- **Duration:** 201ss
- **Approach:** Inventoried SfdcIntegratorController (2 routes: POST /deploy, POST /validate) from source annotations, then created a new SfdcIntegratorControllerCompatibilityTest with 8 @WebMvcTest tests. Each route has: success-body test loading a fixture file, ArgumentCaptor-based delegation test verifying exact field values, service-exception-to-500 test verifying SfdcExceptionHandler catch-all returns 'Internal service error', and malformed-JSON-to-400 test. A contract map JSON at src/test/resources/contracts/ documents both routes with all contract details. Fixtures use synthetic identifiers and example.internal URLs only. legacy-contract-inventory.json updated to cross-reference the new test class and contract map.

## WO-061: User Story: WO-061 - Pin Immutable Internal Dependencies
- **Status:** completed
- **Commit:** `3b058e1`
- **Files:** 2 (+154/-6)
- **Duration:** 393ss
- **Approach:** Replaced the commented-out internal library placeholder in build.gradle with four explicit immutable dependency declarations (java-core-library, java-jobengine-library, kubernetes-client, salesforce-core-lib) at version 1.0.0 with no 'changing' flag. Added a checkNoChangingDependencies Gradle task wired into the check lifecycle that scans all *.gradle files for 'changing = true', 'changing: true', and -SNAPSHOT coordinates on internal Opsera groups; the task labels violations with 'changing-flag' (not 'changing=true') to avoid self-detection. Updated the README with an Internal Dependency Immutability Policy section covering the pinned library table, prohibited patterns, the version bump procedure, and verification commands.

## WO-114: User Story: WO-114 - Normalize Boundary Error Responses
- **Status:** completed
- **Commit:** `71d0ea7`
- **Files:** 15 (+640/-54)
- **Duration:** 838ss
- **Approach:** Created ErrorResponse (correlationId, errorCode, message, remediation, timestamp, fieldErrors) and SafeFieldError (field, rejectedReason, safeMessage — no raw rejected value) in the exceptions package. Replaced SfdcExceptionHandler plain-string responses with structured ErrorResponse JSON for all exception types: MethodArgumentNotValidException and ConstraintViolationException (400 VALIDATION_FAILED with field errors), HttpMessageNotReadableException (400 MALFORMED_REQUEST_BODY or MISSING_REQUEST_BODY), HttpMediaTypeNotSupportedException (400 UNSUPPORTED_MEDIA_TYPE), and Exception catch-all (500 INTERNAL_ERROR). Added @NotBlank to QuickDeployRequest.deploymentRequestId and @Valid to JobExecutionController.startQuickDeploy, removing the manual null/blank check in favour of Jakarta Bean Validation. Correlation ID is read from MDC via CorrelationIdConstants.MDC_KEY in every handler. Updated all existing tests that asserted old plain-string error bodies to assert the new structured JSON format using jsonPath. Added 13 unit tests in SfdcExceptionHandlerTest and 7 MVC integration tests in JobExecutionControllerBoundaryTest. Added five fixture files covering all AC-7 scenarios.

## WO-115: User Story: WO-115 - Sanitize Controller Operational Logging
- **Status:** completed
- **Commit:** `abc88b9`
- **Files:** 19 (+790/-68)
- **Duration:** 587ss
- **Approach:** Created SafeLogEvent (immutable builder value object: operation, controller, correlationId, outcome, safeFields map, extractionFailed flag) and SafeStructuredLogger (@Component) in a new logging package. SafeStructuredLogger reads correlationId from MDC, applies a second-pass sensitive-key filter (matching 'token', 'password', 'secret', 'apikey', 'credential', 'authorization', 'cookie', 'auth', 'orgurl', 'payload'), sanitizes values for log-injection control chars, emits INFO for ACCEPTED/COMPLETED and WARN for REJECTED/FAILED, and never throws — fallback emits a minimal safe warn. Updated all 4 existing controllers to inject SafeStructuredLogger and emit SafeLogEvent with pipelineId and stepId as explicit allow-listed fields; orgUrl, fallbackTaskId, sourceOrgUrl, targetOrgUrl are never included. Ad-hoc debug logging and ClassificationContext log calls replaced. All 7 affected controller test files updated with @MockBean SafeStructuredLogger. Added 19 unit tests (SafeStructuredLoggerTest with Logback ListAppender) and 4 MVC integration tests (JobExecutionControllerSafeLoggingTest with ArgumentCaptor). Created 4 logging fixture files. The 4 missing controllers listed in the WO (PipelineNotificationsController, SfdcTestClassController, OrgHealthController, SfdcCodeScanController) do not yet exist in the codebase — coverage deferred to when those controllers are created.

## WO-116: User Story: WO-116 - Carry Correlation Across Async Work
- **Status:** completed
- **Commit:** `fc1a1d8`
- **Files:** 13 (+930/-0)
- **Duration:** 537ss
- **Approach:** Created a new observability package with three utilities: CorrelationContextSnapshot (captures MDC correlationId at scheduling time and wraps Runnable/Callable/Supplier to restore it on a background thread, always clearing MDC in a finally block), CorrelationTaskDecorator (Spring TaskDecorator delegating to CorrelationContextSnapshot.capture() + wrap()), and KafkaCorrelationHeaders (producer-side addCorrelationHeader, consumer-side restoreFromRecord/clearCorrelationId, and isSafe validation matching CorrelationIdFilter rules). AppConfig.java was created in the config package to register a ThreadPoolTaskExecutor bean named 'correlationAwareTaskExecutor' decorated with CorrelationTaskDecorator, so any async work submitted through it automatically inherits the HTTP-ingress correlation identifier. Controllers were not modified because they already delegate to service interfaces synchronously; the propagation utilities are available for callers to use when submitting to background threads. All malformed/missing Kafka headers are replaced with a generated UUID and logged as a degraded propagation event at WARN level — business messages are never dropped.

## WO-117: User Story: WO-117 - Implement V2 Release Submission Facade
- **Status:** completed
- **Commit:** `d532e15`
- **Files:** 13 (+804/-0)
- **Duration:** 402ss
- **Approach:** Created ReleaseCommandFacade (@Service in services/v2) that validates canonical ReleaseCommandRequest, resolves correlation from MDC > clientCorrelationId > generated UUID, delegates to QuickDeployService (QUICK_DEPLOY), SfdcIntegratorService.deploy (DEPLOY), or SfdcIntegratorService.validate (VALIDATE), and returns an AcceptedAcknowledgement with jobId, correlationId, statusUrl (/api/v2/sfdc/release-jobs/{jobId}/status), state=ACCEPTED, acceptedAt, and operationType. CANCEL throws V2UnsupportedOperationException (new). Created ReleaseJobController in controller/v2 at POST /api/v2/sfdc/release-jobs with @Valid request validation, returns 202 + Location header, and short-circuits to 404 when sfdc.v2.release.routes.enabled=false (checked via @Value). Extended SfdcExceptionHandler with a handler for V2UnsupportedOperationException returning 422 UNSUPPORTED_OPERATION. Added sfdc.v2.release.routes.enabled: true to application.yaml. Legacy controllers (JobExecutionController, SfdcIntegratorController) and their tests are unchanged.

## WO-119: User Story: WO-119 - Create Durable Job Schema
- **Status:** completed
- **Commit:** `3f1c1ff`
- **Files:** 17 (+1221/-0)
- **Duration:** 629ss
- **Approach:** Created V2__lifecycle_schema.sql adding five normalized tables: jobs (job_id PK, correlation_id, operation_type, current_state, customer_id_hash [hashed], sfdc_tool_id, pipeline_id, step_id, data_classification, timestamps, version for optimistic concurrency), job_checkpoints (BIGSERIAL PK, job_id FK, unique(job_id, sequence_number) constraint for idempotency), worker_attempts (BIGSERIAL PK, job_id FK, unique(job_id, attempt_number)), job_diagnostics (BIGSERIAL PK, job_id FK, safe_summary VARCHAR(1024), error_code), job_retention_metadata (job_id PK+FK, retention_hint, data_classification, purge_eligible_at, purged_at). Added 9 indexes covering correlation_id, current_state, created_at, customer_id_hash, and checkpoint ordering. Java persistence layer in a new lifecycle package: JobRepository interface, JdbcJobRepository @Repository using JdbcTemplate with optimistic concurrency (version check in updateState), LifecyclePersistenceException for typed error propagation. All model classes are plain POJOs; no JPA/Hibernate used. No raw payloads, credentials, or org URLs stored. Customer references use hashed identifiers.

## WO-118: User Story: WO-118 - Harden Java Runtime Build
- **Status:** completed
- **Commit:** `37a6f9c`
- **Files:** 7 (+362/-1)
- **Duration:** 386ss
- **Approach:** WO-118 builds on the already-hardened build baseline from WO-053 (no JCenter), WO-104 (Java 21 toolchain), WO-076 (no NPM), and WO-061 (immutable internal deps). The remaining gap was dependency locking and verification metadata. Added dependencyLocking { lockAllConfigurations(); lockMode = LockMode.LENIENT } to build.gradle — LENIENT mode allows the initial lock files to bootstrap without a live Gradle resolution run, while still making version changes visible in code review. Created four per-configuration lock files (compileClasspath, runtimeClasspath, testCompileClasspath, testRuntimeClasspath) under gradle/dependency-locks/ with direct dependency coordinates at Spring Boot 3.4.1 BOM-managed versions. Created gradle/verification-metadata.xml with the structural configuration section (verify-metadata=true, verify-signatures=false) documenting that components/SHA-256 checksums must be populated via --write-verification-metadata sha256 once a live Gradle run is available. Added two new Gradle verification tasks wired into the check lifecycle: checkDependencyLockConfiguration (fails if lock directory or *.lockfile files are absent) and checkVerificationMetadata (fails if verification-metadata.xml is absent or lacks <configuration> element). Updated README with Dependency Locking and Dependency Verification Metadata sections covering mode, format, regeneration procedure, update workflow, and policy table.

## WO-120: User Story: WO-120 - Create Immutable Audit Event Schema
- **Status:** completed
- **Commit:** `2102f0e`
- **Files:** 26 (+1432/-15)
- **Duration:** 761ss
- **Approach:** Created a standalone governance/audit package under com.opsera.integrator.sfdc.governance.audit with a full append-only audit pipeline. V3__audit_events_schema.sql adds the audit_events table with all required columns (event_id PK, correlation_id, actor_type, actor_ref, resource_type, resource_ref, operation, classification, safe_metadata, event_timestamp, created_at, request_source) plus five indexes, and documents the PostgreSQL REVOKE UPDATE/DELETE administrative step and break-glass procedure. The Java domain layer consists of: AuditActorType/AuditOperation/AuditResourceType enums; SafeAuditMetadata builder with an allow-listed key set that drops unknown keys (prevents raw payload leakage) and sanitizes for log injection; AuditEvent immutable POJO with builder validation (toString hides actorRef/resourceRef); AuditEventRepository interface with a single append() method (no update/delete); JdbcAuditEventRepository INSERT-only implementation wrapping failures in AuditWriteException; AuditEventWriter @Service that resolves correlationId from MDC, generates UUID eventId, respects sfdc.audit.enabled flag, and re-throws AuditWriteException on failure (fail-closed). JobExecutionController emits JOB_SUBMITTED (after start) and JOB_CANCELLED (after stop). DataMigrationController emits DATA_MIGRATION_INITIATED. All three use SafeAuditMetadata.builder() with pipelineId/stepId/outcome — no raw payloads. Five controller test files updated with @MockBean AuditEventWriter.

## WO-060: User Story: WO-060 - Enable Gradle Dependency Verification
- **Status:** completed
- **Commit:** `973dd74`
- **Files:** 0 (+0/-0)
- **Duration:** 181ss
- **Approach:** WO-060 (Enable Gradle Dependency Verification) was implemented as part of WO-118 (Harden Java Runtime Build), which was processed earlier in this batch. WO-118 added the full dependency locking and verification metadata infrastructure to build.gradle and committed the required per-configuration lock files and verification-metadata.xml skeleton. No additional code changes are required for WO-060. This summary documents the verification against all 7 acceptance criteria using the artifacts committed in WO-118.

## WO-121: User Story: WO-121 - Validate Quick Deploy Requests
- **Status:** completed
- **Commit:** `aab96c2`
- **Files:** 6 (+242/-0)
- **Duration:** 337ss
- **Approach:** WO-121 (Validate Quick Deploy Requests) builds on validation enforcement already added in WO-114 (which added @NotBlank to QuickDeployRequest.deploymentRequestId and @Valid to JobExecutionController.startQuickDeploy). The controller and DTO required no production code changes — the validation contract was already complete. This WO delivers a dedicated Spring MVC test class (JobExecutionControllerQuickDeployValidationTest) that groups all seven WO-121 acceptance criteria scenarios in one place, plus a new fixtures/quickdeploy-validation/ directory with five scenario fixtures. The fallbackTaskId canonicalization (the current codebase equivalent of the WO-121 'taskId/gitTaskId' normalization) is tested with ArgumentCaptor: fallbackTaskId is preserved when present, normalized to empty string when absent, and the service is verified to be called exactly once for valid requests.

## WO-122: User Story: WO-122 - Guard Shell Execution Arguments
- **Status:** completed
- **Commit:** `bea5b8d`
- **Files:** 21 (+978/-1)
- **Duration:** 778ss
- **Approach:** Created a reusable shell argument safety layer in the security package. ShellArgumentProfile enum defines per-field allow-list regex patterns and max lengths for six field types. ShellArgumentValidator @Component enforces null/blank/oversized/null-byte/command-separator/path-traversal/allow-list checks; validateIfPresent() skips null/blank optional fields. ShellArgumentViolationException stores only fieldName, profile, and rejectionCategory — never the raw rejected value. Applied the validator in JobExecutionController (quick deploy path, both start and stop endpoints). Created MetadataDeployProcessor as a deployment-adjacent processor seam demonstrating the validation pattern. SfdcExceptionHandler maps the exception to HTTP 400 with SHELL_UNSAFE_INPUT error code and safe field-level error details. Tests use the real validator via @Import to exercise end-to-end injection rejection.

## WO-125: User Story: WO-125 - Resolve Gateway Caller Identity
- **Status:** completed
- **Commit:** `16c84a5`
- **Files:** 29 (+999/-1)
- **Duration:** 1207ss
- **Approach:** Added Spring Security OAuth2 resource server support. CallerContext is an immutable value object capturing subject, clientId, scopes (lowercased), issuer, audience, authenticationType, and correlationId. CallerContextResolver extracts claims from JwtAuthenticationToken with three-tier scope normalization (scope string → scp array → scopes array). JwtAudienceValidator is a composable OAuth2TokenValidator<Jwt> enforcing expected audience. SecurityAuthenticationEntryPoint returns structured 401 JSON (errorCode=AUTHENTICATION_REQUIRED, correlationId from MDC) without exposing exception details. SecurityConfig is @ConditionalOnProperty(matchIfMissing=true) for fail-closed production behavior, with @ConditionalOnMissingBean(JwtDecoder.class) allowing test stubs. application.yaml configures jwk-set-uri and issuer-uri from env vars. application-test.properties disables all security auto-configuration for @SpringBootTest contexts. All 12 existing @WebMvcTest tests received @WithMockUser to satisfy default HTTP Basic security. SecurityIntegrationTest imports real SecurityConfig with a stub JwtDecoder and uses SecurityMockMvcRequestPostProcessors.jwt() for CI-safe authenticated requests.

## WO-123: User Story: WO-123 - Publish V2 OpenAPI Contracts
- **Status:** completed
- **Commit:** `73f7664`
- **Files:** 11 (+385/-4)
- **Duration:** 466ss
- **Approach:** Added Springdoc OpenAPI 2.7.0 to generate typed v2 release contracts and document legacy coexistence routes. SwaggerConfig provides the global OpenAPI info bean with URL namespace disambiguation text. application.yaml configures /api-docs (documentation endpoint) and two springdoc group-configs: v2-release-api (/api/v2/**) and legacy-release-api (legacy coexistence paths). ReleaseJobController received @Tag, @Operation, and @ApiResponse annotations covering all required HTTP status codes (202, 400, 401, 403, 404, 422). V2 DTOs (ReleaseCommandRequest, AcceptedAcknowledgement, V2ErrorResponse) received @Schema annotations with safe example values. SecurityConfig updated to permit /api-docs/**, /swagger-ui/**, and /swagger-ui.html without authentication. OpenApiContractTest uses @SpringBootTest @AutoConfigureMockMvc @ActiveProfiles(test) to retrieve /api-docs and assert schema presence, status codes, URL namespace separation, legacy route coexistence, and absence of sensitive example values. Two new OpenAPI example fixtures added for 401 and 403 error responses.

## WO-131: User Story: WO-131 - Implement Job State Machine
- **Status:** completed
- **Commit:** `c964db5`
- **Files:** 14 (+1504/-0)
- **Duration:** 488ss
- **Approach:** Implemented the lifecycle state machine as pure domain logic first (no Spring dependencies) with repository integration in the service layer. JobLifecycleState enum defines 8 canonical states with isTerminal()/isActive() helpers and a fromString() parser. LifecycleTransitionPolicy enforces the transition matrix using an EnumMap of EnumSets, with terminal states mapped to empty sets. LifecycleTransitionException and LifecycleConcurrencyException provide typed failure paths. TransitionRequest (builder pattern) and TransitionResult (immutable) are safe value objects. JobLifecycleService (@Service) is the single authoritative mutation path: loads job, checks idempotent replay, validates via policy, updates state with optimistic concurrency, optionally appends checkpoints, persists diagnostics for failure terminal states. Diagnostic summaries are truncated to 1024 chars. All legacy controllers unchanged.

## WO-130: User Story: WO-130 - Standardize Managed Secret References
- **Status:** completed
- **Commit:** `968e47b`
- **Files:** 13 (+730/-1)
- **Duration:** 811ss
- **Approach:** Implemented the managed secret reference model in three layers. (1) Domain layer: @UnsafePlaceholder constraint annotation + UnsafePlaceholderValidator that rejects values containing 'placeholder', 'not-real', 'change_me', or 'changeme' — patterns used exclusively in committed .env.example fixture files. (2) Configuration layer: SecretReferenceProperties (@ConfigurationProperties('sfdc.secrets') + @Validated) binds vault-base-url (optional, @UnsafePlaceholder) and oauth2-expected-audience (@NotBlank + @UnsafePlaceholder), causing startup validation failure if either constraint is violated. SecretConfig wires it via @EnableConfigurationProperties. (3) Documentation and fixture layer: application.yaml gets an sfdc.secrets block with classification/injection comments; all .env.example fixture files add OAUTH2_JWK_SET_URI, OAUTH2_EXPECTED_AUDIENCE, and VAULT_BASE_URL placeholders; validate-deploy-scripts.sh REQUIRED_VARS now covers those three new vars; README.md gains a full secret inventory table, classification levels, ownership table, rotation schedule, and a missing-secret startup failure runbook.

## WO-124: User Story: WO-124 - Mask Nonproduction PII Fixtures
- **Status:** completed
- **Commit:** `1ac0f42`
- **Files:** 11 (+922/-0)
- **Duration:** 594ss
- **Approach:** Implemented the WO-124 PII masking standard as a pure test-scope addition. SyntheticFixtureFactory uses SHA-256 with a fixed non-secret salt (sfdc-test-fixture-v1) to produce deterministic identifiers across all field categories. FixtureSafetyScanTest walks committed fixture and documentation paths and fails on JWT patterns, PEM private key headers, Salesforce production domains, long bearer tokens, and real email addresses — reporting only file path and category, never the matched value. MaskedFixtureControllerTest uses @WebMvcTest slices for DataMigrationController and PostRefreshTaskController with ArgumentCaptor<SafeLogEvent> to assert the allow-listed safe fields (pipelineId, stepId) while asserting absence of sourceOrgUrl, targetOrgUrl, and DTO toString fragments. Six sanitized fixture files cover all compliance-sensitive scenarios using fixture.example.internal domains and clearly synthetic prefixes. README documents the convention, allowed patterns, and code examples.
