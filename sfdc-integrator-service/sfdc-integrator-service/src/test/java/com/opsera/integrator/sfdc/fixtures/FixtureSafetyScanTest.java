package com.opsera.integrator.sfdc.fixtures;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixture safety scan: asserts that committed fixture files and documentation do not contain
 * real bearer tokens, private keys, production Salesforce domains, or unmasked email addresses.
 *
 * <p>Failure messages include the file path and the category of the unsafe pattern, but never
 * echo the matched value — this prevents secrets from appearing in CI logs (AC-4).
 *
 * <p>Safe patterns that are intentionally allowed:
 * <ul>
 *   <li>Domains ending with {@code .internal}, {@code .example}, or {@code .test}</li>
 *   <li>Email addresses using {@code @fixture.example.internal} or {@code @example.internal}</li>
 *   <li>Tokens prefixed with {@code tok-fixture-}</li>
 *   <li>The literal string {@code <placeholder>} used in documentation examples</li>
 * </ul>
 *
 * <p>Runs as a standard JUnit test during {@code ./gradlew test} — no external dependencies.
 */
class FixtureSafetyScanTest {

    // Directories and files to scan (relative to module root, matching Gradle test working dir)
    private static final List<String> SCAN_PATHS = List.of(
        "src/test/resources/fixtures",
        "src/test/resources/contracts",
        "README.md"
    );

    // ---- Unsafe patterns ----

    /** JWT signature: three base64url segments separated by dots. */
    private static final Pattern JWT_PATTERN = Pattern.compile(
        "eyJ[A-Za-z0-9\\-_]{5,}\\.[A-Za-z0-9\\-_]{5,}\\.[A-Za-z0-9\\-_]{5,}");

    /** PEM private key header. */
    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile(
        "BEGIN\\s+(RSA\\s+|EC\\s+)?PRIVATE\\s+KEY");

    /** Salesforce production domains (not fixture domains). */
    private static final Pattern SALESFORCE_DOMAIN_PATTERN = Pattern.compile(
        "(?i)(login|test|na\\d+|cs\\d+|eu\\d+|ap\\d+)\\.salesforce\\.com"
        + "|(?i)[a-z0-9\\-]+\\.my\\.salesforce\\.com"
        + "|(?i)[a-z0-9\\-]+\\.force\\.com");

    /** Bearer token prefix followed by a credential-length value. */
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile(
        "Bearer\\s+[A-Za-z0-9+/=.\\-_]{40,}");

    /** Real email addresses that use a non-safe domain. */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "\\b[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}\\b");

    // ---- Safe domain/value exemptions ----

    /** Domains that are always safe in fixture files. */
    private static final List<String> SAFE_EMAIL_DOMAIN_SUFFIXES = List.of(
        ".internal", "@example.com", "@example.org", "@example.net"
    );

    /** Value prefixes that indicate intentional safe placeholder tokens. */
    private static final List<String> SAFE_TOKEN_PREFIXES = List.of(
        "tok-fixture-", "placeholder-token", "<token>"
    );

    @Test
    void fixturePaths_containNoUnsafeSecretPatterns() throws IOException {
        List<String> violations = new ArrayList<>();

        for (String scanPath : SCAN_PATHS) {
            Path path = Paths.get(scanPath);
            if (!path.toFile().exists()) continue;

            if (path.toFile().isDirectory()) {
                try (Stream<Path> walk = Files.walk(path)) {
                    walk.filter(Files::isRegularFile)
                        .filter(p -> !p.toString().endsWith(".class"))
                        .forEach(file -> scanFile(file, violations));
                }
            } else {
                scanFile(path, violations);
            }
        }

        assertTrue(violations.isEmpty(),
            "Fixture safety scan found " + violations.size() + " violation(s). " +
            "Fix by replacing unsafe values with synthetic fixture data from SyntheticFixtureFactory.\n" +
            "Violations (file path and category only — values not echoed):\n" +
            String.join("\n", violations));
    }

    private void scanFile(Path file, List<String> violations) {
        String content;
        try {
            content = Files.readString(file);
        } catch (IOException e) {
            violations.add("  SCAN-ERROR " + file + ": " + e.getMessage());
            return;
        }

        String relPath = file.toString();

        // Check JWT patterns
        if (JWT_PATTERN.matcher(content).find()) {
            violations.add("  JWT-PATTERN " + relPath);
        }

        // Check private key headers
        if (PRIVATE_KEY_PATTERN.matcher(content).find()) {
            violations.add("  PRIVATE-KEY " + relPath);
        }

        // Check Salesforce production domains
        if (SALESFORCE_DOMAIN_PATTERN.matcher(content).find()) {
            violations.add("  SALESFORCE-DOMAIN " + relPath);
        }

        // Check bearer tokens
        var bearerMatcher = BEARER_TOKEN_PATTERN.matcher(content);
        if (bearerMatcher.find()) {
            String match = bearerMatcher.group();
            // Allow safe tok-fixture- prefixed tokens described in docs
            boolean isSafe = SAFE_TOKEN_PREFIXES.stream()
                .anyMatch(prefix -> match.toLowerCase().contains(prefix.toLowerCase()));
            if (!isSafe) {
                violations.add("  BEARER-TOKEN " + relPath);
            }
        }

        // Check real email addresses (allow safe fixture and RFC example domains)
        var emailMatcher = EMAIL_PATTERN.matcher(content);
        while (emailMatcher.find()) {
            String match = emailMatcher.group();
            boolean isSafe = SAFE_EMAIL_DOMAIN_SUFFIXES.stream()
                .anyMatch(suffix -> match.toLowerCase().endsWith(suffix.toLowerCase()))
                || match.toLowerCase().contains(".internal")
                || match.toLowerCase().contains("fixture");
            if (!isSafe) {
                violations.add("  REAL-EMAIL " + relPath + " (domain category only)");
                break; // One violation per file to avoid flooding output
            }
        }
    }
}
