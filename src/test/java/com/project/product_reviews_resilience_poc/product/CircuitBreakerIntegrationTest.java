package com.project.product_reviews_resilience_poc.product;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demonstra o módulo <b>Circuit Breaker</b>: a Reviews API (simulada via
 * WireMock) fica indisponível, o circuito abre após o número mínimo de
 * chamadas falhas configurado em {@code resilience4j.circuitbreaker.instances.reviewsCircuitBreaker}
 * e as chamadas seguintes passam a ser rejeitadas localmente (sem nem chegar
 * à Reviews API), caindo no fallback.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CircuitBreakerIntegrationTest {

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

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetState() {
        wireMockServer.resetAll();
        circuitBreakerRegistry.circuitBreaker("reviewsCircuitBreaker").reset();
    }

    @Test
    void deveFicarFechadoERetornarDadosReaisQuandoReviewsApiEstaSaudavel() {
        wireMockServer.stubFor(get(urlPathMatching("/api/v1/reviews/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":\"r1\",\"productId\":\"p1\",\"author\":\"ana\",\"rating\":5,\"comment\":\"ótimo\"}]")));

        ReviewsOutcome outcome = restTemplate.getForObject(
                "/api/v1/products/p1/reviews/circuit-breaker", ReviewsOutcome.class);

        assertThat(outcome.status()).isEqualTo(ReviewsOutcome.Status.OK);
        assertThat(outcome.reviews()).hasSize(1);
        assertThat(circuitBreakerRegistry.circuitBreaker("reviewsCircuitBreaker").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void deveAbrirOCircuitoERejeitarChamadasSeguintesQuandoReviewsApiFalhaRepetidamente() {
        wireMockServer.stubFor(get(urlPathMatching("/api/v1/reviews/.*"))
                .willReturn(aResponse().withStatus(500)));

        // minimumNumberOfCalls=5 no application.yaml: as 5 primeiras chamadas
        // avaliam a taxa de falha (100% > 50% threshold) e abrem o circuito.
        for (int i = 0; i < 5; i++) {
            ReviewsOutcome outcome = restTemplate.getForObject(
                    "/api/v1/products/p1/reviews/circuit-breaker", ReviewsOutcome.class);
            assertThat(outcome.status()).isEqualTo(ReviewsOutcome.Status.FALLBACK);
        }

        assertThat(circuitBreakerRegistry.circuitBreaker("reviewsCircuitBreaker").getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        // Chamada extra: deve ser rejeitada localmente (CallNotPermittedException),
        // sem nem chegar à Reviews API.
        ReviewsOutcome rejected = restTemplate.getForObject(
                "/api/v1/products/p1/reviews/circuit-breaker", ReviewsOutcome.class);
        assertThat(rejected.status()).isEqualTo(ReviewsOutcome.Status.FALLBACK);
        assertThat(rejected.message()).contains("CallNotPermittedException");

        wireMockServer.verify(5, getRequestedFor(urlPathMatching("/api/v1/reviews/.*")));
    }
}
