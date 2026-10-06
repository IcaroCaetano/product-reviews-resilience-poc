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

import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RateLimiterIntegrationTest {

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
        wireMockServer.stubFor(get(urlPathMatching("/api/v1/reviews/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":\"r1\",\"productId\":\"p1\",\"author\":\"ana\",\"rating\":5,\"comment\":\"ótimo\"}]")));
    }

    @Test
    void devePermitirApenasOLimiteConfiguradoDeChamadasPorJanelaERejeitarOExcedente() {
        // limitForPeriod=2 na janela corrente: as duas primeiras chamadas devem
        // passar normalmente; a terceira, dentro da mesma janela, deve ser
        // rejeitada (RequestNotPermitted) e cair no fallback.
        List<ReviewsOutcome> outcomes = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            outcomes.add(restTemplate.getForObject(
                    url("/api/v1/products/p1/reviews/rate-limiter"), ReviewsOutcome.class));
        }

        long okCount = outcomes.stream().filter(o -> o.status() == ReviewsOutcome.Status.OK).count();
        long fallbackCount = outcomes.stream().filter(o -> o.status() == ReviewsOutcome.Status.FALLBACK).count();

        assertThat(okCount).isEqualTo(2);
        assertThat(fallbackCount).isEqualTo(1);
        assertThat(outcomes.get(2).message()).contains("RequestNotPermitted");
    }
}
