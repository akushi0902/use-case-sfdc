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
