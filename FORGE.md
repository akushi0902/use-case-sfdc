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
