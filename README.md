# Poc Product reviews resilience

````plantuml
             Product API
                  |
                  | HTTP
                  v
             Reviews API
                  |
        ┌─────────┴─────────┐
        │    Resilience4j   │
        │                   │
        │ Circuit Breaker   │
        │ Retry             │
        │ Rate Limiter      │
        │ Bulkhead          │
        │ Time Limiter      │
        └───────────────────┘
````