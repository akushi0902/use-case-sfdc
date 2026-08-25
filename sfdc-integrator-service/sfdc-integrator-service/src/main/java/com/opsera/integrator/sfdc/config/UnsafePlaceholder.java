package com.opsera.integrator.sfdc.config;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a configuration value does not contain an unsafe sample placeholder pattern.
 *
 * <p>Unsafe placeholder patterns (e.g. {@code placeholder-dev-password-not-real}) are committed
 * to fixture files for dry-run testing only. They must never reach a running deployment.
 *
 * <p>A blank or null value passes this constraint — use {@code @NotBlank} in addition when the
 * field is required. This constraint only rejects values that look like committed sample values
 * in case someone accidentally used a fixture file as real configuration.
 */
@Documented
@Constraint(validatedBy = UnsafePlaceholderValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface UnsafePlaceholder {

    String message() default "Configuration value appears to contain an unsafe sample placeholder. " +
            "Replace with the actual value or a Kubernetes secret reference for this field. " +
            "Remediation: set the environment variable listed in application.yaml for this property.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
