package com.opsera.integrator.sfdc.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * Validates that the JWT {@code aud} claim contains the configured expected audience.
 *
 * <p>Tokens with a missing or mismatched audience are rejected before controller execution,
 * satisfying the fail-closed requirement for wrong-audience credentials.
 */
public class JwtAudienceValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_AUDIENCE_ERROR = new OAuth2Error(
            "invalid_token",
            "Token audience does not include the required audience; request rejected",
            null);

    private final String expectedAudience;

    public JwtAudienceValidator(String expectedAudience) {
        this.expectedAudience = expectedAudience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        List<String> audience = token.getAudience();
        if (audience == null || !audience.contains(expectedAudience)) {
            return OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE_ERROR);
        }
        return OAuth2TokenValidatorResult.success();
    }
}
