package com.project.product_reviews_resilience_poc.reviews;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Reviews API: serviço "remoto" (dependência) que a Product API consulta.
 *
 * <p>Além do endpoint "normal", este controller expõe parâmetros opcionais
 * {@code delayMs} e {@code fail} que permitem, numa demo em tempo real, forçar
 * lentidão ou erro na Reviews API para disparar manualmente o Circuit Breaker,
 * o Retry, o Rate Limiter, o Bulkhead ou o Time Limiter configurados na
 * Product API — sem precisar de ferramentas externas.</p>
 *
 * <p>Nos testes de integração automatizados, esse comportamento é simulado
 * via WireMock (ver {@code app.reviews.base-url}), então este controller não
 * é exercitado diretamente pelos testes de resiliência.</p>
 */
@RestController
public class ReviewsController {

    @GetMapping("/api/v1/reviews/{productId}")
    public ResponseEntity<List<Review>> getReviews(
            @PathVariable String productId,
            @RequestParam(name = "delayMs", defaultValue = "0") long delayMs,
            @RequestParam(name = "fail", defaultValue = "false") boolean fail) {

        if (delayMs > 0) {
            sleep(delayMs);
        }

        if (fail) {
            return ResponseEntity.status(503).build();
        }

        List<Review> reviews = List.of(
                new Review("r1", productId, "ana", 5, "Excelente produto, recomendo!"),
                new Review("r2", productId, "bruno", 4, "Bom custo-benefício."),
                new Review("r3", productId, "carla", 3, "Atendeu as expectativas.")
        );
        return ResponseEntity.ok(reviews);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrompido ao simular latência", e);
        }
    }
}
