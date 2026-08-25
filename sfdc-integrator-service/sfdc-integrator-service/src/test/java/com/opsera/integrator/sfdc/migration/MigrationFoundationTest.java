package com.opsera.integrator.sfdc.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test proving that Flyway migrations execute successfully during
 * Spring application context startup when the test datasource is provided.
 *
 * <p>Uses the "test" profile for H2 datasource configuration, and overrides
 * {@code spring.flyway.enabled=true} so migration execution is tested explicitly.
 * No external PostgreSQL database is required (AC-5).
 *
 * <p>A passing context load is sufficient to confirm migrations ran — Flyway throws
 * on startup if a migration fails or a checksum mismatch is detected, which would
 * cause this test to fail with a clear error before any assertion runs.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.flyway.enabled=true")
class MigrationFoundationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void applicationContextLoadsWithMigrationsEnabled() {
        assertNotNull(dataSource, "DataSource must be available after context startup with Flyway enabled");
    }

    @Test
    void baselineMigrationCreatesSchemaInfoTable() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            // H2 in PostgreSQL mode lowercases identifiers — query with lowercase name
            ResultSet rs = conn.getMetaData().getTables(null, null, "sfdc_schema_info", new String[]{"TABLE"});
            assertTrue(rs.next(), "V1 baseline migration must create the sfdc_schema_info table");
        }
    }

    @Test
    void flywaySchemaHistoryRecordsBaselineMigrationAsSuccessful() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT version, success FROM flyway_schema_history WHERE version = '1'");
            assertTrue(rs.next(), "flyway_schema_history must contain an entry for version 1");
            assertTrue(rs.getBoolean("success"), "V1 migration must be recorded as successful");
        }
    }

    @Test
    void baselineMigrationInsertsSchemaVersionRow() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT schema_version FROM sfdc_schema_info WHERE schema_version = 'V1-baseline'");
            assertTrue(rs.next(), "V1 baseline migration must insert a 'V1-baseline' row into sfdc_schema_info");
        }
    }
}
