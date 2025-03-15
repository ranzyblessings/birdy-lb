package com.github.birdylb.core.model;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;

/**
 * Immutable representation of a backend server in a load balancer pool, encapsulating identity,
 * health, and metadata for thread-safe algorithmic decisions.
 */
public record Backend(
        URI url,
        boolean isHealthy,
        Instant lastHealthCheck,
        Optional<String> failureReason, // Failure reason if unhealthy, empty otherwise.
        long connectionCount, // Active connections for load tracking (e.g., least-connections algorithm).
        long weight // Weight for load balancing (e.g., weighted round-robin).
) {
    /**
     * Constructs a Backend with validated parameters.
     *
     * @param url             Backend server URL (http/https, non-null, valid host)
     * @param isHealthy       Initial health status
     * @param lastHealthCheck Last health check timestamp
     * @param failureReason   Failure reason if unhealthy (optional)
     * @param connectionCount Initial active connections (non-negative)
     * @param weight          Load balancing weight (positive)
     * @throws IllegalArgumentException if URL is null, invalid, connectionCount < 0, or weight ≤ 0
     */
    public Backend {
        if (url == null) {
            throw new IllegalArgumentException("URL must not be null");
        }
        final String scheme = url.getScheme();
        if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
            throw new IllegalArgumentException("URL scheme must be http or https");
        }
        if (url.getHost() == null) {
            throw new IllegalArgumentException("URL must specify a valid host");
        }
        if (connectionCount < 0) {
            throw new IllegalArgumentException("Connection count cannot be negative");
        }
        if (weight <= 0) {
            throw new IllegalArgumentException("Weight must be positive");
        }
    }

    /**
     * Returns a new Backend with updated health status and timestamp.
     *
     * @param healthy       New health status
     * @param timestamp     Health check timestamp
     * @param failureReason Failure reason if unhealthy (null if healthy)
     * @return Updated Backend instance
     */
    public Backend updateHealth(boolean healthy, Instant timestamp, String failureReason) {
        return new Backend(
                url,
                healthy,
                timestamp,
                healthy ? Optional.empty() : Optional.ofNullable(failureReason),
                connectionCount,
                weight
        );
    }

    /**
     * Returns a new Backend with adjusted connection count, ensuring non-negative values.
     *
     * @param delta Change in connections (positive to increase, negative to decrease)
     * @return Updated Backend instance
     */
    public Backend adjustConnections(final long delta) {
        long newCount = Math.max(0, connectionCount + delta);
        return new Backend(url, isHealthy, lastHealthCheck, failureReason, newCount, weight);
    }

    /**
     * Returns a new Backend with the specified weight.
     *
     * @param newWeight New weight (must be positive)
     * @return Updated Backend instance
     * @throws IllegalArgumentException if newWeight ≤ 0
     */
    public Backend withWeight(long newWeight) {
        return new Backend(url, isHealthy, lastHealthCheck, failureReason, connectionCount, newWeight);
    }
}