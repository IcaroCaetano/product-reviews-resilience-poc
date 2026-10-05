package com.project.product_reviews_resilience_poc.product;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demonstra o módulo <b>Bulkhead</b> (semáforo): a instância
 * {@code reviewsBulkhead} (application.yaml) permite no máximo 2 chamadas
 * concorrentes. Disparamos 5 chamadas simultâneas contra uma Reviews API
 * simulada com latência artificial (para garantir que elas se sobreponham no
 * tempo) e esperamos que ao menos uma delas seja rejeitada (bulkhead cheio)
 * e caia no fallback.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BulkheadIntegrationTest {

    private static final WireMockServer wireMockServer =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        wireMockServer.start();
    }

    @DynamicPropertySource
    static void reviewsBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("app.reviews.base-url", () -> "http://localhost:" + wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void resetWireMock() {
        wireMockServer.resetAll();
        wireMockServer.stubFor(get(urlPathMatching("/api/v1/reviews/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withFixedDelay(1000)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":\"r1\",\"productId\":\"p1\",\"author\":\"ana\",\"rating\":5,\"comment\":\"ótimo\"}]")));
    }

    @Test
    void deveRejeitarChamadasQueExcedamACapacidadeConcorrenteDoBulkhead() throws Exception {
        int totalCalls = 5;
        ExecutorService executor = Executors.newFixedThreadPool(totalCalls);
        try {
            List<Callable<ReviewsOutcome>> tasks = new ArrayList<>();
            for (int i = 0; i < totalCalls; i++) {
                tasks.add(() -> restTemplate.getForObject(
                        "/api/v1/products/p1/reviews/bulkhead", ReviewsOutcome.class));
            }

            List<Future<ReviewsOutcome>> futures = executor.invokeAll(tasks, 10, TimeUnit.SECONDS);

            List<ReviewsOutcome> outcomes = new ArrayList<>();
            for (Future<ReviewsOutcome> future : futures) {
                outcomes.add(future.get());
            }

            long okCount = outcomes.stream().filter(o -> o.status() == ReviewsOutcome.Status.OK).count();
            long fallbackCount = outcomes.stream().filter(o -> o.status() == ReviewsOutcome.Status.FALLBACK).count();

            assertThat(okCount + fallbackCount).isEqualTo(totalCalls);
            // maxConcurrentCalls=2 no application.yaml: com 5 chamadas simultâneas
            // e 1s de latência artificial, pelo menos uma precisa ser rejeitada.
            assertThat(fallbackCount).isGreaterThanOrEqualTo(1);
            assertThat(okCount).isGreaterThanOrEqualTo(1);
            outcomes.stream()
                    .filter(o -> o.status() == ReviewsOutcome.Status.FALLBACK)
                    .forEach(o -> assertThat(o.message()).contains("BulkheadFullException"));
        } finally {
            executor.shutdownNow();
        }
    }
}
