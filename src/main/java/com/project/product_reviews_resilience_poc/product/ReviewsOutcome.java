package com.project.product_reviews_resilience_poc.product;

import com.project.product_reviews_resilience_poc.reviews.Review;

import java.util.List;

/**
 * Resultado retornado pelos endpoints da Product API.
 *
 * <p>{@code status} indica se a resposta veio de fato da Reviews API
 * ({@link Status#OK}) ou se foi produzida por um método de fallback
 * ({@link Status#FALLBACK}) por conta de algum dos módulos Resilience4j ter
 * interrompido a chamada (circuito aberto, limite de taxa, bulkhead cheio,
 * timeout ou esgotamento das tentativas de retry).</p>
 */
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
