package com.project.product_reviews_resilience_poc.reviews;

/**
 * Representa uma avaliação (review) de um produto, retornada pela Reviews API.
 */
public record Review(String id, String productId, String author, int rating, String comment) {
}
