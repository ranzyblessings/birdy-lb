package com.github.birdylb.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Configuration properties for Weighted Round Robin load balancer strategy.
 */
@ConfigurationProperties(prefix = "loadbalancer.weighted-round-robin")
public class WeightedRoundRobinProperties {
    private final BackendWeightUpdater backendWeightUpdater;
    private final WeightUpdateScheduler weightUpdateScheduler;

    @ConstructorBinding
    public WeightedRoundRobinProperties(BackendWeightUpdater backendWeightUpdater, WeightUpdateScheduler weightUpdateScheduler) {
        this.backendWeightUpdater = backendWeightUpdater != null ? backendWeightUpdater : new BackendWeightUpdater("weighted-round-robin-with-decay", 100, 5);
        this.weightUpdateScheduler = weightUpdateScheduler != null ? weightUpdateScheduler : new WeightUpdateScheduler(0, 5);
    }

    public BackendWeightUpdater backendWeightUpdater() {
        return backendWeightUpdater;
    }

    public WeightUpdateScheduler weightUpdateScheduler() {
        return weightUpdateScheduler;
    }

    /**
     * Configures the backend weight updater algorithm.
     */
    public record BackendWeightUpdater(
            @NotBlank(message = "Weight updater algorithm must not be null or empty")
            String algorithm,

            @Positive(message = "The maximum weight a backend can have")
            long maxWeight,

            @Positive(message = "The duration after which weight starts decaying for a backend in seconds")
            long decayInterval
    ) {
    }

    /**
     * Nested record for weight update scheduling configuration.
     */
    public record WeightUpdateScheduler(
            @PositiveOrZero(message = "Initial delay cannot be negative")
            long initialDelay,

            @Positive(message = "Period must be positive")
            long period
    ) {
    }

    @Override
    public String toString() {
        return "LoadBalancerProperties{backendWeightUpdater=%s, weightUpdateScheduler=%s}"
                .formatted(backendWeightUpdater, weightUpdateScheduler);
    }
}