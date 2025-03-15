package com.github.birdylb.core.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link Backend}, ensuring immutability, validation, and behavior under diverse conditions
 * critical for load balancer reliability.
 */
@DisplayName("Backend Validation and Behavior")
class BackendTest {

    private URI validUrl;
    private Instant fixedTimestamp;
    private Optional<String> noFailure;

    /**
     * Sets up common test fixtures.
     */
    @BeforeEach
    void setUp() {
        validUrl = URI.create("http://example.com:8080");
        fixedTimestamp = Instant.parse("2025-03-12T10:00:00Z");
        noFailure = Optional.empty();
    }

    @Nested
    @DisplayName("Constructor Validation")
    class ConstructorValidationTests {

        @Test
        @DisplayName("Accepts valid parameters")
        void shouldCreateBackendWithValidParameters() {
            Backend backend = new Backend(validUrl, true, fixedTimestamp, noFailure, 0L, 10);

            assertThat(backend.url()).isEqualTo(validUrl);
            assertThat(backend.isHealthy()).isTrue();
            assertThat(backend.lastHealthCheck()).isEqualTo(fixedTimestamp);
            assertThat(backend.failureReason()).isEmpty();
            assertThat(backend.connectionCount()).isZero();
            assertThat(backend.weight()).isEqualTo(10);
        }

        @Test
        @DisplayName("Accepts HTTPS URL with unhealthy state")
        void shouldCreateBackendWithHttpsAndUnhealthy() {
            URI httpsUrl = URI.create("https://secure.example.com");
            String reason = "Timeout";
            Backend backend = new Backend(httpsUrl, false, fixedTimestamp, Optional.of(reason), 5L, 5);

            assertThat(backend.url()).isEqualTo(httpsUrl);
            assertThat(backend.isHealthy()).isFalse();
            assertThat(backend.failureReason()).isPresent().contains(reason);
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("Rejects null URL")
        void shouldThrowForNullUrl(URI nullUrl) {
            assertThatThrownBy(() -> new Backend(nullUrl, true, fixedTimestamp, noFailure, 0L, 20))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("URL must not be null");
        }

        @ParameterizedTest
        @ValueSource(strings = {"ftp://example.com", "ws://example.com", ""})
        @DisplayName("Rejects invalid URL scheme")
        void shouldThrowForInvalidScheme(String invalidUri) {
            assertThatThrownBy(() -> new Backend(URI.create(invalidUri), true, fixedTimestamp, noFailure, 0L, 15))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("scheme must be http or https");
        }

        @Test
        @DisplayName("Rejects URL without host")
        void shouldThrowForUrlWithoutHost() {
            URI noHostUrl = URI.create("http:///path");
            assertThatThrownBy(() -> new Backend(noHostUrl, true, fixedTimestamp, noFailure, 0L, 5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("URL must specify a valid host");
        }

        @Test
        @DisplayName("Rejects negative connection count")
        void shouldThrowForNegativeConnectionCount() {
            assertThatThrownBy(() -> new Backend(validUrl, true, fixedTimestamp, noFailure, -1L, 5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Connection count cannot be negative");
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
        @DisplayName("Rejects non-positive weight")
        void shouldThrowForNonPositiveWeight(int invalidWeight) {
            assertThatThrownBy(() -> new Backend(validUrl, true, fixedTimestamp, noFailure, 0L, invalidWeight))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Weight must be positive");
        }
    }

    @Nested
    @DisplayName("Health Updates")
    class HealthUpdateTests {

        @Test
        @DisplayName("Updates to healthy state")
        void shouldUpdateToHealthy() {
            Backend initial = new Backend(validUrl, false, fixedTimestamp, Optional.of("initial failure"), 3L, 10);
            Backend updated = initial.updateHealth(true, fixedTimestamp.plusSeconds(1), "ignored reason");

            assertThat(updated.isHealthy()).isTrue();
            assertThat(updated.lastHealthCheck()).isEqualTo(fixedTimestamp.plusSeconds(1));
            assertThat(updated.failureReason()).isEmpty();
            assertThat(updated.connectionCount()).isEqualTo(3L);
        }

        @Test
        @DisplayName("Updates to unhealthy state with reason")
        void shouldUpdateToUnhealthyWithReason() {
            Backend initial = new Backend(validUrl, true, fixedTimestamp, noFailure, 2L, 25);
            String reason = "Connection timeout";
            Backend updated = initial.updateHealth(false, fixedTimestamp.plusSeconds(1), reason);

            assertThat(updated.isHealthy()).isFalse();
            assertThat(updated.lastHealthCheck()).isEqualTo(fixedTimestamp.plusSeconds(1));
            assertThat(updated.failureReason()).isPresent().contains(reason);
            assertThat(updated.connectionCount()).isEqualTo(2L);
        }

        @Test
        @DisplayName("Updates to unhealthy state without reason")
        void shouldUpdateToUnhealthyWithNullReason() {
            Backend initial = new Backend(validUrl, true, fixedTimestamp, noFailure, 1L, 5);
            Backend updated = initial.updateHealth(false, fixedTimestamp.plusSeconds(1), null);

            assertThat(updated.isHealthy()).isFalse();
            assertThat(updated.lastHealthCheck()).isEqualTo(fixedTimestamp.plusSeconds(1));
            assertThat(updated.failureReason()).isEmpty();
            assertThat(updated.connectionCount()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("Connection Count Adjustments")
    class ConnectionCountAdjustmentTests {

        @Test
        @DisplayName("Increases connection count")
        void shouldIncreaseConnectionCount() {
            Backend initial = new Backend(validUrl, true, fixedTimestamp, noFailure, 2L, 20);
            Backend updated = initial.adjustConnections(3);

            assertThat(updated.connectionCount()).isEqualTo(5L);
            assertThat(updated.url()).isEqualTo(initial.url());
            assertThat(updated.isHealthy()).isEqualTo(initial.isHealthy());
            assertThat(updated.lastHealthCheck()).isEqualTo(initial.lastHealthCheck());
            assertThat(updated.failureReason()).isEqualTo(initial.failureReason());
        }

        @Test
        @DisplayName("Prevents negative connection count")
        void shouldNotGoBelowZero() {
            Backend initial = new Backend(validUrl, true, fixedTimestamp, noFailure, 2L, 20);
            Backend updated = initial.adjustConnections(-5);

            assertThat(updated.connectionCount()).isZero();
        }

        @ParameterizedTest
        @CsvSource({
                "0, 1, 1",
                "3, -2, 1",
                "2, -5, 0"
        })
        @DisplayName("Adjusts connection count accurately")
        void shouldAdjustConnectionCount(long initialCount, long delta, long expectedCount) {
            Backend initial = new Backend(validUrl, true, fixedTimestamp, noFailure, initialCount, 5);
            Backend updated = initial.adjustConnections(delta);

            assertThat(updated.connectionCount()).isEqualTo(expectedCount);
        }

        @Test
        @DisplayName("Supports chained adjustments")
        void shouldHandleChainedAdjustments() {
            Backend backend = new Backend(validUrl, true, fixedTimestamp, noFailure, 0L, 5)
                    .adjustConnections(5)  // +5 = 5
                    .adjustConnections(-2) // -2 = 3
                    .adjustConnections(1); // +1 = 4

            assertThat(backend.connectionCount()).isEqualTo(4L);
        }
    }

    @Nested
    @DisplayName("Weight Updates")
    class WeightUpdateTests {

        @Test
        @DisplayName("Updates weight")
        void shouldUpdateWeight() {
            Backend initial = new Backend(validUrl, true, fixedTimestamp, noFailure, 2L, 10);
            Backend updated = initial.withWeight(20);

            assertThat(updated.weight()).isEqualTo(20);
            assertThat(updated).usingRecursiveComparison()
                    .ignoringFields("weight")
                    .isEqualTo(initial);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
        @DisplayName("Rejects non-positive weight")
        void shouldThrowForNonPositiveWeightUpdate(int invalidWeight) {
            Backend initial = new Backend(validUrl, true, fixedTimestamp, noFailure, 0L, 5);
            assertThatThrownBy(() -> initial.withWeight(invalidWeight))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Weight must be positive");
        }

        @Test
        @DisplayName("Supports chained weight updates")
        void shouldHandleChainedWeightUpdates() {
            Backend backend = new Backend(validUrl, true, fixedTimestamp, noFailure, 0L, 10)
                    .withWeight(15)
                    .withWeight(25);

            assertThat(backend.weight()).isEqualTo(25);
            assertThat(backend.connectionCount()).isZero();
            assertThat(backend.isHealthy()).isTrue();
        }
    }
}