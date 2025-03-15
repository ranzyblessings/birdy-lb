package com.github.birdylb.core.strategy.weightedroundrobin;

import com.github.birdylb.config.WeightedRoundRobinProperties;
import com.github.birdylb.core.model.Backend;
import com.github.birdylb.core.strategy.LoadBalancingStrategy;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implements a weighted round-robin load balancing strategy, optimizing backend selection
 * based on dynamic weights. Ensures thread-safe, reactive operations for high-throughput systems.
 */
@Component
public class WeightedRoundRobinStrategy implements LoadBalancingStrategy {
    private static final Logger logger = LoggerFactory.getLogger(WeightedRoundRobinStrategy.class);

    private final ThreadLocal<Long> threadLocalIndex = ThreadLocal.withInitial(() -> 0L);
    private final BackendWeightUpdater weightUpdater;
    private final Counter selectionCounter;
    private final Counter noHealthyBackendsCounter;
    private final Timer selectionTimer;

    private final AtomicReference<WeightedBackendArray> weightedArray =
            new AtomicReference<>(new WeightedBackendArray(Collections.emptyList()));
    private final ScheduledExecutorService weightUpdaterExecutor = Executors.newSingleThreadScheduledExecutor();

    @Autowired
    public WeightedRoundRobinStrategy(
            MeterRegistry meterRegistry,
            BackendWeightUpdater weightUpdater,
            WeightedRoundRobinProperties weightedRoundRobinProperties) {
        if (meterRegistry == null) {
            throw new IllegalArgumentException("MeterRegistry must not be null");
        }
        if (weightUpdater == null) {
            throw new IllegalArgumentException("WeightUpdater must not be null");
        }

        this.weightUpdater = weightUpdater;

        this.selectionCounter = Counter.builder("loadbalancer.strategy.selections")
                .tag("strategy", "weighted-round-robin")
                .description("Number of successful backend selections")
                .register(meterRegistry);
        this.noHealthyBackendsCounter = Counter.builder("loadbalancer.strategy.no_healthy_backends")
                .tag("strategy", "weighted-round-robin")
                .description("Number of times no healthy backends were available")
                .register(meterRegistry);
        this.selectionTimer = Timer.builder("loadbalancer.strategy.selection_time")
                .tag("strategy", "weighted-round-robin")
                .description("Time taken for backend selection operations")
                .register(meterRegistry);

        if (weightUpdater.requiresPeriodicRefresh()) {
            weightUpdaterExecutor.scheduleAtFixedRate(this::refreshWeights,
                    weightedRoundRobinProperties.weightUpdateScheduler().initialDelay(),
                    weightedRoundRobinProperties.weightUpdateScheduler().period(), TimeUnit.SECONDS
            );
        }
    }

    @Override
    public Mono<Backend> selectBackend(Mono<List<Backend>> backends) {
        if (backends == null) {
            logger.error("Backend Mono is null");
            return Mono.error(new IllegalArgumentException("Backend Mono must not be null"));
        }

        return selectionTimer.record(() ->
                backends
                        .switchIfEmpty(handleEmptyBackendList())
                        .flatMap(this::selectFromFilteredBackends)
                        .publishOn(Schedulers.parallel())
                        .onErrorResume(this::handleSelectionError)
        );
    }

    private Mono<List<Backend>> handleEmptyBackendList() {
        return Mono.fromCallable(() -> {
            logger.warn("Backend list is empty");
            noHealthyBackendsCounter.increment();
            return Collections.emptyList();
        });
    }

    private Mono<Backend> selectFromFilteredBackends(List<Backend> allBackends) {
        return filterHealthyBackends(allBackends)
                .map(this::updateWeights)
                .flatMap(healthyBackends -> {
                    if (healthyBackends.isEmpty()) {
                        logger.warn("No healthy backends available out of {} total backends", allBackends.size());
                        noHealthyBackendsCounter.increment();
                        return Mono.empty();
                    }

                    if (!weightedArray.get().matches(healthyBackends)) {
                        weightedArray.set(new WeightedBackendArray(healthyBackends));
                    }

                    try {
                        Backend selected = selectNextBackend();
                        selectionCounter.increment();

                        if (logger.isDebugEnabled()) {
                            logger.debug("Selected backend: {} (weight: {}, healthy count: {})",
                                    selected.url(), selected.weight(), healthyBackends.size());
                        }

                        return Mono.just(selected);
                    } catch (IllegalStateException e) {
                        logger.error("Failed to select backend due to invalid state", e);
                        return Mono.error(e);
                    }
                });
    }

    private List<Backend> updateWeights(List<Backend> backends) {
        return backends.stream()
                .map(backend -> backend.withWeight(weightUpdater.updateWeight(backend)))
                .toList();
    }

    private Backend selectNextBackend() {
        WeightedBackendArray currentArray = weightedArray.get();
        if (currentArray.totalWeight == 0) {
            throw new IllegalStateException("No backends available with positive weight for selection");
        }
        final long index = threadLocalIndex.get();
        threadLocalIndex.set(index + 1);
        return currentArray.getBackendAt((int) (index % currentArray.totalWeight));
    }

    private Mono<Backend> handleSelectionError(Throwable throwable) {
        logger.error("Error during backend selection", throwable);
        return Mono.empty();
    }

    // Triggered periodically if the updater requires it
    private void refreshWeights() {
        weightedArray.set(new WeightedBackendArray(updateWeights(weightedArray.get().backends)));
    }

    @PreDestroy
    public void shutdown() {
        if (!weightUpdaterExecutor.isShutdown()) {
            weightUpdaterExecutor.shutdown();
            try {
                if (!weightUpdaterExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    weightUpdaterExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                weightUpdaterExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private static class WeightedBackendArray {
        private final List<Backend> backends;
        private final int[] cumulativeWeights;
        private final int totalWeight;

        WeightedBackendArray(List<Backend> backends) {
            this.backends = List.copyOf(backends);
            this.cumulativeWeights = new int[backends.size()];
            int sum = 0;
            for (int i = 0; i < backends.size(); i++) {
                sum += backends.get(i).weight();
                cumulativeWeights[i] = sum;
            }
            this.totalWeight = sum;
        }

        Backend getBackendAt(final int weightIndex) {
            int pos = Arrays.binarySearch(cumulativeWeights, weightIndex + 1);
            if (pos < 0) {
                pos = -pos - 1;
            }
            return backends.get(pos);
        }

        boolean matches(List<Backend> other) {
            return backends.equals(other);
        }
    }
}
