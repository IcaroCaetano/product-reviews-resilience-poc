package com.project.product_reviews_resilience_poc.reviews;


public record Review(String id, String productId, String author, int rating, String comment) {
}
