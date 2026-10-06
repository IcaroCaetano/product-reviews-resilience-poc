package com.project.product_reviews_resilience_poc.reviews;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
