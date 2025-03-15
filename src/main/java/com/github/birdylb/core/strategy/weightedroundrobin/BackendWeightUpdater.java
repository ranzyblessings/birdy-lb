package com.github.birdylb.core.strategy.weightedroundrobin;

import com.github.birdylb.core.model.Backend;

/**
 * Strategy for dynamically updating backend weight based on runtime metrics or external factors.
 * Implementations must be thread-safe and efficient, as they may be invoked frequently in high-throughput systems.
 * This interface enables the load balancer to adjust weights based on real-time backend performance or capacity.
 */
public interface BackendWeightUpdater {

    String WEIGHTED_ROUND_ROBIN_WITH_DECAY_UPDATER = "weighted-round-robin-with-decay";

    /**
     * Computes the updated weight for the given backend based on its current state and external metrics.
     *
     * @param backend the backend to evaluate, with access to fields such as connectionCount and lastHealthCheck
     * @return the updated weight (must be positive), reflecting the backend's capacity or priority
     * @throws IllegalArgumentException if the backend is null or the computed weight is invalid (≤ 0)
     */
    long updateWeight(Backend backend);

    /**
     * Indicates whether the updater requires periodic refresh (e.g., for external metrics).
     * If true, the load balancer will schedule regular updates; otherwise, updates will be on-demand.
     *
     * @return true if periodic refresh is required, false otherwise
     */
    default boolean requiresPeriodicRefresh() {
        return false;
    }
}