# SFDC Integrator Service — Build Notes

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
# Resolve dependencies and run all checks (includes JCenter hygiene and Java toolchain checks)
./gradlew check

# Run tests only
./gradlew test

# Build executable JAR
./gradlew bootJar

# Verify Java 21 toolchain is configured correctly
./gradlew checkJavaToolchain

# Validate no JCenter is declared anywhere in Gradle build files
./gradlew checkNoJCenter

# Print toolchain diagnostics
./gradlew -q javaToolchains
```

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
