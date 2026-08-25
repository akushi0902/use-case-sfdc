package com.opsera.integrator.sfdc.observability.health;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KafkaConnectivityHealthIndicator — unit tests")
class KafkaConnectivityHealthIndicatorTest {

    @Test
    @DisplayName("returns UNKNOWN when bootstrap-servers is blank")
    void health_blankBootstrapServers_returnsUnknown() {
        KafkaConnectivityHealthIndicator indicator = new KafkaConnectivityHealthIndicator("");

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(health.getDetails()).containsEntry("reason", "bootstrap-servers-not-configured");
    }

    @Test
    @DisplayName("returns UNKNOWN when bootstrap-servers is null (empty string via @Value default)")
    void health_nullBootstrapServers_returnsUnknown() {
        KafkaConnectivityHealthIndicator indicator = new KafkaConnectivityHealthIndicator(null);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
    }

    @Test
    @DisplayName("returns UP when bootstrap-servers is configured with a single broker")
    void health_singleBroker_returnsUp() {
        KafkaConnectivityHealthIndicator indicator = new KafkaConnectivityHealthIndicator("kafka-broker:9092");

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("brokerCount", 1);
    }

    @Test
    @DisplayName("returns UP and counts multiple brokers from comma-separated list")
    void health_multiplebrokers_returnsUpWithCorrectCount() {
        KafkaConnectivityHealthIndicator indicator =
                new KafkaConnectivityHealthIndicator("broker1:9092,broker2:9092,broker3:9092");

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("brokerCount", 3);
    }

    @Test
    @DisplayName("health detail values do not contain credentials or raw endpoint URLs")
    void health_detailsAreBounded_noCredentials() {
        KafkaConnectivityHealthIndicator indicator = new KafkaConnectivityHealthIndicator("kafka:9092");

        Health health = indicator.health();

        health.getDetails().values().forEach(v ->
                assertThat(v.toString()).doesNotContain("password", "token", "secret", "kafka:9092"));
    }
}
