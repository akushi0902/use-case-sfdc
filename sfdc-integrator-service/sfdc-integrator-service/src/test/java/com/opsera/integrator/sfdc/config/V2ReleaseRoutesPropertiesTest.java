package com.opsera.integrator.sfdc.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link V2ReleaseRoutesProperties} routing policy logic.
 *
 * <p>Covers: default-deny for unknown types, global-disable precedence,
 * per-operation allow-list evaluation, null/blank input handling, and
 * readOnlyStatusFallback default value.
 */
class V2ReleaseRoutesPropertiesTest {

    // ---- Default values ----

    @Test
    void defaults_globalEnabledTrue() {
        V2ReleaseRoutesProperties props = new V2ReleaseRoutesProperties();
        assertThat(props.isEnabled()).isTrue();
    }

    @Test
    void defaults_readOnlyStatusFallbackTrue() {
        V2ReleaseRoutesProperties props = new V2ReleaseRoutesProperties();
        assertThat(props.isReadOnlyStatusFallback()).isTrue();
    }

    @Test
    void defaults_operationsMapEmpty() {
        V2ReleaseRoutesProperties props = new V2ReleaseRoutesProperties();
        assertThat(props.getOperations()).isEmpty();
    }

    // ---- Default-deny for unconfigured operations ----

    @Test
    void isOperationEnabled_operationNotInMap_returnsFalse() {
        V2ReleaseRoutesProperties props = new V2ReleaseRoutesProperties();
        props.setEnabled(true);

        assertThat(props.isOperationEnabled("QUICK_DEPLOY")).isFalse();
    }

    @Test
    void isOperationEnabled_operationMappedToFalse_returnsFalse() {
        V2ReleaseRoutesProperties props = new V2ReleaseRoutesProperties();
        props.setEnabled(true);
        props.setOperations(mapOf("QUICK_DEPLOY", false));

        assertThat(props.isOperationEnabled("QUICK_DEPLOY")).isFalse();
    }

    @Test
    void isOperationEnabled_operationMappedToTrue_returnsTrue() {
        V2ReleaseRoutesProperties props = new V2ReleaseRoutesProperties();
        props.setEnabled(true);
        props.setOperations(mapOf("QUICK_DEPLOY", true));

        assertThat(props.isOperationEnabled("QUICK_DEPLOY")).isTrue();
    }

    // ---- Global disable precedence ----

    @Test
    void isOperationEnabled_globalDisabled_returnsFalseEvenWhenOperationEnabled() {
        V2ReleaseRoutesProperties props = new V2ReleaseRoutesProperties();
        props.setEnabled(false);
        props.setOperations(mapOf("QUICK_DEPLOY", true));

        assertThat(props.isOperationEnabled("QUICK_DEPLOY")).isFalse();
    }

    @Test
    void isOperationEnabled_globalDisabled_returnsFalseForAllKnownOperations() {
        V2ReleaseRoutesProperties props = new V2ReleaseRoutesProperties();
        props.setEnabled(false);
        Map<String, Boolean> ops = new HashMap<>();
        ops.put("QUICK_DEPLOY", true);
        ops.put("DEPLOY", true);
        ops.put("VALIDATE", true);
        ops.put("CANCEL", true);
        props.setOperations(ops);

        assertThat(props.isOperationEnabled("QUICK_DEPLOY")).isFalse();
        assertThat(props.isOperationEnabled("DEPLOY")).isFalse();
        assertThat(props.isOperationEnabled("VALIDATE")).isFalse();
        assertThat(props.isOperationEnabled("CANCEL")).isFalse();
    }

    // ---- Null and blank input handling ----

    @Test
    void isOperationEnabled_nullOperationType_returnsFalse() {
        V2ReleaseRoutesProperties props = new V2ReleaseRoutesProperties();
        props.setEnabled(true);

        assertThat(props.isOperationEnabled(null)).isFalse();
    }

    @Test
    void isOperationEnabled_blankOperationType_returnsFalse() {
        V2ReleaseRoutesProperties props = new V2ReleaseRoutesProperties();
        props.setEnabled(true);

        assertThat(props.isOperationEnabled("   ")).isFalse();
    }

    @Test
    void isOperationEnabled_emptyOperationType_returnsFalse() {
        V2ReleaseRoutesProperties props = new V2ReleaseRoutesProperties();
        props.setEnabled(true);

        assertThat(props.isOperationEnabled("")).isFalse();
    }

    // ---- Per-operation allow-list evaluation ----

    @Test
    void isOperationEnabled_allP0OperationsEnabled() {
        V2ReleaseRoutesProperties props = new V2ReleaseRoutesProperties();
        props.setEnabled(true);
        Map<String, Boolean> ops = new HashMap<>();
        ops.put("QUICK_DEPLOY", true);
        ops.put("DEPLOY", true);
        ops.put("VALIDATE", true);
        ops.put("CANCEL", false);
        props.setOperations(ops);

        assertThat(props.isOperationEnabled("QUICK_DEPLOY")).isTrue();
        assertThat(props.isOperationEnabled("DEPLOY")).isTrue();
        assertThat(props.isOperationEnabled("VALIDATE")).isTrue();
        assertThat(props.isOperationEnabled("CANCEL")).isFalse();
    }

    @Test
    void isOperationEnabled_quickDeployOnlyEnabled_otherOperationsBlocked() {
        V2ReleaseRoutesProperties props = new V2ReleaseRoutesProperties();
        props.setEnabled(true);
        Map<String, Boolean> ops = new HashMap<>();
        ops.put("QUICK_DEPLOY", true);
        ops.put("DEPLOY", false);
        ops.put("VALIDATE", false);
        props.setOperations(ops);

        assertThat(props.isOperationEnabled("QUICK_DEPLOY")).isTrue();
        assertThat(props.isOperationEnabled("DEPLOY")).isFalse();
        assertThat(props.isOperationEnabled("VALIDATE")).isFalse();
    }

    @Test
    void isOperationEnabled_unknownOperationTypeDeniedByDefault() {
        V2ReleaseRoutesProperties props = new V2ReleaseRoutesProperties();
        props.setEnabled(true);
        props.setOperations(mapOf("QUICK_DEPLOY", true));

        assertThat(props.isOperationEnabled("ROLLBACK")).isFalse();
        assertThat(props.isOperationEnabled("UNKNOWN_OP")).isFalse();
    }

    // ---- setOperations null safety ----

    @Test
    void setOperations_null_doesNotThrowAndEmptiesMap() {
        V2ReleaseRoutesProperties props = new V2ReleaseRoutesProperties();
        props.setOperations(mapOf("QUICK_DEPLOY", true));
        props.setOperations(null);

        assertThat(props.getOperations()).isEmpty();
        assertThat(props.isOperationEnabled("QUICK_DEPLOY")).isFalse();
    }

    // ---- Helpers ----

    private Map<String, Boolean> mapOf(String key, boolean value) {
        Map<String, Boolean> map = new HashMap<>();
        map.put(key, value);
        return map;
    }
}
