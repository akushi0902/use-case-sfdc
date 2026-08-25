package com.opsera.integrator.sfdc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the SFDC Integrator Service.
 *
 * <p>This Spring Boot application integrates with Salesforce DevOps workflows,
 * providing deployment, validation, data migration, org health, code scan,
 * and Apex test class management capabilities.
 */
@SpringBootApplication
public class SfdcIntegratorServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SfdcIntegratorServiceApplication.class, args);
    }
}
