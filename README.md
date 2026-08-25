# SFDC Integrator Service

Internal Java and Spring Boot integration platform for Salesforce DevOps workflows.

## Project Structure

```
sfdc-integrator-service/
  sfdc-integrator-service/   # Main application module
    build.gradle              # Build configuration (no JCenter)
    settings.gradle           # Gradle settings
    README.md                 # Build and repository documentation
    src/
      main/java/              # Application source
      main/resources/         # Configuration
      test/java/              # Tests
```

## Build Requirements

- Java 21
- Gradle (via wrapper)
- Access to internal package manager (see `sfdc-integrator-service/sfdc-integrator-service/README.md`)

## Supported Artifact Repositories

Dependencies are resolved exclusively from:
- **Maven Central** – `mavenCentral()`
- **Gradle Plugin Portal** – `gradlePluginPortal()`
- **Internal Opsera Package Manager** – authenticated internal Maven repository

**JCenter is not used and must not be re-added.** See repository hygiene notes in the module README.
