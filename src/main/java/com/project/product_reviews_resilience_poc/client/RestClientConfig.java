package com.project.product_reviews_resilience_poc.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Infraestrutura HTTP usada pela Product API para chamar a Reviews API.
 *
 * <p>O timeout do {@link RestClient} (connect/read) é deliberadamente mais
 * alto que o timeout configurado no Resilience4j TimeLimiter
 * ({@code resilience4j.timelimiter.instances.reviewsTimeLimiter}), para que
 * nas demonstrações seja o <b>TimeLimiter</b> a interromper a chamada lenta,
 * e não o cliente HTTP "por baixo".</p>
 */
@Configuration
@EnableConfigurationProperties(ReviewsClientProperties.class)
public class RestClientConfig {

    @Bean
    public RestClient reviewsRestClient(ReviewsClientProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Executor dedicado para a chamada assíncrona usada no endpoint de
     * demonstração do Time Limiter (o Resilience4j {@code @TimeLimiter}
     * exige que o método decorado retorne um {@code CompletableFuture}).
     */
    @Bean
    public ExecutorService timeLimiterExecutor() {
        return Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "reviews-time-limiter");
            thread.setDaemon(true);
            return thread;
        });
    }
}
