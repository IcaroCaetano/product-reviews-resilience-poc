package com.project.product_reviews_resilience_poc.client;

/**
 * Exceção lançada pelo {@link ReviewsClient} quando a chamada à Reviews API
 * falha (erro HTTP, timeout de conexão/leitura, etc).
 *
 * <p>É esta exceção que os módulos Resilience4j (Circuit Breaker e Retry)
 * estão configurados para reconhecer via {@code recordExceptions} /
 * {@code retryExceptions} no application.yaml.</p>
 */
public class ReviewsClientException extends RuntimeException {

    public ReviewsClientException(String message) {
        super(message);
    }

    public ReviewsClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
