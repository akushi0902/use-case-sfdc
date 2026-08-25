package com.opsera.integrator.sfdc.observability.health;

import com.hazelcast.cluster.Cluster;
import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.LifecycleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("HazelcastClusterHealthIndicator — unit tests")
class HazelcastClusterHealthIndicatorTest {

    @Test
    @DisplayName("returns UNKNOWN when HazelcastInstance is null")
    void health_nullInstance_returnsUnknown() {
        HazelcastClusterHealthIndicator indicator = new HazelcastClusterHealthIndicator(null);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(health.getDetails()).containsEntry("reason", "hazelcast-not-configured");
    }

    @Test
    @DisplayName("returns DOWN when Hazelcast lifecycle is not running")
    void health_lifecycleNotRunning_returnsDown() {
        HazelcastInstance instance = mock(HazelcastInstance.class);
        LifecycleService lifecycle = mock(LifecycleService.class);
        when(instance.getLifecycleService()).thenReturn(lifecycle);
        when(lifecycle.isRunning()).thenReturn(false);

        HazelcastClusterHealthIndicator indicator = new HazelcastClusterHealthIndicator(instance);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reason", "hazelcast-lifecycle-terminated");
    }

    @Test
    @DisplayName("returns UP with cluster size when Hazelcast is running")
    void health_lifecycleRunning_returnsUpWithClusterSize() {
        HazelcastInstance instance = mock(HazelcastInstance.class);
        LifecycleService lifecycle = mock(LifecycleService.class);
        Cluster cluster = mock(Cluster.class);
        Member member1 = mock(Member.class);
        Member member2 = mock(Member.class);

        when(instance.getLifecycleService()).thenReturn(lifecycle);
        when(lifecycle.isRunning()).thenReturn(true);
        when(instance.getCluster()).thenReturn(cluster);
        when(cluster.getMembers()).thenReturn(Set.of(member1, member2));

        HazelcastClusterHealthIndicator indicator = new HazelcastClusterHealthIndicator(instance);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("clusterSize", 2);
        assertThat(health.getDetails()).containsEntry("state", "ACTIVE");
    }

    @Test
    @DisplayName("returns DOWN when unexpected exception is thrown")
    void health_unexpectedException_returnsDown() {
        HazelcastInstance instance = mock(HazelcastInstance.class);
        when(instance.getLifecycleService()).thenThrow(new RuntimeException("unexpected"));

        HazelcastClusterHealthIndicator indicator = new HazelcastClusterHealthIndicator(instance);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reason", "health-check-error");
    }

    @Test
    @DisplayName("health details do not contain cluster member addresses or raw config")
    void health_detailsAreBounded_noAddresses() {
        HazelcastInstance instance = mock(HazelcastInstance.class);
        LifecycleService lifecycle = mock(LifecycleService.class);
        Cluster cluster = mock(Cluster.class);
        Member member = mock(Member.class);

        when(instance.getLifecycleService()).thenReturn(lifecycle);
        when(lifecycle.isRunning()).thenReturn(true);
        when(instance.getCluster()).thenReturn(cluster);
        when(cluster.getMembers()).thenReturn(Set.of(member));

        HazelcastClusterHealthIndicator indicator = new HazelcastClusterHealthIndicator(instance);
        Health health = indicator.health();

        health.getDetails().keySet().forEach(key ->
                assertThat(key).doesNotContain("address", "endpoint", "token", "uuid", "host"));
    }
}
