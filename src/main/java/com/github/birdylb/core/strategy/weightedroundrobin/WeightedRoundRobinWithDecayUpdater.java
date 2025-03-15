package com.github.birdylb.core.strategy.weightedroundrobin;

import com.github.birdylb.core.model.Backend;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A BackendWeightUpdater that implements a round-robin scheduling strategy with backpressure handling.
 * The weight of a backend decays over time, reducing its priority if it is frequently chosen.
 * This mechanism helps prevent overloading backends that are repeatedly selected, promoting better load distribution.
 */
public class WeightedRoundRobinWithDecayUpdater implements BackendWeightUpdater {
    private final long maxWeight; // Maximum possible weight for a backend.
    private final Duration decayInterval; // Time interval after which the weight decays.
    private final Map<Backend, Instant> lastUsedTimestamp = new ConcurrentHashMap<>(); // Track the last time each backend was selected.

    /**
     * Constructs the WeightedRoundRobinWithDecayUpdater with specified maximum weight and decay interval.
     *
     * @param maxWeight     the maximum weight a backend can have.
     * @param decayInterval the duration after which weight starts decaying for a backend.
     */
    public WeightedRoundRobinWithDecayUpdater(long maxWeight, Duration decayInterval) {
        if (maxWeight <= 0 || decayInterval.isNegative()) {
            throw new IllegalArgumentException("Invalid max weight or decay interval");
        }
        this.maxWeight = maxWeight;
        this.decayInterval = decayInterval;
    }

    /**
     * Updates the weight of the given backend based on its usage frequency.
     * The backend's weight decays over time if it is frequently selected.
     *
     * @param backend the backend whose weight is to be updated.
     * @return the updated weight for the backend (at least 1).
     * @throws IllegalArgumentException if the backend is null.
     */
    @Override
    public long updateWeight(Backend backend) {
        if (backend == null) {
            throw new IllegalArgumentException("Backend must not be null");
        }

        Instant now = Instant.now(); // Current timestamp.
        Instant lastUsed = lastUsedTimestamp.getOrDefault(backend, Instant.MIN); // Last timestamp the backend was selected.

        // Calculate the time elapsed since the last usage.
        long timeSinceLastUsed = Duration.between(lastUsed, now).toMillis();

        // Compute weight decay factor based on time since last usage.
        long weightDecayFactor = Math.min(maxWeight, timeSinceLastUsed / decayInterval.toMillis());

        // Update the last used timestamp for the backend.
        lastUsedTimestamp.put(backend, now);

        // Return the adjusted weight, ensuring it's at least 1.
        return Math.max(1, maxWeight - weightDecayFactor);
    }
}