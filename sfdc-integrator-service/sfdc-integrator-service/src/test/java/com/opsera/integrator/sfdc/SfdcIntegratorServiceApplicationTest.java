package com.opsera.integrator.sfdc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spring application context smoke test.
 *
 * <p>Verifies that the Spring application context loads successfully after
 * repository configuration changes (e.g. JCenter removal). A failure here
 * indicates a wiring regression caused by dependency resolution changes.
 */
@SpringBootTest
@ActiveProfiles("test")
class SfdcIntegratorServiceApplicationTest {

    /**
     * Asserts the Spring application context loads without errors.
     * This test exercises dependency wiring and auto-configuration,
     * confirming no runtime class-loading failures were introduced
     * by the dependency resolution changes.
     */
    @Test
    void contextLoads() {
        // If this test passes, the Spring context assembled successfully,
        // confirming all beans wired correctly after JCenter removal.
    }
}
