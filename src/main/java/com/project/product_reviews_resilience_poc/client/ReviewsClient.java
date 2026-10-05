package com.project.product_reviews_resilience_poc.client;

import com.project.product_reviews_resilience_poc.reviews.Review;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Client HTTP "cru" para a Reviews API — sem nenhuma anotação de
 * resiliência. As anotações do Resilience4j (Circuit Breaker, Retry,
 * Rate Limiter, Bulkhead, Time Limiter) são aplicadas uma camada acima, em
 * {@code ProductReviewsResilienceService}, para manter este client simples
 * e cada demonstração isolada por módulo.
 */
@Component
public class ReviewsClient {

    private static final Logger log = LoggerFactory.getLogger(ReviewsClient.class);

    private final RestClient restClient;

    public ReviewsClient(RestClient reviewsRestClient) {
        this.restClient = reviewsRestClient;
    }

    public List<Review> fetchReviews(String productId) {
        log.debug("Chamando Reviews API para productId={}", productId);
        try {
            Review[] reviews = restClient.get()
                    .uri("/api/v1/reviews/{productId}", productId)
                    .retrieve()
                    .body(Review[].class);
            return reviews == null ? List.of() : List.of(reviews);
        } catch (RestClientException e) {
            throw new ReviewsClientException("Falha ao chamar a Reviews API para productId=" + productId, e);
        }
    }
}
