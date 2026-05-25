package dev.joseignacio.similar.adapter.out.http;

import com.github.benmanes.caffeine.cache.AsyncCache;
import dev.joseignacio.similar.adapter.out.http.dto.ProductDetailResponse;
import dev.joseignacio.similar.application.domain.exception.ProductNotFoundException;
import dev.joseignacio.similar.application.domain.model.Product;
import dev.joseignacio.similar.application.port.out.FindSimilarIdsPort;
import dev.joseignacio.similar.application.port.out.LoadProductPort;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.timelimiter.TimeLimiter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
class ProductHttpAdapter implements FindSimilarIdsPort, LoadProductPort {

    private static final ParameterizedTypeReference<List<String>> STRING_LIST =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final TimeLimiter timeLimiter;
    private final AsyncCache<String, Product> productCache;

    ProductHttpAdapter(WebClient productWebClient,
                       CircuitBreaker productCircuitBreaker,
                       TimeLimiter productTimeLimiter,
                       AsyncCache<String, Product> productCache) {
        this.webClient = productWebClient;
        this.circuitBreaker = productCircuitBreaker;
        this.timeLimiter = productTimeLimiter;
        this.productCache = productCache;
    }

    @Override
    public Mono<List<String>> findSimilarIds(String productId) {
        return webClient.get()
                .uri("/product/{id}/similarids", productId)
                .retrieve()
                .bodyToMono(STRING_LIST)
                .onErrorMap(WebClientResponseException.NotFound.class,
                        e -> new ProductNotFoundException(productId))
                .transformDeferred(TimeLimiterOperator.of(timeLimiter))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
    }

    @Override
    public Mono<Product> loadProduct(String productId) {
        return Mono.fromFuture(
                productCache.get(productId, (id, executor) -> fetchProductFromUpstream(id).toFuture())
        );
    }

    private Mono<Product> fetchProductFromUpstream(String productId) {
        return webClient.get()
                .uri("/product/{id}", productId)
                .retrieve()
                .bodyToMono(ProductDetailResponse.class)
                .map(ProductDetailResponse::toDomain)
                .onErrorMap(WebClientResponseException.NotFound.class,
                        e -> new ProductNotFoundException(productId))
                .transformDeferred(TimeLimiterOperator.of(timeLimiter))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
    }
}
