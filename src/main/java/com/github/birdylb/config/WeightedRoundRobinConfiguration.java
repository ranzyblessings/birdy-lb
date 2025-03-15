package com.github.birdylb.config;

import com.github.birdylb.core.strategy.weightedroundrobin.BackendWeightUpdater;
import com.github.birdylb.core.strategy.weightedroundrobin.WeightedRoundRobinWithDecayUpdater;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configures the Weighted Round Robin load balancer components.
 */
@Configuration
@EnableConfigurationProperties(WeightedRoundRobinProperties.class)
public class WeightedRoundRobinConfiguration {
    private final WeightedRoundRobinProperties properties;

    public WeightedRoundRobinConfiguration(WeightedRoundRobinProperties properties) {
        this.properties = properties;
    }

    /**
     * Provides a {@link BackendWeightUpdater} based on the configured algorithm.
     *
     * @return a weight updater instance tailored to the specified algorithm
     * @throws IllegalArgumentException if the algorithm is not supported
     */
    @Bean
    public BackendWeightUpdater backendWeightUpdater() {
        final WeightedRoundRobinProperties.BackendWeightUpdater updaterConfig = properties.backendWeightUpdater();
        final String algorithm = updaterConfig.algorithm().toLowerCase();

        return switch (algorithm) {
            case BackendWeightUpdater.WEIGHTED_ROUND_ROBIN_WITH_DECAY_UPDATER -> new WeightedRoundRobinWithDecayUpdater(
                    updaterConfig.maxWeight(),
                    Duration.ofSeconds(updaterConfig.decayInterval())
            );
            default -> throw new IllegalArgumentException("Unsupported weight updater algorithm: " + algorithm);
        };
    }
}