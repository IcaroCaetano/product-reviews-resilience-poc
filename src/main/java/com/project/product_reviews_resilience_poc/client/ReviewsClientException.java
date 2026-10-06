package com.project.product_reviews_resilience_poc.client;

public class ReviewsClientException extends RuntimeException {

    public ReviewsClientException(String message) {
        super(message);
    }

    public ReviewsClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
