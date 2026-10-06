package com.project.product_reviews_resilience_poc.client;

import com.project.product_reviews_resilience_poc.reviews.Review;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
public class ReviewsClient {

    private static final Logger log = LoggerFactory.getLogger(ReviewsClient.class);

    private final RestClient restClient;

    public ReviewsClient(RestClient reviewsRestClient) {
        this.restClient = reviewsRestClient;
    }

    public List<Review> fetchReviews(String productId) {
        log.debug("Calling Reviews API to productId={}", productId);
        try {
            Review[] reviews = restClient.get()
                    .uri("/api/v1/reviews/{productId}", productId)
                    .retrieve()
                    .body(Review[].class);
            return reviews == null ? List.of() : List.of(reviews);
        } catch (RestClientException e) {
            throw new ReviewsClientException("Error to call Reviews API to productId=" + productId, e);
        }
    }
}
