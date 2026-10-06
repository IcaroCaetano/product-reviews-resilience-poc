package com.project.product_reviews_resilience_poc.product;

import com.project.product_reviews_resilience_poc.reviews.Review;

import java.util.List;

public record ReviewsOutcome(String productId, Status status, List<Review> reviews, String message) {

    public enum Status {
        OK,
        FALLBACK
    }

    public static ReviewsOutcome ok(String productId, List<Review> reviews) {
        return new ReviewsOutcome(productId, Status.OK, reviews, "ok");
    }

    public static ReviewsOutcome fallback(String productId, String message) {
        return new ReviewsOutcome(productId, Status.FALLBACK, List.of(), message);
    }
}
