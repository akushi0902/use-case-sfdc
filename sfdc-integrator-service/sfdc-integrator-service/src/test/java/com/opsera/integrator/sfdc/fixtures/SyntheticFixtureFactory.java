package com.opsera.integrator.sfdc.fixtures;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Generates deterministic, sanitized test fixture values for compliance-sensitive tests.
 *
 * <p>All generated values are:
 * <ul>
 *   <li><b>Deterministic:</b> same seed → same output across JVM instances.</li>
 *   <li><b>Clearly synthetic:</b> prefixed or domain-suffixed to distinguish from production data.</li>
 *   <li><b>Free of real PII, credentials, org URLs, or customer identifiers.</b></li>
 * </ul>
 *
 * <p>Uses a fixed, non-secret test salt ({@link #TEST_SALT}). This salt must never be used in
 * production hashing or masking — it exists only to make fixture output repeatable in tests.
 *
 * <p>Masking convention summary:
 * <ul>
 *   <li>Customer IDs → 64-char lowercase hex string via SHA-256</li>
 *   <li>Pipeline/step/task/job IDs → {@code <type>-fixture-<12-char-hash>}</li>
 *   <li>Emails → {@code user-<hash>@fixture.example.internal}</li>
 *   <li>Org URLs → {@code https://org-<hash>.org.fixture.example.internal}</li>
 *   <li>Tokens → {@code tok-fixture-<24-char-hash>} (no dots — not a JWT)</li>
 *   <li>Salesforce org refs → {@code 00Dfixture<HASH>} (not a real org ID)</li>
 * </ul>
 */
public final class SyntheticFixtureFactory {

    static final String TEST_SALT = "sfdc-test-fixture-v1";
    private static final String SAFE_EMAIL_DOMAIN = "fixture.example.internal";
    private static final String SAFE_ORG_DOMAIN = "org.fixture.example.internal";

    private SyntheticFixtureFactory() {}

    // ---- Hashed correlation identifiers ----

    /**
     * Produces a deterministic 64-char lowercase hex string for the given seed.
     * Suitable for use as a {@code customer_id_hash} — the same format stored by the schema.
     *
     * <p>Null or blank input returns a 64-char zero string (safe default, clearly synthetic).
     */
    public static String customerIdHash(String seed) {
        if (seed == null || seed.isBlank()) {
            return "0000000000000000000000000000000000000000000000000000000000000000";
        }
        return sha256Hex(TEST_SALT + ":customer:" + seed);
    }

    // ---- Entity identifier generators ----

    public static String pipelineId(String seed) {
        if (seed == null || seed.isBlank()) return "pipeline-fixture-default";
        return "pipeline-fixture-" + shortHash(seed, "pipeline");
    }

    public static String stepId(String seed) {
        if (seed == null || seed.isBlank()) return "step-fixture-default";
        return "step-fixture-" + shortHash(seed, "step");
    }

    public static String taskId(String seed) {
        if (seed == null || seed.isBlank()) return "task-fixture-default";
        return "task-fixture-" + shortHash(seed, "task");
    }

    public static String deploymentRequestId(String seed) {
        if (seed == null || seed.isBlank()) return "deploy-req-fixture-default";
        return "deploy-req-fixture-" + shortHash(seed, "deploy");
    }

    public static String correlationId(String seed) {
        if (seed == null || seed.isBlank()) return "cid-fixture-default";
        return "cid-fixture-" + shortHash(seed, "cid");
    }

    public static String jobId(String seed) {
        if (seed == null || seed.isBlank()) return "job-fixture-default";
        return "job-fixture-" + shortHash(seed, "job");
    }

    public static String username(String seed) {
        if (seed == null || seed.isBlank()) return "fixture-user-default";
        return "fixture-user-" + shortHash(seed, "user");
    }

    // ---- Sensitive field generators (clearly non-production) ----

    /**
     * Generates a synthetic email address using the safe fixture domain.
     * The format is {@code user-<hash>@fixture.example.internal}.
     *
     * <p>This address satisfies RFC 5321 email format requirements while being
     * clearly non-production. Secret scanners and PII detectors should not flag it
     * as a real user email.
     */
    public static String email(String seed) {
        if (seed == null || seed.isBlank()) return "user-fixture-default@" + SAFE_EMAIL_DOMAIN;
        return "user-" + shortHash(seed, "email") + "@" + SAFE_EMAIL_DOMAIN;
    }

    /**
     * Generates a synthetic Salesforce source org URL using the safe fixture domain.
     * This URL does NOT resolve and is not accepted by Salesforce APIs.
     */
    public static String sourceOrgUrl(String seed) {
        if (seed == null || seed.isBlank()) return "https://source-fixture." + SAFE_ORG_DOMAIN;
        return "https://source-" + shortHash(seed, "sourceorg") + "." + SAFE_ORG_DOMAIN;
    }

    /**
     * Generates a synthetic Salesforce target org URL using the safe fixture domain.
     */
    public static String targetOrgUrl(String seed) {
        if (seed == null || seed.isBlank()) return "https://target-fixture." + SAFE_ORG_DOMAIN;
        return "https://target-" + shortHash(seed, "targetorg") + "." + SAFE_ORG_DOMAIN;
    }

    /**
     * Generates a clearly synthetic token placeholder.
     * Prefixed with {@code tok-fixture-} to distinguish from real tokens.
     * Does NOT resemble a JWT (contains no dots) or a Bearer token.
     */
    public static String token(String seed) {
        if (seed == null || seed.isBlank()) return "tok-fixture-000000000000000000000000";
        return "tok-fixture-" + shortHash(seed, "token") + shortHash(seed, "token2");
    }

    /**
     * Generates a synthetic Salesforce org reference using the {@code 00Dfixture} prefix.
     * The {@code 00D} prefix mirrors the Salesforce org ID format but is followed by
     * {@code fixture} — making it clearly non-production and non-valid.
     */
    public static String sfdcOrgRef(String seed) {
        if (seed == null || seed.isBlank()) return "00DfixtureDefault000000";
        return "00Dfixture" + shortHash(seed, "orgref").toUpperCase();
    }

    /**
     * Generates a synthetic Salesforce report sample reference.
     * These references should not contain real Salesforce record IDs.
     */
    public static String reportSampleRef(String seed) {
        if (seed == null || seed.isBlank()) return "report-fixture-default";
        return "report-fixture-" + shortHash(seed, "report");
    }

    // ---- Internal utilities (package-private for test access) ----

    static String shortHash(String seed, String namespace) {
        return sha256Hex(TEST_SALT + ":" + namespace + ":" + seed).substring(0, 12);
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the Java SE specification — this branch is unreachable
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
