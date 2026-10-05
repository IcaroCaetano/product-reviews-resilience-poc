package com.project.product_reviews_resilience_poc.product;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

/**
 * Product API: expõe um endpoint dedicado por módulo do Resilience4j,
 * cada um consultando a Reviews API através de
 * {@link ProductReviewsResilienceService}.
 *
 * <p>Essa separação (em vez de um único endpoint combinando os 5 módulos)
 * foi escolhida para a apresentação: cada padrão pode ser acionado e
 * demonstrado isoladamente.</p>
 */
@RestController
public class ProductReviewsController {

    private final ProductReviewsResilienceService service;

    public ProductReviewsController(ProductReviewsResilienceService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/products/{productId}/reviews/circuit-breaker")
    public ReviewsOutcome circuitBreaker(@PathVariable String productId) {
        return service.getReviewsWithCircuitBreaker(productId);
    }

    @GetMapping("/api/v1/products/{productId}/reviews/retry")
    public ReviewsOutcome retry(@PathVariable String productId) {
        return service.getReviewsWithRetry(productId);
    }

    @GetMapping("/api/v1/products/{productId}/reviews/rate-limiter")
    public ReviewsOutcome rateLimiter(@PathVariable String productId) {
        return service.getReviewsWithRateLimiter(productId);
    }

    @GetMapping("/api/v1/products/{productId}/reviews/bulkhead")
    public ReviewsOutcome bulkhead(@PathVariable String productId) {
        return service.getReviewsWithBulkhead(productId);
    }

    @GetMapping("/api/v1/products/{productId}/reviews/time-limiter")
    public CompletableFuture<ReviewsOutcome> timeLimiter(@PathVariable String productId) {
        return service.getReviewsWithTimeLimiter(productId);
    }
}
