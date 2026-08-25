package com.opsera.integrator.sfdc.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Springdoc OpenAPI configuration for the SFDC Integrator Service.
 *
 * <h3>URL namespace disambiguation</h3>
 * <p>This service uses two distinct URL namespaces that must not be confused:
 * <ul>
 *   <li><b>/api-docs</b> — the Springdoc OpenAPI documentation endpoint (this config).
 *       Returns the generated OpenAPI JSON. It is a <em>documentation</em> resource,
 *       not a business API version.</li>
 *   <li><b>/api/v2/sfdc/...</b> — the v2 business route namespace for release jobs
 *       (e.g. {@code POST /api/v2/sfdc/release-jobs}). These are the actual service
 *       endpoints that dispatch work and return responses.</li>
 * </ul>
 *
 * <h3>API groups</h3>
 * <p>Two groups are configured in {@code application.yaml}:
 * <ul>
 *   <li><b>v2-release-api</b> — typed v2 release contracts under {@code /api/v2/**}.</li>
 *   <li><b>legacy-release-api</b> — legacy coexistence routes ({@code /quickdeploy},
 *       {@code /deploy}, {@code /validate}) that remain available during migration.</li>
 * </ul>
 *
 * <p>Legacy routes in the documentation are marked for coexistence only and must not
 * be removed from the generated spec during the migration window.
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openApiDefinition() {
        return new OpenAPI()
                .info(new Info()
                        .title("SFDC Integrator Service")
                        .version("v2")
                        .description("""
                                Salesforce integration service for release pipeline automation.

                                **Route groups**
                                - **V2 Release API** (`/api/v2/sfdc/...`) — canonical typed contracts. \
                                Use these endpoints for new integrations.
                                - **Legacy Release API** (`/quickdeploy`, `/deploy`, `/validate`) \
                                — legacy coexistence routes maintained during migration. \
                                Do not build new integrations against these paths.

                                **API documentation vs business routes**
                                The path `/api-docs` is the Springdoc documentation endpoint. \
                                It is **not** part of the business route namespace. \
                                Business v2 routes are under `/api/v2/sfdc/`.
                                """)
                        .contact(new Contact()
                                .name("Opsera Platform Team")
                                .email("platform@opsera.io"))
                        .license(new License()
                                .name("Internal — Opsera Proprietary")
                                .url("https://opsera.io")));
    }
}
