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
# (includes JCenter hygiene, Java toolchain, and NPM artifact guard)
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
```

---

## Build Checks Summary

| Gradle Task | Lifecycle | What It Checks |
|---|---|---|
| `checkNoJCenter` | `check` | No JCenter repository in any `*.gradle` file |
| `checkJavaToolchain` | `check` | `sourceCompatibility` and `targetCompatibility` are Java 21 |
| `checkNoNpmArtifacts` | `check` | No prohibited NPM artifacts in tracked source paths |
| `verifyNpmArtifactGuard` | `check` | Self-test: guard detects prohibited files and passes clean state |

All tasks in the table above run automatically when you run `./gradlew check` or `./gradlew build`.

The `validateDeployScripts` task is also wired into `check` and runs syntax and dry-run validation on all Kubernetes deployment scripts.

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
