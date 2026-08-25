package com.opsera.integrator.sfdc.fixtures;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SyntheticFixtureFactory} (AC-5).
 *
 * <p>Covers: deterministic hash stability, null/blank handling, safe placeholder generation,
 * and that generated values do not resemble production data.
 */
class SyntheticFixtureFactoryTest {

    // ---- Deterministic hash stability ----

    @Test
    void customerIdHash_sameSeed_returnsSameValue() {
        assertEquals(
            SyntheticFixtureFactory.customerIdHash("scenario-alpha"),
            SyntheticFixtureFactory.customerIdHash("scenario-alpha")
        );
    }

    @Test
    void customerIdHash_differentSeeds_returnDifferentValues() {
        assertNotEquals(
            SyntheticFixtureFactory.customerIdHash("seed-A"),
            SyntheticFixtureFactory.customerIdHash("seed-B")
        );
    }

    @Test
    void customerIdHash_returns64CharLowercaseHex() {
        String hash = SyntheticFixtureFactory.customerIdHash("test-customer");
        assertEquals(64, hash.length(), "customerIdHash must be 64 chars");
        assertTrue(hash.matches("[0-9a-f]{64}"), "customerIdHash must be lowercase hex");
    }

    @Test
    void pipelineId_sameSeed_returnsSameValue() {
        assertEquals(
            SyntheticFixtureFactory.pipelineId("scenario-1"),
            SyntheticFixtureFactory.pipelineId("scenario-1")
        );
    }

    @Test
    void pipelineId_differentSeeds_returnDifferentValues() {
        assertNotEquals(
            SyntheticFixtureFactory.pipelineId("seed-X"),
            SyntheticFixtureFactory.pipelineId("seed-Y")
        );
    }

    // ---- Null and blank input handling ----

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void customerIdHash_nullOrBlank_returnsSafeDefault(String input) {
        String result = SyntheticFixtureFactory.customerIdHash(input);
        assertNotNull(result, "Must not return null for null/blank input");
        assertEquals(64, result.length(), "Safe default must still be 64 chars");
        assertFalse(result.contains("null"), "Safe default must not contain 'null'");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void pipelineId_nullOrBlank_returnsSafePlaceholder(String input) {
        String result = SyntheticFixtureFactory.pipelineId(input);
        assertNotNull(result);
        assertTrue(result.startsWith("pipeline-fixture-"), "Should use pipeline-fixture- prefix");
        assertFalse(result.contains("null"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void email_nullOrBlank_returnsSafeDefaultEmail(String input) {
        String result = SyntheticFixtureFactory.email(input);
        assertNotNull(result);
        assertTrue(result.contains("@fixture.example.internal"),
            "Default email must use safe fixture domain");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void token_nullOrBlank_returnsSafePlaceholder(String input) {
        String result = SyntheticFixtureFactory.token(input);
        assertNotNull(result);
        assertTrue(result.startsWith("tok-fixture-"), "Token must start with tok-fixture-");
        assertFalse(result.contains("."), "Token must not contain dots (not a JWT)");
    }

    // ---- Safe placeholder generation ----

    @Test
    void email_usesSafeFixtureDomain() {
        String email = SyntheticFixtureFactory.email("john.doe");
        assertTrue(email.endsWith("@fixture.example.internal"),
            "Email must use safe fixture domain, not a real domain. Actual: " + email);
        assertFalse(email.contains("gmail"), "Email must not contain real domain names");
        assertFalse(email.contains("salesforce"), "Email must not contain Salesforce domain");
    }

    @Test
    void sourceOrgUrl_usesSafeFixtureDomain() {
        String url = SyntheticFixtureFactory.sourceOrgUrl("test-org");
        assertTrue(url.startsWith("https://source-"), "URL must start with https://source-");
        assertTrue(url.contains(".org.fixture.example.internal"),
            "URL must use safe fixture domain. Actual: " + url);
        assertFalse(url.contains("salesforce.com"), "URL must not contain salesforce.com");
        assertFalse(url.contains("force.com"), "URL must not contain force.com");
    }

    @Test
    void targetOrgUrl_usesSafeFixtureDomain() {
        String url = SyntheticFixtureFactory.targetOrgUrl("test-org");
        assertTrue(url.startsWith("https://target-"), "URL must start with https://target-");
        assertFalse(url.contains("salesforce.com"), "URL must not contain salesforce.com");
    }

    @Test
    void token_doesNotResembleJwtOrBearerToken() {
        String tok = SyntheticFixtureFactory.token("test-service");
        assertFalse(tok.startsWith("eyJ"), "Token must not look like a JWT");
        assertFalse(tok.contains("."), "Token must not contain dots (no JWT format)");
        assertTrue(tok.startsWith("tok-fixture-"), "Token must use tok-fixture- prefix");
        assertTrue(tok.length() >= 24, "Token must be at least 24 chars");
    }

    @Test
    void sfdcOrgRef_doesNotResembleRealOrgId() {
        String orgRef = SyntheticFixtureFactory.sfdcOrgRef("test-org");
        assertTrue(orgRef.startsWith("00Dfixture"), "Org ref must contain 'fixture' marker");
        assertFalse(orgRef.matches("00D[0-9A-Za-z]{15}"),
            "Org ref must be distinguishable from a real 18-char org ID");
    }

    @Test
    void correlationId_hasCorrectPrefix() {
        String cid = SyntheticFixtureFactory.correlationId("test-request");
        assertTrue(cid.startsWith("cid-fixture-"), "Correlation ID must use cid-fixture- prefix");
    }

    @Test
    void deploymentRequestId_hasCorrectPrefix() {
        String drid = SyntheticFixtureFactory.deploymentRequestId("test-deploy");
        assertTrue(drid.startsWith("deploy-req-fixture-"), "Must use deploy-req-fixture- prefix");
    }

    @Test
    void jobId_hasCorrectPrefix() {
        String jid = SyntheticFixtureFactory.jobId("test-job");
        assertTrue(jid.startsWith("job-fixture-"), "Must use job-fixture- prefix");
    }

    // ---- Isolation: salt ensures fixture values don't match production patterns ----

    @Test
    void sha256Hex_returnsLowercaseHex() {
        String result = SyntheticFixtureFactory.sha256Hex("test-input");
        assertEquals(64, result.length());
        assertTrue(result.matches("[0-9a-f]{64}"), "sha256Hex must return lowercase hex");
    }

    @Test
    void shortHash_returns12CharString() {
        String hash = SyntheticFixtureFactory.shortHash("seed", "namespace");
        assertEquals(12, hash.length(), "shortHash must return 12 chars");
    }

    // ---- Masking convention: field names don't leak through prefix stripping ----

    @Test
    void allGenerators_handleLongSeedWithoutError() {
        String longSeed = "a".repeat(10_000);
        assertDoesNotThrow(() -> SyntheticFixtureFactory.customerIdHash(longSeed));
        assertDoesNotThrow(() -> SyntheticFixtureFactory.email(longSeed));
        assertDoesNotThrow(() -> SyntheticFixtureFactory.token(longSeed));
        assertDoesNotThrow(() -> SyntheticFixtureFactory.sourceOrgUrl(longSeed));
    }
}
