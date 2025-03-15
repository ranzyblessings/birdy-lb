package com.github.birdylb.core.strategy;

import com.github.birdylb.core.model.Backend;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * Defines a reactive strategy for selecting a backend in a distributed load balancer.
 * Implementations must be non-blocking, thread-safe, and optimized for high-throughput environments.
 */
public interface LoadBalancingStrategy {
    /**
     * Selects a backend reactively from a dynamic stream of backends.
     *
     * @param backends a Mono emitting an immutable snapshot of available backends
     * @return a Mono emitting the selected backend, or empty if none available
     * @throws IllegalArgumentException if the backends Mono is null
     */
    Mono<Backend> selectBackend(Mono<List<Backend>> backends);

    /**
     * Filters backends based on a health predicate, defaulting to isHealthy().
     * Executes on a bounded elastic scheduler to avoid blocking the main thread.
     *
     * @param backends the list of backends to filter
     * @return a Mono emitting the filtered list
     */
    default Mono<List<Backend>> filterHealthyBackends(List<Backend> backends) {
        return Mono.fromCallable(() -> backends.stream()
                        .filter(Backend::isHealthy)
                        .toList())
                .subscribeOn(Schedulers.boundedElastic()); // Offload CPU-bound work
    }
}