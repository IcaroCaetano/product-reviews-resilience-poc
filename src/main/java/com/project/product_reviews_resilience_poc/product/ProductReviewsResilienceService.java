package com.project.product_reviews_resilience_poc.product;

import com.project.product_reviews_resilience_poc.client.ReviewsClient;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Service
public class ProductReviewsResilienceService {

    private static final Logger log = LoggerFactory.getLogger(ProductReviewsResilienceService.class);

    private final ReviewsClient reviewsClient;
    private final ExecutorService timeLimiterExecutor;

    public ProductReviewsResilienceService(ReviewsClient reviewsClient, ExecutorService timeLimiterExecutor) {
        this.reviewsClient = reviewsClient;
        this.timeLimiterExecutor = timeLimiterExecutor;
    }

    // ------------------------------------------------------------------
    // Circuit Breaker
    // ------------------------------------------------------------------

    @CircuitBreaker(name = "reviewsCircuitBreaker", fallbackMethod = "circuitBreakerFallback")
    public ReviewsOutcome getReviewsWithCircuitBreaker(String productId) {
        return ReviewsOutcome.ok(productId, reviewsClient.fetchReviews(productId));
    }

    private ReviewsOutcome circuitBreakerFallback(String productId, Throwable throwable) {
        log.warn("Circuit Breaker 'reviewsCircuitBreaker' acionou fallback para productId={}: {}",
                productId, throwable.toString());
        return ReviewsOutcome.fallback(productId,
                "Circuit breaker active (" + throwable.getClass().getSimpleName() + "): using default response.");
    }

    // ------------------------------------------------------------------
    // Retry
    // ------------------------------------------------------------------

    @Retry(name = "reviewsRetry", fallbackMethod = "retryFallback")
    public ReviewsOutcome getReviewsWithRetry(String productId) {
        return ReviewsOutcome.ok(productId, reviewsClient.fetchReviews(productId));
    }

    private ReviewsOutcome retryFallback(String productId, Throwable throwable) {
        log.warn("Retry 'reviewsRetry' esgotou as tentativas para productId={}: {}",
                productId, throwable.toString());
        return ReviewsOutcome.fallback(productId,
                "Tentativas de retry esgotadas (" + throwable.getClass().getSimpleName() + ").");
    }

    // ------------------------------------------------------------------
    // Rate Limiter
    // ------------------------------------------------------------------

    @RateLimiter(name = "reviewsRateLimiter", fallbackMethod = "rateLimiterFallback")
    public ReviewsOutcome getReviewsWithRateLimiter(String productId) {
        return ReviewsOutcome.ok(productId, reviewsClient.fetchReviews(productId));
    }

    private ReviewsOutcome rateLimiterFallback(String productId, Throwable throwable) {
        log.warn("Rate Limiter 'reviewsRateLimiter' rejeitou a chamada para productId={}: {}",
                productId, throwable.toString());
        return ReviewsOutcome.fallback(productId,
                "Limite de requisições excedido (" + throwable.getClass().getSimpleName() + ").");
    }

    // ------------------------------------------------------------------
    // Bulkhead
    // ------------------------------------------------------------------

    @Bulkhead(name = "reviewsBulkhead", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "bulkheadFallback")
    public ReviewsOutcome getReviewsWithBulkhead(String productId) {
        return ReviewsOutcome.ok(productId, reviewsClient.fetchReviews(productId));
    }

    private ReviewsOutcome bulkheadFallback(String productId, Throwable throwable) {
        log.warn("Bulkhead 'reviewsBulkhead' está cheio, rejeitando chamada para productId={}: {}",
                productId, throwable.toString());
        return ReviewsOutcome.fallback(productId,
                "Capacidade concorrente do bulkhead esgotada (" + throwable.getClass().getSimpleName() + ").");
    }

    // ------------------------------------------------------------------
    // Time Limiter
    // ------------------------------------------------------------------
    // O @TimeLimiter do Resilience4j só funciona sobre métodos que retornam
    // CompletionStage/CompletableFuture, por isso a chamada é despachada
    // para um executor dedicado (timeLimiterExecutor).

    @TimeLimiter(name = "reviewsTimeLimiter", fallbackMethod = "timeLimiterFallback")
    public CompletableFuture<ReviewsOutcome> getReviewsWithTimeLimiter(String productId) {
        return CompletableFuture.supplyAsync(
                () -> ReviewsOutcome.ok(productId, reviewsClient.fetchReviews(productId)),
                timeLimiterExecutor);
    }

    private CompletableFuture<ReviewsOutcome> timeLimiterFallback(String productId, Throwable throwable) {
        log.warn("Time Limiter 'reviewsTimeLimiter' interrompeu a chamada para productId={}: {}",
                productId, throwable.toString());
        return CompletableFuture.completedFuture(ReviewsOutcome.fallback(productId,
                "Tempo limite excedido (" + throwable.getClass().getSimpleName() + ")."));
    }
}
