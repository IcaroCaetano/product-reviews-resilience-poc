# Poc Product reviews resilience

POC para apresentação sobre **Resilience4j** com Spring Boot. Simula uma
Product API que depende de uma Reviews API, e aplica os 5 módulos do
Resilience4j — via **anotações** e configuração em `application.yaml` — na
chamada entre as duas:

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

## Arquitetura

Tudo roda num único processo Spring Boot:

- **Reviews API** (`reviews` package) — `ReviewsController` expõe
  `GET /api/v1/reviews/{productId}`, com parâmetros opcionais `delayMs` e
  `fail` para forçar lentidão/erro manualmente numa demo em tempo real.
- **Product API** (`product` package) — `ProductReviewsController` expõe um
  endpoint dedicado por módulo do Resilience4j, cada um delegando para um
  método de `ProductReviewsResilienceService` decorado com **exatamente uma**
  anotação Resilience4j + um `fallbackMethod`:

  | Endpoint | Módulo | Anotação |
  |---|---|---|
  | `GET /api/v1/products/{id}/reviews/circuit-breaker` | Circuit Breaker | `@CircuitBreaker` |
  | `GET /api/v1/products/{id}/reviews/retry` | Retry | `@Retry` |
  | `GET /api/v1/products/{id}/reviews/rate-limiter` | Rate Limiter | `@RateLimiter` |
  | `GET /api/v1/products/{id}/reviews/bulkhead` | Bulkhead | `@Bulkhead` |
  | `GET /api/v1/products/{id}/reviews/time-limiter` | Time Limiter | `@TimeLimiter` |

- **client package** — `ReviewsClient` é um client HTTP "cru" (usando
  `RestClient`) sem nenhuma anotação de resiliência; as anotações ficam todas
  na camada de serviço da Product API, mantendo cada endpoint isolado para a
  demonstração.

Toda a configuração dos 5 módulos (thresholds, janelas, timeouts) está em
`src/main/resources/application.yaml`, sob `resilience4j.*`.

## Rodando a aplicação

```bash
./gradlew bootRun
```

A app sobe em `http://localhost:8080`. Como é um único processo, a Product
API chama a Reviews API nela mesma (`app.reviews.base-url=http://localhost:8080`).

### Demo manual de cada módulo (sem precisar dos testes automatizados)

Use os parâmetros `delayMs`/`fail` do endpoint da Reviews API combinados com
as chamadas da Product API para forçar cada padrão:

**Circuit Breaker** — force erro repetido e veja o circuito abrir
(`minimumNumberOfCalls=5`, `failureRateThreshold=50%`):
```bash
# Simule a Reviews API fora do ar trocando temporariamente o parâmetro fail=true
# direto no endpoint da Reviews API, ou aponte app.reviews.base-url para uma porta inexistente.
for i in {1..6}; do curl -s "http://localhost:8080/api/v1/products/p1/reviews/circuit-breaker"; echo; done
curl -s http://localhost:8080/actuator/circuitbreakers | jq
```

**Retry** — veja no log (`logging.level.com.project...=DEBUG`) as tentativas:
```bash
curl -s "http://localhost:8080/api/v1/products/p1/reviews/retry"
```

**Rate Limiter** — dispare mais de 2 chamadas rápidas (limite da janela):
```bash
for i in {1..3}; do curl -s "http://localhost:8080/api/v1/products/p1/reviews/rate-limiter"; echo; done
```

**Bulkhead** — dispare chamadas concorrentes (capacidade = 2):
```bash
for i in {1..5}; do curl -s "http://localhost:8080/api/v1/products/p1/reviews/bulkhead" & done; wait
```

**Time Limiter** — faça a Reviews API demorar mais que 1500ms:
```bash
# chamando a Reviews API diretamente com delay, para inspecionar o comportamento isolado:
curl -s "http://localhost:8080/api/v1/reviews/p1?delayMs=3000"
# via Product API (o endpoint da Product API não aceita delayMs; para demo
# ponta a ponta, use os testes de integração, que já simulam a latência via WireMock)
curl -s "http://localhost:8080/api/v1/products/p1/reviews/time-limiter"
```

Endpoints úteis do Actuator para a apresentação: `/actuator/health`,
`/actuator/circuitbreakers`, `/actuator/circuitbreakerevents`,
`/actuator/ratelimiters`, `/actuator/bulkheads`, `/actuator/retries`.

## Testes de integração

Cada módulo tem sua própria classe de teste de integração
(`src/test/java/.../product/*IntegrationTest.java`), subindo o contexto
Spring completo (`@SpringBootTest(webEnvironment = RANDOM_PORT)`) e usando
**WireMock** para simular a Reviews API (falhas, latência, recuperação após
algumas tentativas) — sem depender da `ReviewsController` real, então cada
teste controla exatamente o comportamento do "backend" que quer provocar.

Rodar todos os testes:
```bash
./gradlew test
```

Rodar um módulo específico:
```bash
./gradlew test --tests "*.CircuitBreakerIntegrationTest"
./gradlew test --tests "*.RetryIntegrationTest"
./gradlew test --tests "*.RateLimiterIntegrationTest"
./gradlew test --tests "*.BulkheadIntegrationTest"
./gradlew test --tests "*.TimeLimiterIntegrationTest"
```

Relatório HTML gerado em `build/reports/tests/test/index.html`.

O que cada teste comprova:

- **CircuitBreakerIntegrationTest** — com a Reviews API saudável, o circuito
  fica `CLOSED` e os dados reais são retornados; com falhas repetidas, o
  circuito abre (`OPEN`) após o número mínimo de chamadas configurado, e as
  chamadas seguintes são rejeitadas localmente (sem nem chegar à Reviews
  API), caindo no fallback.
- **RetryIntegrationTest** — com um cenário stateful do WireMock (falha,
  falha, sucesso), o Retry tenta novamente e devolve o resultado real na
  3ª tentativa; com falha persistente, esgota as `maxAttempts` configuradas
  e cai no fallback.
- **RateLimiterIntegrationTest** — das 3 chamadas feitas na mesma janela,
  apenas as 2 primeiras (limite configurado) são permitidas; a 3ª é
  rejeitada imediatamente (`RequestNotPermitted`) e cai no fallback.
- **BulkheadIntegrationTest** — 5 chamadas concorrentes contra uma Reviews
  API com latência artificial; como a capacidade concorrente configurada é
  2, pelo menos uma chamada é rejeitada (`BulkheadFullException`) e cai no
  fallback.
- **TimeLimiterIntegrationTest** — com a Reviews API respondendo dentro do
  timeout configurado, o resultado real é retornado; com latência maior que
  o timeout, a chamada é interrompida (`TimeoutException`) e cai no
  fallback — mesmo com o timeout do cliente HTTP configurado bem mais alto.

## Observações sobre as versões

Este projeto usa Spring Boot `4.1.1` e Java `25` (toolchain do Gradle). As
dependências do Resilience4j (`resilience4j-spring-boot3`) e do WireMock
(`wiremock-standalone`) foram fixadas em versões estáveis conhecidas no
momento da escrita; se o Gradle não conseguir resolvê-las (ou houver
incompatibilidade de major version com o Spring Boot usado), ajuste as
versões em `build.gradle` (bloco `ext`) para as mais recentes disponíveis no
Maven Central para a sua combinação de Spring Boot/Java.
