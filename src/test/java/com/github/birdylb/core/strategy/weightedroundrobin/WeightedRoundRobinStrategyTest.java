package com.github.birdylb.core.strategy.weightedroundrobin;

import com.github.birdylb.config.WeightedRoundRobinProperties;
import com.github.birdylb.core.model.Backend;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test suite for {@link WeightedRoundRobinStrategy}, verifying weighted round-robin load balancing.
 * Validates functional correctness, edge cases, concurrency, and scalability for high-throughput systems.
 */
@ExtendWith(MockitoExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("WeightedRoundRobinStrategy Reactive Load Balancing")
@MockitoSettings(strictness = Strictness.LENIENT)
class WeightedRoundRobinStrategyTest {

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private BackendWeightUpdater weightUpdater;

    @Mock
    private Counter selectionCounter;

    @Mock
    private Counter noHealthyBackendsCounter;

    @Mock
    private Timer selectionTimer;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private WeightedRoundRobinProperties weightedRoundRobinProperties;

    private Backend backend1;
    private Backend backend2;
    private Backend unhealthyBackend;
    private WeightedRoundRobinStrategy underTest;

    private MockedStatic<Counter> counterStaticMock;
    private MockedStatic<Timer> timerStaticMock;

    @BeforeEach
    void configureMocksAndFixtures() {
        counterStaticMock = Mockito.mockStatic(Counter.class);
        timerStaticMock = Mockito.mockStatic(Timer.class);

        MeterRegistry.Config config = mock(MeterRegistry.Config.class);
        when(meterRegistry.config()).thenReturn(config);

        Counter.Builder selectionCounterBuilder = mock(Counter.Builder.class);
        counterStaticMock.when(() -> Counter.builder("loadbalancer.strategy.selections"))
                .thenReturn(selectionCounterBuilder);
        when(selectionCounterBuilder.tag("strategy", "weighted-round-robin")).thenReturn(selectionCounterBuilder);
        when(selectionCounterBuilder.description("Number of successful backend selections"))
                .thenReturn(selectionCounterBuilder);
        when(selectionCounterBuilder.register(meterRegistry)).thenReturn(selectionCounter);

        Counter.Builder noHealthyBackendsCounterBuilder = mock(Counter.Builder.class);
        counterStaticMock.when(() -> Counter.builder("loadbalancer.strategy.no_healthy_backends"))
                .thenReturn(noHealthyBackendsCounterBuilder);
        when(noHealthyBackendsCounterBuilder.tag("strategy", "weighted-round-robin"))
                .thenReturn(noHealthyBackendsCounterBuilder);
        when(noHealthyBackendsCounterBuilder.description("Number of times no healthy backends were available"))
                .thenReturn(noHealthyBackendsCounterBuilder);
        when(noHealthyBackendsCounterBuilder.register(meterRegistry)).thenReturn(noHealthyBackendsCounter);

        Timer.Builder selectionTimerBuilder = mock(Timer.Builder.class);
        timerStaticMock.when(() -> Timer.builder("loadbalancer.strategy.selection_time"))
                .thenReturn(selectionTimerBuilder);
        when(selectionTimerBuilder.tag("strategy", "weighted-round-robin")).thenReturn(selectionTimerBuilder);
        when(selectionTimerBuilder.description("Time taken for backend selection operations"))
                .thenReturn(selectionTimerBuilder);
        when(selectionTimerBuilder.register(meterRegistry)).thenReturn(selectionTimer);

        lenient().doAnswer(invocation -> invocation.<Supplier<Mono<Backend>>>getArgument(0).get())
                .when(selectionTimer).record(any(Supplier.class));

        when(weightedRoundRobinProperties.weightUpdateScheduler().initialDelay()).thenReturn(1L);
        when(weightedRoundRobinProperties.weightUpdateScheduler().period()).thenReturn(10L);

        backend1 = new Backend(URI.create("http://backend1:8080"), true, Instant.now(), Optional.empty(), 0L, 2);
        backend2 = new Backend(URI.create("http://backend2:8080"), true, Instant.now(), Optional.empty(), 0L, 1);
        unhealthyBackend = new Backend(URI.create("http://backend3:8080"), false, Instant.now(), Optional.of("timeout"), 0L, 1);

        when(weightUpdater.updateWeight(any(Backend.class))).thenAnswer(invocation -> ((Backend) invocation.getArgument(0)).weight());
        when(weightUpdater.requiresPeriodicRefresh()).thenReturn(false);

        underTest = new WeightedRoundRobinStrategy(meterRegistry, weightUpdater, weightedRoundRobinProperties);
    }

    @AfterEach
    void cleanup() {
        if (counterStaticMock != null) {
            counterStaticMock.close();
        }
        if (timerStaticMock != null) {
            timerStaticMock.close();
        }
        Mockito.reset(meterRegistry, weightUpdater, selectionCounter, noHealthyBackendsCounter, selectionTimer, weightedRoundRobinProperties);
    }

    @Nested
    @DisplayName("Construction and Initialization")
    class ConstructionTests {
        @Test
        @DisplayName("Rejects null MeterRegistry with IllegalArgumentException")
        void failsForNullMeterRegistry() {
            assertThatThrownBy(() -> new WeightedRoundRobinStrategy(null, weightUpdater, weightedRoundRobinProperties))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("MeterRegistry must not be null");
        }

        @Test
        @DisplayName("Rejects null WeightUpdater with IllegalArgumentException")
        void failsForNullWeightUpdater() {
            assertThatThrownBy(() -> new WeightedRoundRobinStrategy(meterRegistry, null, weightedRoundRobinProperties))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("WeightUpdater must not be null");
        }

        @Test
        @DisplayName("Initializes with default weights without periodic refresh")
        void initializesSuccessfully() {
            Mono<List<Backend>> backends = Mono.just(List.of(backend1));
            StepVerifier.create(underTest.selectBackend(backends))
                    .expectNext(backend1)
                    .verifyComplete();
            verify(selectionCounter).increment();
        }
    }

    @Nested
    @DisplayName("Backend Selection Logic")
    class SelectionLogicTests {
        @Test
        @DisplayName("Rejects null backend Mono with IllegalArgumentException")
        void failsForNullBackends() {
            StepVerifier.create(underTest.selectBackend(null))
                    .expectErrorMatches(t -> t instanceof IllegalArgumentException && "Backend Mono must not be null".equals(t.getMessage()))
                    .verify(Duration.ofSeconds(1));
        }

        @Test
        @DisplayName("Handles empty backend list gracefully")
        void handlesEmptyBackendList() {
            Mono<List<Backend>> emptyBackends = Mono.just(Collections.emptyList());
            StepVerifier.create(underTest.selectBackend(emptyBackends))
                    .verifyComplete();
            verify(noHealthyBackendsCounter).increment();
        }

        @Test
        @DisplayName("Handles all unhealthy backends gracefully")
        void handlesAllUnhealthyBackends() {
            Mono<List<Backend>> unhealthyBackends = Mono.just(List.of(unhealthyBackend));
            StepVerifier.create(underTest.selectBackend(unhealthyBackends))
                    .verifyComplete();
            verify(noHealthyBackendsCounter).increment();
        }

        @Test
        @DisplayName("Distributes selections proportionally by weight")
        void respectsWeights() {
            Mono<List<Backend>> backends = Mono.just(List.of(backend1, backend2)); // Weights: 2, 1
            List<Backend> selections = Flux.range(0, 300)
                    .flatMap(i -> underTest.selectBackend(backends))
                    .collectList()
                    .block(Duration.ofSeconds(1));
            long backend1Count = selections.stream().filter(b -> b.equals(backend1)).count();
            long backend2Count = selections.stream().filter(b -> b.equals(backend2)).count();
            assertThat(backend1Count).isCloseTo(200, within(20L)); // Approx. 2/3
            assertThat(backend2Count).isCloseTo(100, within(20L)); // Approx. 1/3
            verify(selectionCounter, times(300)).increment();
        }

        @Test
        @DisplayName("Recovers gracefully from backend fetch error")
        void recoversFromFetchError() {
            Mono<List<Backend>> errorBackends = Mono.error(new RuntimeException("Fetch failed"));
            StepVerifier.create(underTest.selectBackend(errorBackends))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Scalability and Concurrency")
    class ScalabilityAndConcurrencyTests {
        @Test
        @DisplayName("Distributes load proportionally under high concurrency")
        void distributesLoadUnderConcurrency() {
            Mono<List<Backend>> backends = Mono.just(List.of(backend1, backend2)); // Weights: 2, 1
            List<Backend> selections = Flux.range(0, 3000)
                    .flatMap(i -> underTest.selectBackend(backends).subscribeOn(Schedulers.parallel()))
                    .collectList()
                    .block(Duration.ofSeconds(5));
            long backend1Count = selections.stream().filter(b -> b.equals(backend1)).count();
            long backend2Count = selections.stream().filter(b -> b.equals(backend2)).count();
            assertThat(backend1Count).isCloseTo(2000, within(200L)); // Approx. 2/3
            assertThat(backend2Count).isCloseTo(1000, within(200L)); // Approx. 1/3
            verify(selectionCounter, times(3000)).increment();
        }

        @Test
        @DisplayName("Scales efficiently with large backend pool")
        void scalesWithLargeBackendPool() {
            List<Backend> largePool = IntStream.range(0, 1000)
                    .mapToObj(i -> new Backend(URI.create("http://backend%d:8080".formatted(i)), true, Instant.now(), Optional.empty(), 0L, 1 + (i % 5)))
                    .toList();
            Mono<List<Backend>> backends = Mono.just(largePool).cache();
            StepVerifier.create(
                            Flux.range(0, 1000)
                                    .flatMap(i -> underTest.selectBackend(backends), 8)
                                    .subscribeOn(Schedulers.parallel())
                    )
                    .expectNextCount(1000)
                    .verifyComplete();
            verify(selectionCounter, times(1000)).increment();
            verifyNoInteractions(noHealthyBackendsCounter);
        }
    }

    @Nested
    @DisplayName("Weight Updates")
    class WeightUpdateTests {
        @Test
        @DisplayName("Updates weights dynamically via weightUpdater")
        void updatesWeightsDynamically() {
            when(weightUpdater.updateWeight(backend1)).thenReturn(3L);
            when(weightUpdater.updateWeight(backend2)).thenReturn(1L);
            Mono<List<Backend>> backends = Mono.just(List.of(backend1, backend2));
            List<Backend> selections = Flux.range(0, 400)
                    .flatMap(i -> underTest.selectBackend(backends))
                    .collectList()
                    .block(Duration.ofSeconds(1));
            long backend1Count = selections.stream()
                    .filter(b -> b.weight() == 3 && b.url().equals(backend1.url()))
                    .count();
            long backend2Count = selections.stream()
                    .filter(b -> b.weight() == 1 && b.url().equals(backend2.url()))
                    .count();
            assertThat(backend1Count).isCloseTo(300, within(30L)); // Approx. 3/4
            assertThat(backend2Count).isCloseTo(100, within(30L)); // Approx. 1/4
            verify(selectionCounter, times(400)).increment();
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {
        @Test
        @DisplayName("Handles single healthy backend efficiently")
        void handlesSingleHealthyBackend() {
            Mono<List<Backend>> singleBackend = Mono.just(List.of(backend1, unhealthyBackend));
            StepVerifier.create(underTest.selectBackend(singleBackend).repeat(4))
                    .expectNext(backend1)
                    .expectNext(backend1)
                    .expectNext(backend1)
                    .expectNext(backend1)
                    .expectNext(backend1)
                    .verifyComplete();
            verify(selectionCounter, times(5)).increment();
        }
    }
}