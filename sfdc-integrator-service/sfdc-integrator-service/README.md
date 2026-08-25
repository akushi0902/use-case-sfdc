# SFDC Integrator Service — Build Notes

## Contribution Policy: NPM Artifacts Are Prohibited

**This is a Java and Gradle service. NPM package manager artifacts are NOT permitted in commits.**

The following files and directories are prohibited and will fail the release build if committed:

| Prohibited artifact | Why |
|---|---|
| `package.json` | NPM package manifest — not applicable to a Java service |
| `package-lock.json` | NPM lockfile — not applicable |
| `npm-shrinkwrap.json` | Legacy NPM lockfile — not applicable |
| `yarn.lock` | Yarn lockfile — not applicable |
| `pnpm-lock.yaml` | pnpm lockfile — not applicable |
| `node_modules/` | NPM dependency tree — not applicable |

The `checkNoNpmArtifacts` Gradle task and `scripts/check-no-npm-artifacts.sh` shell script
enforce this policy automatically on every release build. See _Build Checks_ below.

**If you see a build failure caused by prohibited NPM files:**
1. Remove the offending files from the repository.
2. Do not install Node, NPM, Yarn, or pnpm to fix the error — the fix is removal, not installation.
3. Re-run `./gradlew checkNoNpmArtifacts` to confirm the guard passes.

---

## Supported Artifact Repositories

Gradle resolves all dependencies and plugins exclusively from the following approved repositories:

| Repository | Gradle Declaration | Purpose |
|---|---|---|
| Maven Central | `mavenCentral()` | Open source Java libraries and Spring Boot dependencies |
| Gradle Plugin Portal | `gradlePluginPortal()` | Gradle build plugins (buildscript block only) |
| Internal Opsera Package Manager | authenticated `maven { url ... }` | Opsera-internal libraries required for compilation |

**JCenter (`jcenter.bintray.com`) is NOT used and MUST NOT be re-added.**
JCenter is a deprecated repository with weak artifact availability and provenance guarantees.
Re-adding it creates supply-chain risk. See _Repository Hygiene_ below.

---

## Required Environment Variables (Internal Package Manager)

The internal Opsera package manager requires authentication credentials injected at build time.
**Never commit credentials to source control or print them in build logs.**

| Variable | Purpose |
|---|---|
| `OPSERA_REPO_URL` | Full URL of the internal Maven repository (e.g. `https://npm.pkg.github.com/` or internal Nexus/Artifactory URL) |
| `OPSERA_REPO_USER` | Username or service account name for authentication |
| `OPSERA_REPO_TOKEN` | Password or token for authentication (rotate every 90 days) |

### Local Development

Set these variables in your shell profile or a local `.env` file that is **not committed** (`.env` is in `.gitignore`):

```bash
export OPSERA_REPO_URL="https://<internal-registry-host>/repository/maven-opsera/"
export OPSERA_REPO_USER="<your-username>"
export OPSERA_REPO_TOKEN="<your-token>"
```

### CI / CD

Inject these from your CI secret store (GitHub Actions secrets, Vault, or equivalent).
Do not echo or print them in pipeline output.

If `OPSERA_REPO_URL` is not set, the internal repository block is skipped and any artifact
that requires it will fail with a clear dependency resolution error identifying the missing
coordinate — not a credentials leak.

---

---

## Secret Inventory and Classification

All build-time and runtime configuration inputs are classified below. No real credentials may be committed to source control — use the injection mechanisms listed.

### Classification Levels

| Level | Definition |
|---|---|
| `NON_SECRET` | Public or semi-public value (e.g. service endpoint URL). May appear in non-sensitive logs. |
| `CONFIDENTIAL` | Internal reference or metadata. Must not appear in error messages or public-facing responses. |
| `RESTRICTED` | Credential with direct access capability (password, token, key). Must never appear in any log, CLI output, or error message. |

### Runtime Secret Inventory

| Env Var | Classification | Injection Mechanism | Required For | Notes |
|---|---|---|---|---|
| `DB_URL` | NON_SECRET | Kubernetes Secret (env) | API + Worker | Spring fails startup if absent |
| `DB_USER` | CONFIDENTIAL | Kubernetes Secret (env) | API + Worker | Spring fails startup if absent |
| `DB_PASSWORD` | **RESTRICTED** | Kubernetes Secret (env) | API + Worker | Must never appear in logs. Spring fails startup if absent. Rotate every 90 days. |
| `KAFKA_BOOTSTRAP_SERVERS` | NON_SECRET | Kubernetes ConfigMap (env) | API + Worker | `kubernetes` profile only. Spring fails if active and absent. |
| `OAUTH2_JWK_SET_URI` | NON_SECRET | Kubernetes ConfigMap (env) | API pod | Public JWKS endpoint. Empty default means JWT validation fails silently — always set. |
| `OAUTH2_ISSUER_URI` | NON_SECRET | Kubernetes ConfigMap (env) | API pod | OIDC discovery alternative to `OAUTH2_JWK_SET_URI`. Use one or the other. |
| `OAUTH2_EXPECTED_AUDIENCE` | CONFIDENTIAL | Kubernetes ConfigMap (env) | API pod | Validated at startup via `SecretReferenceProperties`. Startup fails if blank or unsafe placeholder. Default: `sfdc-integrator`. |
| `VAULT_BASE_URL` | NON_SECRET | Kubernetes ConfigMap (env) | Future | Vault integration placeholder. Optional. Validated if set — must not contain unsafe placeholder patterns. |

### Build-Time Secret Inventory

| Env Var | Classification | Injection Mechanism | Required For | Notes |
|---|---|---|---|---|
| `OPSERA_REPO_URL` | NON_SECRET | CI/CD secret store | Build only | Internal Maven repo URL. Build skips internal repo block if absent. |
| `OPSERA_REPO_USER` | CONFIDENTIAL | CI/CD secret store | Build only | Service account username. Filtered from build output. |
| `OPSERA_REPO_TOKEN` | **RESTRICTED** | CI/CD secret store | Build only | Auth token. Must never appear in build logs. Rotate every 90 days. |

### Secret Ownership and Rotation

| Secret | Owner | Rotation Period | Rotation Process |
|---|---|---|---|
| `DB_PASSWORD` | Platform DBA team | 90 days | Update Kubernetes Secret, rolling restart |
| `OPSERA_REPO_TOKEN` | Platform CI team | 90 days | Rotate in CI secret store, update all pipelines |
| `OAUTH2_EXPECTED_AUDIENCE` | Platform Security team | On service rename | Update ConfigMap and redeploy |

### Startup Failure Runbook (Missing or Unsafe Secret Reference)

**Symptom:** Application fails to start with a validation error mentioning a configuration property.

**Diagnosis and remediation by error type:**

| Error pattern | Cause | Remediation |
|---|---|---|
| `Could not resolve placeholder 'DB_URL'` | `DB_URL` env var not set | Inject `DB_URL` from the Kubernetes Secret in the pod spec |
| `sfdc.secrets.oauth2-expected-audience must not be blank` | `OAUTH2_EXPECTED_AUDIENCE` is blank or missing | Set `OAUTH2_EXPECTED_AUDIENCE` in the Kubernetes ConfigMap and redeploy |
| `Configuration value appears to contain an unsafe sample placeholder` | A fixture `.env.example` value was used as real config | Replace the placeholder with the actual endpoint URL or secret reference |
| `Unable to obtain connection from datastore` | PostgreSQL unreachable or DB_PASSWORD wrong | Verify network policy, `DB_URL`, `DB_USER`, `DB_PASSWORD` in Kubernetes Secrets |

**General steps:**
1. Check pod logs: `kubectl logs -l app=sfdc-integrator-service -n <namespace> --tail=50`
2. The error message names the missing or invalid property — not the value.
3. Verify the Kubernetes Secret or ConfigMap contains the required key: `kubectl get secret <name> -n <namespace> -o jsonpath='{.data}' | tr ',' '\n'`
4. Verify the pod spec mounts the key as an env var: `kubectl describe pod <pod-name> -n <namespace>`
5. After correcting the Secret/ConfigMap, trigger a rolling restart: `kubectl rollout restart deployment/sfdc-integrator-service -n <namespace>`

**Never print the value in a bug report or Slack message** — report only the variable name and the environment.

---

## Building the Service

```bash
# Resolve dependencies and run checks (includes repository hygiene check)
./gradlew check

# Run tests only
./gradlew test

# Build executable JAR
./gradlew bootJar

# Validate no JCenter is declared anywhere in Gradle build files
./gradlew checkNoJCenter
```

---

## Repository Hygiene Verification

A custom Gradle task `checkNoJCenter` scans all `*.gradle` and `*.gradle.kts` files for
`jcenter()` or any `jcenter.bintray.com` URL and fails the build if found:

```bash
./gradlew checkNoJCenter
```

This task is wired into the standard `check` lifecycle, so it runs automatically with:

```bash
./gradlew check
```

**Reviewers:** Run `./gradlew checkNoJCenter` to confirm no JCenter declaration exists
before approving any pull request that touches `build.gradle` or `settings.gradle`.

You can also verify manually with the following shell command:

```bash
grep -rn --include="*.gradle" --include="*.kts" \
  -E '(jcenter\(\)|https?://jcenter\.bintray\.com)' \
  . && echo "VIOLATION: JCenter found" || echo "OK: No JCenter declarations"
```

---

## Dependency Resolution Failures

If a dependency cannot be resolved after JCenter removal:

1. **Identify the missing coordinate** from the Gradle error output — it will show the group, artifact, and version.
2. **Check Maven Central** (`search.maven.org`) for the artifact or a newer equivalent.
3. **Check whether an Opsera-maintained fork or upgrade is available** in the internal package manager.
4. **Mirror the artifact** to the internal repository if it is not available on Maven Central.
5. **Do NOT restore JCenter access** — this recreates the supply-chain risk this change eliminated.

The build error output will identify the missing coordinate and will not suggest restoring JCenter.

---

## Java Runtime Baseline

### Production Baseline: Java 21 (LTS)

Java 21 is the **required and only supported runtime** for this service.

| Component | Version | Notes |
|---|---|---|
| Java | **21 (LTS)** | Required for compilation, tests, and container runtime |
| Spring Boot | 3.4.1 | Requires Java 17+ |
| Gradle Wrapper | 8.11 | See `gradle/wrapper/gradle-wrapper.properties` |
| Container base image | `eclipse-temurin:21-jre` | Or equivalent Java 21 JRE image |

**Java 25 is a future certification target only.** It is not approved for production deployment
and must not replace Java 21 as the runtime baseline for this story or until a separate
certification effort is completed.

Do not use Java 8, Java 11, or Java 17 — they are unsupported for this service.

---

## Build Prerequisites

Before building, ensure the following are available:

1. **Java 21 JDK** — install via [SDKMAN](https://sdkman.io/), [Adoptium](https://adoptium.net/), or your package manager:
   ```bash
   # SDKMAN example
   sdk install java 21.0.5-tem
   sdk use java 21.0.5-tem
   java -version  # should print openjdk version "21.x.x"
   ```
2. **OPSERA_REPO_URL, OPSERA_REPO_USER, OPSERA_REPO_TOKEN** — internal package manager credentials (see above).
3. **Gradle Wrapper** — use `./gradlew` (no local Gradle installation required). The wrapper downloads Gradle 8.11 automatically.

---

## Building the Service

```bash
# Resolve dependencies and run all checks
# (includes JCenter hygiene, Java toolchain, NPM artifact guard,
#  dependency locking configuration, and verification metadata check)
./gradlew check

# Run tests only
./gradlew test

# Build executable JAR
./gradlew bootJar

# Verify Java 21 toolchain is configured correctly
./gradlew checkJavaToolchain

# Validate no JCenter is declared anywhere in Gradle build files
./gradlew checkNoJCenter

# Verify no prohibited NPM artifacts are present
./gradlew checkNoNpmArtifacts

# Self-test the NPM artifact guard (proves passing and failing states)
./gradlew verifyNpmArtifactGuard

# Standalone NPM artifact check (no Gradle required — for CI shell pipelines)
./scripts/check-no-npm-artifacts.sh

# Print toolchain diagnostics
./gradlew -q javaToolchains

# Regenerate dependency lock files after a version change
./gradlew dependencies --write-locks

# Generate/update dependency verification checksums
./gradlew --write-verification-metadata sha256 dependencies

# Verify lock file directory and verification metadata exist
./gradlew checkDependencyLockConfiguration
./gradlew checkVerificationMetadata
```

---

## Build Checks Summary

| Gradle Task | Lifecycle | What It Checks |
|---|---|---|
| `checkNoJCenter` | `check` | No JCenter repository in any `*.gradle` file |
| `checkJavaToolchain` | `check` | `sourceCompatibility` and `targetCompatibility` are Java 21 |
| `checkNoNpmArtifacts` | `check` | No prohibited NPM artifacts in tracked source paths |
| `verifyNpmArtifactGuard` | `check` | Self-test: guard detects prohibited files and passes clean state |
| `validateDeployScripts` | `check` | Syntax and dry-run validation on all Kubernetes deployment scripts |
| `checkNoChangingDependencies` | `check` | No `changing = true` or SNAPSHOT coordinates for internal Opsera artifacts |
| `checkDependencyLockConfiguration` | `check` | Lock file directory and `*.lockfile` files are present |
| `checkVerificationMetadata` | `check` | `gradle/verification-metadata.xml` exists with a `<configuration>` element |

All tasks in the table above run automatically when you run `./gradlew check` or `./gradlew build`.

---

## Dependency Locking

Gradle dependency locking records the exact resolved version of every direct and transitive dependency into per-configuration lock files committed to source control. When a dependency version changes — intentionally or via a supply-chain substitution — the lock file diff is visible in code review.

### Lock File Location

```
gradle/dependency-locks/
  compileClasspath.lockfile
  runtimeClasspath.lockfile
  testCompileClasspath.lockfile
  testRuntimeClasspath.lockfile
```

### Locking Mode

Dependency locking is currently enabled in **LENIENT mode**. In this mode:
- New or changed dependencies generate a warning but do not fail the build.
- Lock file content serves as an authoritative record of resolved versions for code review.

To promote to **STRICT mode** (fail on any deviation from the lock file), change `lockMode = LockMode.LENIENT` to `lockMode = LockMode.STRICT` in `build.gradle` after the lock files have been fully bootstrapped with a real resolution run.

### Regenerating Lock Files

After adding, removing, or bumping a dependency:

```bash
# Regenerate all configuration lock files
./gradlew dependencies --write-locks

# Commit the updated lock files alongside the build.gradle change
git add gradle/dependency-locks/
git commit -m "Refresh dependency lock files after bumping <dependency>"
```

### Lock File Format

Each `*.lockfile` lists one resolved coordinate per line:

```
group:artifact:version=configA,configB,...
empty=
```

The `empty=` sentinel at the end is always written by Gradle and must be preserved.

---

## Dependency Verification Metadata

`gradle/verification-metadata.xml` provides checksum-based verification of resolved dependency artifacts. When checksums are populated, Gradle verifies every downloaded JAR against the recorded SHA-256 before allowing compilation. A changed byte (supply-chain substitution or mutable artifact re-publish) fails the build immediately.

### Bootstrap State

The committed file contains structural configuration only (`<configuration>` element with `verify-metadata: true`, `verify-signatures: false`). The `<components>` section must be populated with real checksums before verification is enforced:

```bash
# Generate SHA-256 checksums for all resolved dependencies
./gradlew --write-verification-metadata sha256 dependencies

# Commit the updated file
git add gradle/verification-metadata.xml
git commit -m "Populate dependency verification checksums"
```

### Updating Checksums

When a dependency is bumped or added:

1. Bump the version in `build.gradle`.
2. Run `./gradlew --write-verification-metadata sha256 dependencies`.
3. Review the diff — only the expected coordinates should change.
4. Also refresh lock files: `./gradlew dependencies --write-locks`.
5. Commit `gradle/verification-metadata.xml` and lock files together with the version change.

### Policy

| Setting | Value | Reason |
|---|---|---|
| `verify-metadata` | `true` | POM and module metadata verified, not just JARs |
| `verify-signatures` | `false` | GPG verification deferred; requires keyserver access in CI |

**Never commit real credentials, tokens, or connection details to this file.** It stores only dependency coordinates and checksums.

---

## Internal Dependency Immutability Policy

All internal Opsera library dependencies must be declared with explicit, immutable release version coordinates. This policy ensures release builds are reproducible: the same source revision always produces the same application binary, and incident investigations can reconstruct the exact dependency graph used by a deployed artifact.

### Pinned Internal Libraries

| Coordinate | Declared Version | Required Env |
|---|---|---|
| `com.opsera:java-core-library` | `1.0.0` | `OPSERA_REPO_URL` |
| `com.opsera:java-jobengine-library` | `1.0.0` | `OPSERA_REPO_URL` |
| `com.opsera:kubernetes-client` | `1.0.0` | `OPSERA_REPO_URL` |
| `com.opsera.salesforce:salesforce-core-lib` | `1.0.0` | `OPSERA_REPO_URL` |

All four artifacts are resolved from the authenticated internal Opsera package manager (`OPSERA_REPO_URL`). If resolution fails, mirror the coordinate to the internal repository — **do not restore JCenter or add a `changing` fallback**.

### Prohibited Patterns

The following patterns are prohibited for internal Opsera dependencies and are detected by the `checkNoChangingDependencies` Gradle task:

| Pattern | Why prohibited |
|---|---|
| `changing = true` | Allows artifact content to change under a cached version coordinate, making builds non-reproducible |
| `changing: true` | Same — alternative Gradle syntax |
| `-SNAPSHOT` version coordinate | SNAPSHOT artifacts are inherently mutable; they must not be used for release artifacts |

### Bumping an Internal Library Version

When a new version of an internal Opsera library is needed:

1. **Confirm the new version exists** as an immutable artifact in the internal repository (`OPSERA_REPO_URL`).
2. **Open a PR** updating the version coordinate in `build.gradle`.
3. **Refresh the dependency lock file** (once dependency locking is enabled):
   ```bash
   ./gradlew dependencies --write-locks
   ```
4. **Commit the updated lock file** alongside the version bump — this is the verification metadata update.
5. **Run `./gradlew checkNoChangingDependencies`** to confirm no changing declarations were introduced.
6. **Obtain PR approval** before merging — version bumps to internal libraries require review.

**Never use `changing = true` or `-SNAPSHOT` coordinates as a workaround for a missing artifact.** Contact the library owner to publish an immutable release coordinate and mirror it to the internal repository.

### Verifying the Dependency Graph

```bash
# Confirm no changing or SNAPSHOT internal dependencies (runs automatically with check)
./gradlew checkNoChangingDependencies

# Inspect what Gradle resolved for a specific internal library
./gradlew dependencyInsight --dependency java-core-library --configuration runtimeClasspath
./gradlew dependencyInsight --dependency java-jobengine-library --configuration runtimeClasspath
./gradlew dependencyInsight --dependency kubernetes-client --configuration runtimeClasspath
./gradlew dependencyInsight --dependency salesforce-core-lib --configuration runtimeClasspath

# Full dependency report (all configurations)
./gradlew dependencies
```

If a missing internal artifact causes a dependency resolution failure, the build output will identify the missing coordinate and repository name. It will not expose package manager credentials.

---

## Kubernetes Deployment Baseline

Deployment scripts live under `scripts/deploy/`. Each script supports a `DRY_RUN=true` mode that prints planned commands without contacting a Kubernetes cluster.

### Deployment Scripts

| Script | Target environment | Namespace | `latest` allowed |
|---|---|---|---|
| `scripts/deploy/dev-k8.sh` | Development | `sfdc-integrator-dev` | Yes (default) |
| `scripts/deploy/test-k8.sh` | Test / staging | `sfdc-integrator-test` | No — explicit tag required |
| `scripts/deploy/prod-k8.sh` | Production | `sfdc-integrator-prod` | No — immutable tag required |

### Required Environment Variables (all environments)

| Variable | Purpose |
|---|---|
| `IMAGE_TAG` | Container image tag to deploy |
| `DB_URL` | JDBC connection URL for the target datasource |
| `DB_USER` | Datasource username |
| `DB_PASSWORD` | Datasource password — inject from CI/CD secrets, never commit |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka cluster bootstrap address(es) |
| `KUBECONFIG` (or active context) | Access to the target Kubernetes cluster |
| `OPSERA_REPO_URL / USER / TOKEN` | Internal package manager (build only) |

All credential variables must come from your CI/CD secret store. **Never commit real values.**

### Placeholder Fixture Files

Committed fixture files under `scripts/deploy/fixtures/` contain non-functional placeholder values for dry-run validation and CI testing only. They must never contain real credentials, cluster names, or Salesforce credentials.

| Fixture | For |
|---|---|
| `scripts/deploy/fixtures/dev.env.example` | dev-k8.sh dry-run |
| `scripts/deploy/fixtures/test.env.example` | test-k8.sh dry-run |
| `scripts/deploy/fixtures/prod.env.example` | prod-k8.sh dry-run |

### Running Deployments

```bash
# Dev deployment (IMAGE_TAG defaults to 'latest')
IMAGE_TAG=1.2.3 \
  DB_URL=jdbc:postgresql://<host>:5432/sfdc_integrator_dev \
  DB_USER=<user> DB_PASSWORD=<secret> \
  KAFKA_BOOTSTRAP_SERVERS=<host>:9092 \
  ./scripts/deploy/dev-k8.sh

# Test deployment (explicit tag required)
IMAGE_TAG=1.2.3 \
  DB_URL=... DB_USER=... DB_PASSWORD=... KAFKA_BOOTSTRAP_SERVERS=... \
  ./scripts/deploy/test-k8.sh

# Production deployment (immutable tag required; 'latest' rejected)
IMAGE_TAG=1.2.3 \
  DB_URL=... DB_USER=... DB_PASSWORD=... KAFKA_BOOTSTRAP_SERVERS=... \
  ./scripts/deploy/prod-k8.sh
```

### Dry-Run Mode

Validate scripts locally without contacting a cluster:

```bash
# Single script dry-run
DRY_RUN=true IMAGE_TAG=1.2.3 DB_URL=... DB_USER=... DB_PASSWORD=... \
  KAFKA_BOOTSTRAP_SERVERS=... ./scripts/deploy/dev-k8.sh

# Full validation suite (all scripts, syntax + dry-run using fixture values)
./scripts/validate-deploy-scripts.sh

# Same via Gradle (wired into the check lifecycle)
./gradlew validateDeployScripts
```

### Kubernetes Spring Profile

Activate the `kubernetes` Spring profile when running in cluster:

```yaml
# In your Kubernetes Deployment spec
env:
- name: SPRING_PROFILES_ACTIVE
  value: kubernetes
```

The `application-kubernetes.yaml` profile configures:
- Graceful shutdown (`server.shutdown: graceful`, 60s timeout)
- Kafka bootstrap server from `KAFKA_BOOTSTRAP_SERVERS`
- Kubernetes health probes (`/actuator/health/liveness`, `/actuator/health/readiness`)
- Flyway, metrics, and info actuator endpoints exposed

### Rollback

```bash
kubectl rollout undo deployment/sfdc-integrator-service -n <namespace>
kubectl rollout status deployment/sfdc-integrator-service -n <namespace>
```

### Monitoring Deployment

```bash
kubectl rollout status deployment/sfdc-integrator-service -n <namespace>
kubectl logs -l app=sfdc-integrator-service -n <namespace> --tail=100
kubectl get pods -l app=sfdc-integrator-service -n <namespace>
```

### Deployment Safety Rules

- Production deployments require an approved change ticket.
- `IMAGE_TAG=latest` is rejected for test and production environments.
- All credential variables must be absent from pod logs (`DB_PASSWORD`, `OPSERA_REPO_TOKEN`).
- Dry-run must pass in CI before a live deployment is triggered.

---

## Database Migrations (Flyway)

This service uses [Flyway](https://flywaydb.org/) for repeatable, version-controlled PostgreSQL schema migrations. Migrations run automatically at application startup before the web layer becomes ready.

### Required Environment Variables (Production / Staging)

| Variable | Purpose |
|---|---|
| `DB_URL` | JDBC connection URL — e.g. `jdbc:postgresql://<host>:5432/<dbname>` |
| `DB_USER` | Database username |
| `DB_PASSWORD` | Database password |

**Never commit real database URLs, usernames, or passwords to source control.**

If any of these variables are missing at startup, the application fails immediately with a clear configuration error identifying the missing property — no silent fallback to an embedded database.

### Migration Script Location

```
src/main/resources/db/migration/
  V1__baseline.sql     ← initial schema foundation (committed, immutable)
```

Migration scripts follow Flyway's versioned naming convention: `V{version}__{description}.sql`.

### Running Migrations Locally

To run migrations against a local PostgreSQL instance:

```bash
# Set connection environment variables
export DB_URL="jdbc:postgresql://localhost:5432/sfdc_integrator"
export DB_USER="postgres"
export DB_PASSWORD="<local-password>"

# Start the application — Flyway runs migrations automatically on startup
./gradlew bootRun
```

To validate migration status without starting the full application, use the Flyway CLI or the Spring Boot Actuator `/actuator/flyway` endpoint (requires `management.endpoints.web.exposure.include=flyway`).

### Validating Migration Status in CI

The `MigrationFoundationTest` integration test verifies that:
1. The Spring application context starts with Flyway enabled.
2. The V1 baseline migration creates the `sfdc_schema_info` table.
3. Flyway's `flyway_schema_history` records V1 as applied and successful.

```bash
# Run only migration integration tests
./gradlew test --tests 'com.opsera.integrator.sfdc.migration.*'

# Run all tests (migration tests included)
./gradlew test
```

Migration tests use an H2 in-memory datasource in PostgreSQL compatibility mode — no external database is required in CI.

### Immutability Rule

**Migration scripts are immutable once applied to any environment.** If a migration was already applied, changing its content will cause Flyway to detect a checksum mismatch and fail at startup — this is intentional and prevents silent schema drift.

To fix a mistake in an applied migration:
1. Do NOT edit the existing script.
2. Create a new versioned migration (e.g. `V2__fix_<description>.sql`) that makes the corrective change.

### Rollback Policy

Flyway does not support automatic rollback of applied migrations. To roll back:

1. **Restore from backup** — the approved rollback path is restoring the database from a point-in-time backup taken before the migration was applied.
2. **Write a corrective migration** — for non-destructive changes, write a forward migration that undoes the effect (e.g. `DROP TABLE IF EXISTS`).
3. **Never reuse a version number** — version numbers are permanent once applied.

### Migration Failure Modes

| Failure | Cause | Resolution |
|---|---|---|
| `Could not resolve placeholder 'DB_URL'` | `DB_URL` env var not set | Set the required environment variables before starting |
| `Checksum mismatch for migration V1` | A committed migration script was edited after being applied | Restore the original script content; write a new migration for the fix |
| `Flyway has detected resolved migrations that are still pending` | New migration added without running it | Deploy the new version so Flyway applies it |
| `Unable to obtain connection from datastore` | PostgreSQL unreachable or credentials wrong | Check network policy, `DB_URL`, `DB_USER`, `DB_PASSWORD` |

---

## Operational SLOs and Guardrails

Release workflow reliability targets are defined in two committed files:

| File | Purpose |
|---|---|
| [`docs/operations/release-workflow-slos.md`](docs/operations/release-workflow-slos.md) | Human-readable SLO policy: SLI catalog, targets, escalation guidance, and implementation status |
| [`ops/slo/release-workflow-guardrails.yaml`](ops/slo/release-workflow-guardrails.yaml) | Machine-readable thresholds for dashboard panels, alert rules, and rollout gate conditions |

**These files are the single source of truth for SLO thresholds.** Do not retype threshold values in dashboards or alert configurations — import or reference `ops/slo/release-workflow-guardrails.yaml` directly.

Covered workflows: Quick Deploy (`POST /quickdeploy`), Deployment (`POST /deploy`), Validation (`POST /validate`), and Status Tracking (route pending).

Key guardrail thresholds:

| Guardrail | Threshold | Status |
|---|---|---|
| p95 API acceptance latency | < 2 000 ms | Active (metrics exporter pending) |
| p95 status lookup latency | < 1 000 ms | Pending (status route not yet implemented) |
| Active-job checkpoint freshness | ≤ 60 s staleness | Pending (Kafka checkpoints not yet implemented) |
| Server-side error rate | < 1% | Active (metrics exporter pending) |
| Legacy compatibility | ≥ 99.9% | Active (coexistence period) |
| Production RPO | ≤ 15 min | Pending (PITR backup not yet confirmed) |
| Production RTO | ≤ 4 hours | Pending (recovery runbook not yet timed) |

Baselines marked _pending_ have not yet been established by production telemetry and must not be assumed. See `docs/operations/release-workflow-slos.md` §6 for the implementation gap table.

---

## Test Fixture Masking Convention

All compliance-sensitive test fixtures must use **synthetic, deterministic values** generated
by `SyntheticFixtureFactory` (in `src/test/java/...sfdc/fixtures/`). Real customer names,
Salesforce org identifiers, usernames, emails, tokens, connection strings, or production URLs
must never appear in committed test resources.

### Allowed Domains and Prefixes

| Field type | Safe pattern | Example |
|---|---|---|
| Customer ID hash | 64-char lowercase hex | `0000…0000` or `sha256(salt:customer:seed)` |
| Pipeline / step / task ID | `<type>-fixture-<12hex>` | `pipeline-fixture-b2c3d4e5f6a1` |
| Correlation ID | `cid-fixture-<12hex>` | `cid-fixture-a1b2c3d4e5f6` |
| Deployment request ID | `deploy-req-fixture-<12hex>` | `deploy-req-fixture-c3d4e5f6a1b2` |
| Job ID | `job-fixture-<12hex>` | `job-fixture-d4e5f6a1b2c3` |
| Email address | `user-<hash>@fixture.example.internal` | `user-abc123@fixture.example.internal` |
| Source / target org URL | `https://source-<hash>.org.fixture.example.internal` | See factory |
| API token placeholder | `tok-fixture-<24hex>` (no dots) | `tok-fixture-a1b2c3d4e5f6c7d8e9f0a1b2` |
| Salesforce org ref | `00Dfixture<UPPERCASE-HASH>` | `00DfixtureA1B2C3D4E5F6` |
| Report sample ref | `report-fixture-<12hex>` | `report-fixture-e5f6a1b2c3d4` |
| Username | `fixture-user-<12hex>` | `fixture-user-c3d4e5f6a1b2` |

### Safe Fixture JSON Files

Sanitized JSON fixture files for common scenarios are committed under
`src/test/resources/fixtures/pii-masking/`:

| File | Scenario |
|---|---|
| `masked-quick-deploy-request.json` | Quick deploy pipeline step |
| `masked-data-migration-request.json` | Data migration with source/target org URLs |
| `masked-post-refresh-request.json` | Post-refresh task |
| `masked-audit-event.json` | Audit event with actor ref and org ref |
| `masked-retention-metadata.json` | Retention metadata with customer ID hash |
| `masked-purge-scenario.json` | Purge scenario with retention category |

### Controller Log Field Allow-List

Controllers must only include the following fields in `SafeLogEvent.safeFields`:

| Controller | Allowed safe fields |
|---|---|
| `DataMigrationController` | `pipelineId`, `stepId` |
| `PostRefreshTaskController` | `pipelineId`, `stepId` |
| `JobExecutionController` | `pipelineId`, `stepId` |

Org URLs (`sourceOrgUrl`, `targetOrgUrl`), raw DTO toString, customer hashes, and bearer
tokens must never appear in logged safe fields.

### Fixture Safety Scan

`FixtureSafetyScanTest` runs automatically as part of `./gradlew test` and fails if any
file under `src/test/resources/fixtures/`, `src/test/resources/contracts/`, or `README.md`
contains:

- A JWT signature pattern (`eyJ…`)
- A PEM private key header (`BEGIN PRIVATE KEY`)
- A Salesforce production domain (`.salesforce.com`, `.force.com`)
- A bearer token with a 40+ character value
- A real email address (non-`.internal`, non-`.example.*` domain)

Failure output names the **file path and pattern category only** — matched values are never
echoed to prevent secrets appearing in CI logs.

### Generating Fixture Values in Tests

```java
import com.opsera.integrator.sfdc.fixtures.SyntheticFixtureFactory;

// Deterministic values — same seed always produces the same output
String pipelineId = SyntheticFixtureFactory.pipelineId("my-test-scenario");
String stepId     = SyntheticFixtureFactory.stepId("my-test-scenario");
String sourceUrl  = SyntheticFixtureFactory.sourceOrgUrl("my-test-scenario");
String email      = SyntheticFixtureFactory.email("user-seed");
String token      = SyntheticFixtureFactory.token("service-seed");
```

Null and blank seeds return clearly synthetic placeholders (never null). The factory uses
a fixed, non-secret test salt (`sfdc-test-fixture-v1`) that must not be used in any
production hashing or masking operation.

---

## Java 21 Toolchain Troubleshooting

| Symptom | Cause | Resolution |
|---|---|---|
| `Could not resolve toolchain` | Java 21 JDK not found and auto-provisioning disabled | Install Java 21 or set `org.gradle.java.installations.auto-download=true` in `gradle.properties` |
| `JAVA_HOME points to wrong version` | Host JAVA_HOME is not Java 21 | Set `JAVA_HOME` to a Java 21 installation or use the wrapper's toolchain auto-provisioning |
| `sourceCompatibility mismatch` | `checkJavaToolchain` fails after editing `build.gradle` | Keep `sourceCompatibility = JavaVersion.VERSION_21` and `targetCompatibility = JavaVersion.VERSION_21` in the `java {}` block |
| Build passes but tests fail on Java version | Container image uses wrong JRE | Update Dockerfile/Helm values to use `eclipse-temurin:21-jre` |
| `GradleException: Java toolchain baseline violation` | `sourceCompatibility` or `targetCompatibility` were changed | Restore both to `JavaVersion.VERSION_21` in `build.gradle` |

To force Gradle to print which JDK it resolved for the build:
```bash
./gradlew compileJava --info 2>&1 | grep -i "toolchain\|jvm\|java home"
```

---

## Forge Shipping Release Pipeline

The release pipeline is defined in `.forge/pipeline.yml`. It enforces all supply-chain
controls before any artifact reaches a Kubernetes cluster.

### Pipeline Stage Summary

| Stage | Blocks on failure | Key checks |
|---|---|---|
| **build** | All downstream stages | Java 21 toolchain, `./gradlew clean check bootJar`, dependency locking, dependency verification, NPM guard, JCenter guard |
| **scan** | All downstream stages | Secret detection (gitleaks), SAST (semgrep), SCA/dependency scan, SBOM generation |
| **image-scan** | All downstream stages | Container image CVE scan (trivy) — skipped if no Dockerfile present |
| **gate** | All downstream stages | No critical/high secrets, no SAST criticals, no CVSS ≥ 7.0 SCA findings, SBOM present, no NPM artifacts |
| **provenance** | All downstream stages | JAR signing (cosign), SLSA provenance generation, provenance completeness validation |
| **push** | All downstream stages | Immutable release tag push (no `latest` for non-dev) |
| **deploy-dev** | Notified, not blocked | Dev cluster deployment via `scripts/deploy/dev-k8.sh` |
| **smoke-test** | Staging promotion | Spring Boot smoke profile test — no live Salesforce or infra required |
| **gate-staging** | Test deployment | Smoke test pass, artifact signed, provenance attached |
| **deploy-test** | All downstream stages | Test cluster deployment via `scripts/deploy/test-k8.sh` |
| **smoke-test-staging** | Production gate | Staging smoke test pass |
| **gate-prod** | Production deployment | Human approval: 1× `@opsera/release-approvers` + 1× `@opsera/security-review`, change ticket required, builder cannot be sole approver |
| **deploy-prod** | N/A (final stage) | Production deployment via `scripts/deploy/prod-k8.sh` with rollout health check |

### Running the Pipeline Locally (Dry-run)

```bash
# Requires Forge CLI installed and authenticated to the Forge platform.
# Does NOT require production cluster access, registry credentials, or signing keys.
forge pipeline run --dry-run --file .forge/pipeline.yml
```

To validate only the build and scan stages without Forge CLI:
```bash
./gradlew clean check bootJar --no-daemon   # build + all check tasks
./gradlew checkNoNpmArtifacts               # NPM guard
./gradlew checkDependencyLockConfiguration  # lock file check
./gradlew checkVerificationMetadata         # verification metadata check
```

### Scan Suppression Policy

Scan suppressions live in `.forge/scan-suppressions.yml`. Every suppression **must** include:

| Field | Requirement |
|---|---|
| `id` | Unique identifier — never reuse |
| `rule_id` | Exact scanner rule ID, CVE, or CWE |
| `owner` | GitHub team or user accountable for this exception |
| `justification` | Specific, verifiable reason (not "false positive") |
| `expiry` | ISO 8601 date — maximum 90 days from creation |

Expired suppressions cause the gate stage to fail until renewed or removed.
**Never suppress critical secret findings or hardcoded credential rules.**

---

## Release Pipeline Runbook

### Failed Gate Triage

When a gate stage fails, the pipeline stops immediately and emits the failed gate ID,
affected artifact tag, and scan report URLs to the Forge release log. No secret values
are included in diagnostic output.

**Step-by-step triage:**

1. **Identify the gate** — read the `gate-*` failure message from the Forge release log.
   Each gate check has a unique ID (e.g. `gate-no-critical-secrets`, `gate-sca-cvss`).

2. **Retrieve the scan report** — scan reports are attached to the release record:
   - Secret scan: `reports/` (gitleaks output, redacted)
   - SAST: `reports/sast-results.json`
   - SCA: `reports/dependency-check-report.json`
   - SBOM: `reports/sbom.json`
   - Image scan: `reports/image-scan.json` (if image build exists)

3. **Determine root cause** — distinguish between a real finding and a confirmed
   false positive. **Do not assume false positive without verification.**

4. **Remediate the finding** — fix the code, dependency, or configuration.
   If a suppression is truly required, add a time-bounded entry to
   `.forge/scan-suppressions.yml` with a specific justification, expiry date,
   and accountable owner. Suppression review requires `@opsera/security-review` sign-off.

5. **Re-trigger the pipeline** — commit the fix (or suppression entry), push,
   and re-run from the failed stage.

### Rollback to Last Known-Good Artifact

When a post-push deployment fails, the pipeline stops and notifies the
`forge-release-alerts` channel. To redeploy the last signed, gate-passing artifact:

```bash
# 1. Identify the last known-good release tag from the Forge release log or registry.
#    Example: 1.2.3-release

# 2. Redeploy to the affected environment using the PREVIOUS tag.
#    Never skip gates or promote an unsigned artifact.
IMAGE_TAG=<previous-good-tag> ./scripts/deploy/prod-k8.sh   # production
IMAGE_TAG=<previous-good-tag> ./scripts/deploy/test-k8.sh   # staging
IMAGE_TAG=<previous-good-tag> ./scripts/deploy/dev-k8.sh    # dev

# 3. Verify rollout health.
kubectl rollout status deployment/sfdc-integrator-service -n sfdc-integrator-prod

# 4. Open a post-incident review and update the failed gate remediation plan.
```

**Do not bypass gates to speed up a rollback.** The previous signed artifact already
passed all gates — it is safe to redeploy immediately without re-scanning.

### Provenance Lookup

Every release artifact has a signed provenance record attached to its Forge release entry.

```bash
# List provenance for a specific release tag (requires Forge CLI).
forge artifact provenance --tag <release-tag> --service sfdc-integrator-service

# Verify artifact signature using cosign.
cosign verify-blob \
  --key "$SIGNING_KEY_REF" \
  --signature "sfdc-integrator-service-<version>.jar.sig" \
  "sfdc-integrator-service-<version>.jar"
```

The provenance record includes:
- Source repository URI and Git commit SHA
- Build timestamp and Forge pipeline run ID
- Artifact digest (SHA-256)
- SBOM attachment reference
- Gate pass/fail summary (no secret values)

### Scan Exception Review

Scan exceptions (suppressions) expire automatically. Expired entries cause the gate to fail.

**Review process for new or renewed suppressions:**

1. The requester adds a draft entry to `.forge/scan-suppressions.yml`.
2. The PR requires review from `@opsera/security-review` (enforced by `.github/CODEOWNERS`).
3. The reviewer verifies the justification is specific and the expiry is ≤ 90 days.
4. The reviewer approves the PR. The entry becomes active when merged.
5. The requester sets a calendar reminder before the expiry date to renew or remove.

### Production Approval Process

Production deployments require:

1. **Change ticket** — open a change request in the organization's change management system
   before triggering the `gate-prod` approval step.
2. **Release approver sign-off** — at least one member of `@opsera/release-approvers`
   must approve the Forge pipeline gate.
3. **Security reviewer sign-off** — at least one member of `@opsera/security-review`
   must approve the Forge pipeline gate.
4. **Separation of duty** — the person who triggered the build cannot be the sole approver.
5. **Approval window** — approvals expire after 72 hours. The pipeline must be triggered
   and approved within this window or a new build is required.

### Escalation Ownership

| Scenario | Primary owner | Escalation path |
|---|---|---|
| Failed build or unit test | PR author | `@opsera/sfdc-integrator-maintainers` |
| Failed secret scan | PR author + `@opsera/security-review` | Security lead |
| Failed SCA finding (dependency CVE) | `@opsera/sfdc-integrator-maintainers` | `@opsera/security-review` |
| Failed image scan | `@opsera/sfdc-integrator-maintainers` | `@opsera/security-review` |
| Unsigned artifact / missing provenance | `@opsera/release-approvers` | Platform engineering |
| Production deployment failure | On-call SRE (`@opsera/sre-release-ops`) | Release approver |
| Expired suppression blocking release | Suppression `owner` field | `@opsera/security-review` |

---

## Async Command Dispatch

The service provides a durable async command dispatch layer in the `command` package that ensures
release jobs are recorded before execution begins.

### Dispatcher Acceptance Guarantees

1. **Durable before dispatch**: `DefaultCommandDispatcher` persists a `JobRecord` in `ACCEPTED`
   state before invoking any execution adapter. If persistence fails, the adapter is never called.
2. **Non-blocking handoff**: Adapters are expected to enqueue or delegate work without waiting for
   Salesforce CLI or job-engine completion. The dispatcher returns an `AcceptedCommandOutcome`
   immediately after the job is created and the adapter handoff is initiated.
3. **Idempotency**: If an `AsyncCommandRequest` carries an `idempotencyKey` that matches an
   existing job's correlation ID, the original `AcceptedCommandOutcome` is returned without
   creating a duplicate record.
4. **Failure semantics**: If the adapter handoff fails after the job is persisted, the lifecycle
   service marks the job as `FAILED` with a safe reason code before the exception is propagated.

### Timeout Expectations

- Dispatcher `dispatch()` should complete within 500 ms under normal conditions (job persistence
  + adapter submission).
- Adapters must not perform synchronous Salesforce CLI, Git, or job-engine operations — these
  belong in worker pods or async processors.

### Handoff Failure Runbook

| Symptom | Likely cause | Resolution |
|---|---|---|
| `CommandDispatchException` with reason `ADAPTER_HANDOFF_FAILURE` | Executor rejected task or service unavailable | Check adapter health; retry is safe (job in FAILED state, new request creates new job) |
| `LifecyclePersistenceException` on dispatch | Database connectivity failure | Check DB_URL env var and connection pool; no adapter was called |
| Duplicate `jobId` in logs | Idempotency key not supplied; parallel submissions | Supply idempotency key in repeat requests |

### Legacy Coexistence

The `DefaultCommandDispatcher` is a separate Spring `@Service` — the legacy
`JobExecutionController` continues to call `QuickDeployService.start()` directly and is
unaffected. Modernized callers or new routes may use the dispatcher to receive a full
`AcceptedCommandOutcome` with job tracking. Both paths coexist without interference.

---

## Worker Image (WO-134)

The `sfdc-integrator-worker` container is a **dedicated release-execution runtime** separate from the API pods. It packages the Salesforce CLI, Git, Java 21 JRE, and embedded release scripts. API pods remain lightweight and unprivileged; workers carry the heavy CLI toolchain.

### Image Definition

| Asset | Path |
|---|---|
| Container definition | `Dockerfile.worker` |
| Entrypoint script | `scripts/worker/entrypoint.sh` |
| Release scripts | `src/main/resources/scripts/*.sh` |
| Self-test fixtures | `src/test/resources/fixtures/worker/` |
| Image validation | `scripts/worker/validate-worker-image.sh` |

### Command Modes

| Mode | Usage | External calls |
|---|---|---|
| `self-test` | `entrypoint.sh self-test` | None — verifies tool versions and script paths only |
| `dry-run` | `COMMAND_TYPE=QUICK_DEPLOY entrypoint.sh dry-run` | None — validates env config and prints execution plan |
| `execute <TYPE>` | `COMMAND_TYPE=QUICK_DEPLOY entrypoint.sh execute QUICK_DEPLOY` | Calls Salesforce CLI — requires all secrets to be mounted |

Supported command types for `execute`: `QUICK_DEPLOY`, `DEPLOY`, `VALIDATE`, `ROLLBACK`, `PACKAGE`, `DIAGNOSTIC`.

### Required Runtime Environment Variables

All sensitive values must be **mounted as Kubernetes Secret references** at execution time. They must never be baked into the image.

| Variable | Classification | Purpose |
|---|---|---|
| `SFDC_INSTANCE_URL` | RESTRICTED | Salesforce org instance URL |
| `SFDC_CLIENT_ID` | RESTRICTED | Connected App client ID |
| `SFDC_CLIENT_SECRET` | RESTRICTED | Connected App client secret |
| `SFDC_USERNAME` | CONFIDENTIAL | Salesforce username |
| `CORRELATION_ID` | NON_SECRET | Tracing correlation ID from the API request |
| `COMMAND_TYPE` | NON_SECRET | Release command type (QUICK_DEPLOY, DEPLOY, etc.) |
| `JOB_ID` | NON_SECRET | Durable job identifier from the lifecycle service |
| `DEPLOYMENT_REQUEST_ID` | NON_SECRET | Salesforce deployment request ID (quick-deploy and rollback only) |

### Build and Smoke-Test

```bash
# CI-safe validation (no Docker required)
./gradlew validateWorkerImage

# Build the image
docker build -f Dockerfile.worker -t sfdc-worker:dev .

# Run self-test (exits 0, no external calls)
docker run --rm sfdc-worker:dev self-test

# Run dry-run for QUICK_DEPLOY
docker run --rm -e COMMAND_TYPE=QUICK_DEPLOY sfdc-worker:dev dry-run
```

### Security Invariants

- Worker container runs as `sfdc-worker` (uid 1001) — never root.
- No credentials are baked into the image — all injected at runtime via mounted references.
- Self-test and dry-run modes make no network calls to Salesforce or Kubernetes.
- Entrypoint never prints variable values — only variable names in diagnostic output.
- All log output is prefixed with `[sfdc-worker] correlationId=... jobId=...` for traceability.

### Troubleshooting

| Symptom | Likely cause | Resolution |
|---|---|---|
| `missing-required-secret: 'SFDC_CLIENT_SECRET'` | Secret not mounted | Mount the Kubernetes Secret as env var before executing |
| `missing-script: '/opt/sfdc-worker/scripts/quick-deploy.sh'` | Image not built with scripts | Rebuild image from `Dockerfile.worker` with full build context |
| `unsupported-mode: 'foo'` | Wrong command argument | Use `self-test`, `dry-run`, or `execute <TYPE>` |
| `unsupported-command-type: 'FOO'` | Unknown command type | Check supported types: QUICK_DEPLOY, DEPLOY, VALIDATE, ROLLBACK, PACKAGE, DIAGNOSTIC |
| Self-test fails with `sf-cli-not-responding` | Node.js or SF CLI install failed | Rebuild image; check base image Node version compatibility |

---

## V2 Release API Canary Fallback Runbook

The v2 release API (`POST /api/v2/sfdc/release-jobs`) supports configuration-driven canary
controls. Operators can disable v2 submission globally or per operation type without a code
change or redeployment. **Legacy routes (`/quickdeploy`, `/deploy`, `/validate`) are never
affected by these flags.**

### Configuration Reference

Flags live under `sfdc.v2.release.routes.*` in `application.yaml` or in an overlay:

```yaml
sfdc:
  v2:
    release:
      routes:
        # Master kill switch — set to false to disable ALL v2 submission.
        enabled: true
        # Per-operation allow-list. Default-deny: unknown/unconfigured types are blocked
        # even when enabled=true.
        operations:
          QUICK_DEPLOY: true
          DEPLOY: true
          VALIDATE: true
          CANCEL: false
        # When true, GET status lookups remain available during a submission disable window.
        read-only-status-fallback: true
```

When a request is blocked, the controller returns:
- **HTTP 422 Unprocessable Entity** (not 404 or 503)
- JSON body with `errorCode`, `correlationId`, `message`, and `remediation` fields
- `errorCode: V2_ROUTES_DISABLED` — global kill switch triggered
- `errorCode: V2_OPERATION_DISABLED` — per-operation flag blocked the request
- Telemetry outcome `disabledRoute` emitted via `sfdc.release.api.requests{outcome=disabledRoute}`

### Disabling All V2 Submission (Global Rollback)

1. Locate your environment's configuration override (Kubernetes ConfigMap, Vault, or `application-<env>.yaml`).
2. Set `sfdc.v2.release.routes.enabled=false`.
3. Restart the service (or trigger live-reload if supported):
   ```bash
   kubectl rollout restart deployment/sfdc-integrator-service -n <namespace>
   kubectl rollout status deployment/sfdc-integrator-service -n <namespace>
   ```
4. Verify v2 requests return HTTP 422 (not 404):
   ```bash
   curl -s -o /dev/null -w "%{http_code}" \
     -X POST https://<host>/api/v2/sfdc/release-jobs \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer <token>" \
     -d '{"operationType":"QUICK_DEPLOY",...}'
   # Expected: 422
   ```
5. Verify legacy routes still return HTTP 200 / 202:
   ```bash
   curl -s -o /dev/null -w "%{http_code}" \
     -X POST https://<host>/quickdeploy \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer <token>" \
     -d '{...}'
   # Expected: 200
   ```
6. Confirm no new v2 jobs are accepted by checking the telemetry counter:
   `sfdc.release.api.requests{routeVersion=v2,outcome=disabledRoute}` — count should increase.
   `sfdc.release.api.requests{routeVersion=v2,outcome=accepted}` — count should stay flat.

### Disabling a Single Operation Type

To disable only one operation (e.g. DEPLOY) while keeping QUICK_DEPLOY and VALIDATE active:

1. Set `sfdc.v2.release.routes.operations.DEPLOY=false` in the configuration overlay.
2. Restart or live-reload.
3. Verify DEPLOY returns HTTP 422 with `errorCode: V2_OPERATION_DISABLED`.
4. Verify QUICK_DEPLOY and VALIDATE still return HTTP 202.

### Re-enabling V2 After Rollback

1. Restore `sfdc.v2.release.routes.enabled=true` and the desired operations flags.
2. Restart or live-reload.
3. Verify v2 requests return HTTP 202 again.
4. Confirm the `sfdc.release.api.requests{routeVersion=v2,outcome=accepted}` counter resumes climbing.

### Verifying Legacy Route Health During Fallback

Legacy routes are independent of v2 flags. To confirm they are serving traffic:

```bash
# Quick deploy (legacy)
curl -s -w "\nHTTP %{http_code}\n" -X POST https://<host>/quickdeploy \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{...}'

# Deploy (legacy)
curl -s -w "\nHTTP %{http_code}\n" -X POST https://<host>/deploy \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{...}'
```

Both should return HTTP 200. If they do not, the issue is unrelated to v2 route flags.

### Testing the Fallback Locally

Run the MockMvc test suite to validate enabled/disabled behavior without a running service:

```bash
cd sfdc-integrator-service/sfdc-integrator-service

# All controller canary tests
./gradlew test --tests 'com.opsera.integrator.sfdc.controller.v2.ReleaseJobControllerCanaryTest' --no-daemon

# Disabled-route structured response tests
./gradlew test --tests 'com.opsera.integrator.sfdc.controller.v2.ReleaseJobControllerDisabledRouteTest' --no-daemon

# Properties unit tests (allow-list, default-deny, global-disable precedence)
./gradlew test --tests 'com.opsera.integrator.sfdc.config.V2ReleaseRoutesPropertiesTest' --no-daemon
```

### Fixture Configuration Snippets

Reference YAML snippets for common scenarios are committed under
`src/test/resources/fixtures/config/`:

| File | Scenario |
|---|---|
| `v2-routes-disabled.yaml` | Global v2 disable — all submission returns 422 |
| `v2-routes-quick-deploy-only.yaml` | Narrow canary — only QUICK_DEPLOY enabled |
| `v2-routes-all-p0-enabled.yaml` | All P0 operations enabled (QUICK_DEPLOY, DEPLOY, VALIDATE) |
| `v2-routes-unknown-op-denied.yaml` | Default-deny example — only explicitly listed types pass |

### Escalation

| Symptom | Action |
|---|---|
| v2 returns 422 unexpectedly in production | Check `sfdc.v2.release.routes.enabled` in the active ConfigMap. Re-enable if unintended. |
| Legacy route returns 5xx during v2 disable | V2 flags do not affect legacy routes — investigate independently via `kubectl logs`. |
| `sfdc.release.api.requests{outcome=disabledRoute}` is not emitted | Check `ReleaseCoexistenceTelemetry` is wired and `MeterRegistry` is available. |
| HTTP 404 (not 422) on v2 submission | Indicates a stale deployment — verify the version with `V2ReleaseRoutesProperties` is running. |

## Stalled Job Timeout Monitor Runbook

### Overview

The `JobTimeoutMonitor` is a scheduled Spring component that detects active jobs with no state
progress beyond the configured stale-progress threshold and finalizes them as `TIMED_OUT`.
It runs on a fixed-delay schedule and is safe to execute concurrently across multiple replicas
through optimistic concurrency in the lifecycle service.

### Configuration Reference

| Property | Default | Description |
|---|---|---|
| `sfdc.lifecycle.timeout.enabled` | `true` | Master kill switch. Set `false` during emergency maintenance. |
| `sfdc.lifecycle.timeout.scan-interval-seconds` | `300` | Seconds between scans (fixed delay). |
| `sfdc.lifecycle.timeout.stale-threshold-minutes` | `240` | Minutes without state change before a job is stale. |
| `sfdc.lifecycle.timeout.accepted-grace-period-minutes` | `30` | Grace period for ACCEPTED jobs awaiting dispatch. |
| `sfdc.lifecycle.timeout.max-jobs-per-scan` | `100` | Max jobs finalized per scan. Caps blast radius. |

### Disabling the Monitor (Emergency Maintenance)

1. Identify the active ConfigMap or environment override for the target pod:
   ```
   kubectl get configmap sfdc-integrator-config -n <namespace> -o yaml
   ```
2. Patch the `enabled` flag:
   ```
   kubectl patch configmap sfdc-integrator-config -n <namespace> \
     --type merge -p '{"data":{"SFDC_LIFECYCLE_TIMEOUT_ENABLED":"false"}}'
   ```
3. Restart the pod to pick up the change (or use a live-reload configuration source):
   ```
   kubectl rollout restart deployment/sfdc-integrator-service -n <namespace>
   ```
4. Verify the monitor is no longer scanning by checking application logs for:
   `timeout-monitor disabled — skipping scan`
5. Re-enable by reversing the patch and rolling out again.

### Investigating a Timed-Out Job

1. Look up the job record by jobId or correlationId in the `jobs` table.
2. Read the terminal diagnostic from `job_diagnostics` — the `safe_summary` column contains
   `operationType`, `elapsedSeconds`, `lastCheckpointCode`, `failingStage`, and `retryEligibility`.
3. Inspect checkpoint history in `job_checkpoints` ordered by `sequence_number` for the last
   active stage before the timeout.
4. Check audit events in `audit_events` with `operation = 'JOB_TIMEOUT_FINALIZED'` for the
   finalizing actor and timestamp.

### Metrics

| Metric | Tags | Description |
|---|---|---|
| `sfdc.lifecycle.timeout.scan.total` | `result=ok` | Incremented on each completed scan. |
| `sfdc.lifecycle.timeout.finalized` | `operationType` | Incremented per job finalized as TIMED_OUT. |
| `sfdc.lifecycle.timeout.skipped` | `reason` | Incremented per skipped job (grace_period, concurrency_conflict, already_terminal). |
| `sfdc.lifecycle.timeout.errors` | `reason` | Incremented on per-job errors or query failures. |

### Fixture Reference

Test fixtures for the timeout monitor are committed under `src/test/resources/fixtures/lifecycle/`:

| File | Scenario |
|---|---|
| `stale-running-job.json` | RUNNING job updated >240 min ago — timeout candidate |
| `fresh-running-job.json` | RUNNING job updated recently — must not be timed out |
| `stale-accepted-outside-grace.json` | ACCEPTED job >30 min old with no checkpoints — timeout candidate |
| `stale-accepted-within-grace.json` | ACCEPTED job within grace period — protected |
| `completed-job.json` | COMPLETED terminal job — never touched by monitor |
| `failed-job-with-checkpoints.json` | FAILED terminal job with checkpoint history |
| `checkpoint-less-accepted-job.json` | ACCEPTED job recently created, no checkpoints — grace applies |

### Escalation

| Symptom | Action |
|---|---|
| Jobs stuck in RUNNING/DISPATCHING for >6 hours | Check `sfdc.lifecycle.timeout.enabled=true` and `stale-threshold-minutes` config. |
| Monitor scan count stagnant | Check `sfdc.lifecycle.timeout.scan.total` metric and `sfdc.lifecycle.timeout.errors`. |
| Healthy long-running jobs being timed out | Increase `stale-threshold-minutes`. Default 240 covers 4-hour Salesforce deployments. |
| Monitor running on only one replica but you have multiple pods | Expected — optimistic concurrency ensures concurrent scans are safe. |
| `TIMED_OUT` jobs showing wrong diagnostic fields | Review `job_diagnostics.safe_summary` — never contains credentials or stack traces. |

---

## P0 Release Workflow Test Harness

The P0 harness is an automated CI release gate that exercises the complete v2 release workflow
matrix without live Salesforce orgs, Kafka brokers, PostgreSQL clusters, Git providers, or
Kubernetes clusters. All external collaborators are mocked. It is safe to run in any environment
that can compile the project.

### What the harness covers

| Test class | Scope |
|---|---|
| `P0ReleaseWorkflowHarnessTest` | V2 quick deploy, deploy, validate acceptance; cancellation (active, terminal, unknown); status lookup with rollback decision fields; routes-disabled enforcement; correlation ID propagation |
| `P0LegacyCompatHarnessTest` | Legacy `/quickdeploy` (start, missing ID, stop) — confirms legacy route response shapes are not normalized to v2 contracts during coexistence |

### What is mocked

| External system | Mock strategy |
|---|---|
| Salesforce CLI | `@MockBean` on service/facade layer — no shell execution |
| Kafka (dispatch) | `@MockBean` on `ReleaseCommandFacade` — no broker connection |
| PostgreSQL / H2 | `@MockBean` on `JobRepository` / `JobStatusAdapter` — no database |
| Kubernetes | Not wired into controller layer — no k8s client needed |
| Git provider | Not wired into controller layer — no Git credentials needed |

### Running locally

Run only the P0 harness:

```bash
./gradlew test --tests 'com.opsera.integrator.sfdc.harness.*'
```

Run the v2 workflow harness only:

```bash
./gradlew test --tests 'com.opsera.integrator.sfdc.harness.P0ReleaseWorkflowHarnessTest'
```

Run the legacy compat harness only:

```bash
./gradlew test --tests 'com.opsera.integrator.sfdc.harness.P0LegacyCompatHarnessTest'
```

Run the full test suite (includes the P0 harness and all other tests):

```bash
./gradlew test
```

### CI release gate usage

Add the harness to your CI release gate step:

```yaml
- name: P0 workflow gate
  run: ./gradlew test --tests 'com.opsera.integrator.sfdc.harness.*' --continue
```

A non-zero exit code from this step blocks promotion of the release candidate.

### Fixtures

P0 harness fixtures are committed under `src/test/resources/fixtures/p0-harness/`:

| Fixture | Scenario |
|---|---|
| `p0-quick-deploy-request.json` | Valid v2 quick deploy — all required fields present |
| `p0-deploy-request.json` | Valid v2 deploy — all required fields present |
| `p0-validate-request.json` | Valid v2 validate — all required fields including targetOrgId |
| `p0-cancel-request.json` | Cancellation request with safe reason |
| `p0-invalid-quick-deploy-missing-id.json` | Invalid quick deploy — deployRequestId absent; expects 400 |
| `p0-legacy-quick-deploy-request.json` | Valid legacy quick deploy — expects 200 SUCCESS text/plain |
| `p0-status-with-rollback.json` | Failed deploy status with rollback decision fields populated |

### Security constraints

Fixtures must not contain:
- Real Salesforce credentials, bearer tokens, or org URLs
- Real customer identifiers, production pipeline IDs, or deployment request IDs
- Database connection strings, Kubernetes cluster names, or Git provider tokens

---

## Privacy Runbooks — GDPR / CCPA Operational Procedures

> **AUTHORIZATION REQUIRED:** Executing any privacy request procedure against a production environment requires an approved Data Subject Access Request (DSAR) or erasure order, least-privilege production credentials issued by the platform team, and named approver on record. These documents describe procedures; they do not grant access.

The following runbooks document how to execute, verify, and close GDPR and CCPA privacy requests for data held by this service. All procedures reference implemented service controls (classification, audit, retention, purge, masking, safe logging) and must not be followed without proper authorization.

| Document | Purpose |
|---|---|
| [`docs/privacy/privacy-access-runbook.md`](docs/privacy/privacy-access-runbook.md) | Locate and export safe subject-related job, artifact, retention, and audit metadata for GDPR Article 15 / CCPA Right to Know requests |
| [`docs/privacy/privacy-erasure-runbook.md`](docs/privacy/privacy-erasure-runbook.md) | Eligibility checks, legal hold handling, immutable audit exceptions, purge execution, verification, rollback limitations, and escalation for GDPR Article 17 / CCPA Right to Delete requests |
| [`docs/privacy/privacy-certification-checklist.md`](docs/privacy/privacy-certification-checklist.md) | GDPR and CCPA obligation mapping to implemented controls: classification, audit, retention, purge, masking, safe logging, and scope authorization |
| [`docs/privacy/privacy-evidence-template.md`](docs/privacy/privacy-evidence-template.md) | Dry-run and live-execution evidence template: request intake, operator actions, approval checkpoints, purge or retention decisions, verification, and closure |

### Privacy SLOs

| Request type | End-to-end SLO | Governing regulation |
|---|---|---|
| Access (Right to Know) | ≤ 30 days from request receipt | GDPR Article 12 / CCPA |
| Erasure (Right to Delete) | ≤ 30 days from request receipt (GDPR) / ≤ 45 days (CCPA) | GDPR Article 17 / CCPA |

SLO risk escalation path: notify the privacy team lead and engineering manager immediately. Log escalation in the DSAR ticket.

### Privacy runbook security constraints

Privacy runbook documents must not include:
- Real customer names, email addresses, or personal identifiers
- Real production pipeline identifiers, correlation IDs, or job IDs
- Actual operator account names, credentials, or environment-specific values
- Production database connection strings, org URLs, or artifact store credentials
