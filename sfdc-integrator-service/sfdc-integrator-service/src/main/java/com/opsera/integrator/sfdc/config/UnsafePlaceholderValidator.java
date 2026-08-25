package com.opsera.integrator.sfdc.config;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates that a string configuration value does not match known unsafe sample placeholder patterns.
 *
 * <p>Rejected patterns (case-insensitive):
 * <ul>
 *   <li>{@code placeholder} — values from committed fixture files (e.g. {@code placeholder-dev-password-not-real})</li>
 *   <li>{@code not-real} — explicit fixture suffix used across all .env.example files</li>
 *   <li>{@code change_me} / {@code changeme} — common sample token markers</li>
 * </ul>
 *
 * <p>Null and blank values pass this constraint — use {@code @NotBlank} separately when a value is required.
 */
public class UnsafePlaceholderValidator implements ConstraintValidator<UnsafePlaceholder, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String lower = value.toLowerCase();
        return !lower.contains("placeholder")
                && !lower.contains("not-real")
                && !lower.contains("change_me")
                && !lower.contains("changeme");
    }
}
