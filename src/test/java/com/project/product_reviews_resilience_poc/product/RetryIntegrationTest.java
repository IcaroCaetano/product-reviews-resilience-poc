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
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RetryIntegrationTest {

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
    void deveTentarNovamenteESucederAposFalhasTransitorias() {
        String scenarioName = "retry-success-on-third-attempt";

        wireMockServer.stubFor(get(urlPathMatching("/api/v1/reviews/.*"))
                .inScenario(scenarioName)
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("segunda-falha"));

        wireMockServer.stubFor(get(urlPathMatching("/api/v1/reviews/.*"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("segunda-falha")
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("terceira-tentativa-sucesso"));

        wireMockServer.stubFor(get(urlPathMatching("/api/v1/reviews/.*"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("terceira-tentativa-sucesso")
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":\"r1\",\"productId\":\"p1\",\"author\":\"ana\",\"rating\":5,\"comment\":\"ótimo\"}]")));

        ReviewsOutcome outcome = restTemplate.getForObject(
                url("/api/v1/products/p1/reviews/retry"), ReviewsOutcome.class);

        assertThat(outcome.status()).isEqualTo(ReviewsOutcome.Status.OK);
        assertThat(outcome.reviews()).hasSize(1);
        wireMockServer.verify(3, getRequestedFor(urlPathMatching("/api/v1/reviews/.*")));
    }

    @Test
    void deveEsgotarAsTentativasECairNoFallbackQuandoReviewsApiNuncaSeRecupera() {
        wireMockServer.stubFor(get(urlPathMatching("/api/v1/reviews/.*"))
                .willReturn(aResponse().withStatus(503)));

        ReviewsOutcome outcome = restTemplate.getForObject(
                url("/api/v1/products/p1/reviews/retry"), ReviewsOutcome.class);

        assertThat(outcome.status()).isEqualTo(ReviewsOutcome.Status.FALLBACK);
        assertThat(outcome.message()).contains("ReviewsClientException");
        // maxAttempts=3 no application.yaml: 1 chamada original + 2 retries = 3 chamadas no total.
        wireMockServer.verify(3, getRequestedFor(urlPathMatching("/api/v1/reviews/.*")));
    }
}
