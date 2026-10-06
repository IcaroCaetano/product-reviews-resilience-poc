package com.project.product_reviews_resilience_poc.product;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TimeLimiterIntegrationTest {

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

    @Value("${local.server.port}")
    private int port;

    private final RestTemplate restTemplate = new RestTemplate();

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @BeforeEach
    void resetWireMock() {
        wireMockServer.resetAll();
    }

    @Test
    void deveRetornarDadosReaisQuandoReviewsApiRespondeDentroDoTempo() {
        wireMockServer.stubFor(get(urlPathMatching("/api/v1/reviews/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withFixedDelay(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":\"r1\",\"productId\":\"p1\",\"author\":\"ana\",\"rating\":5,\"comment\":\"ótimo\"}]")));

        ReviewsOutcome outcome = restTemplate.getForObject(
                url("/api/v1/products/p1/reviews/time-limiter"), ReviewsOutcome.class);

        assertThat(outcome.status()).isEqualTo(ReviewsOutcome.Status.OK);
        assertThat(outcome.reviews()).hasSize(1);
    }

    @Test
    void deveCairNoFallbackQuandoReviewsApiDemoraMaisQueOTimeoutConfigurado() {
        // timeoutDuration=1500ms no application.yaml; simulamos 3s de latência
        // na Reviews API para garantir que o Time Limiter interrompa a chamada.
        wireMockServer.stubFor(get(urlPathMatching("/api/v1/reviews/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withFixedDelay(3000)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":\"r1\",\"productId\":\"p1\",\"author\":\"ana\",\"rating\":5,\"comment\":\"ótimo\"}]")));

        ReviewsOutcome outcome = restTemplate.getForObject(
                url("/api/v1/products/p1/reviews/time-limiter"), ReviewsOutcome.class);

        assertThat(outcome.status()).isEqualTo(ReviewsOutcome.Status.FALLBACK);
        assertThat(outcome.message()).contains("TimeoutException");
    }
}
