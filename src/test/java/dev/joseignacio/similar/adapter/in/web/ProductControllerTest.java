package dev.joseignacio.similar.adapter.in.web;

import dev.joseignacio.similar.application.domain.exception.ProductNotFoundException;
import dev.joseignacio.similar.application.domain.model.Product;
import dev.joseignacio.similar.application.port.in.GetSimilarProductsUseCase;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.mockito.Mockito.when;

@WebFluxTest(controllers = ProductController.class)
@Import(GlobalErrorHandler.class)
class ProductControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private GetSimilarProductsUseCase getSimilarProductsUseCase;

    @Test
    void returns200WithSimilarProductsList() {
        when(getSimilarProductsUseCase.getSimilarProducts("1"))
                .thenReturn(Mono.just(List.of(product("2"), product("3"))));

        webTestClient.get().uri("/product/1/similar")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].id").isEqualTo("2")
                .jsonPath("$[0].name").isEqualTo("Product 2")
                .jsonPath("$[0].price").isEqualTo(9.99)
                .jsonPath("$[0].availability").isEqualTo(true)
                .jsonPath("$[1].id").isEqualTo("3");
    }

    @Test
    void returns200WithEmptyArrayWhenNoSimilars() {
        when(getSimilarProductsUseCase.getSimilarProducts("1"))
                .thenReturn(Mono.just(List.of()));

        webTestClient.get().uri("/product/1/similar")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$.length()").isEqualTo(0);
    }

    @Test
    void returns404WhenBaseProductDoesNotExist() {
        when(getSimilarProductsUseCase.getSimilarProducts("999"))
                .thenReturn(Mono.error(new ProductNotFoundException("999")));

        webTestClient.get().uri("/product/999/similar")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.message").isEqualTo("Product not found: 999");
    }

    @Test
    void returns503WhenCircuitBreakerIsOpen() {
        CircuitBreaker openCircuit = CircuitBreaker.of("test",
                CircuitBreakerConfig.custom().minimumNumberOfCalls(1).build());
        openCircuit.transitionToOpenState();
        CallNotPermittedException circuitOpen =
                CallNotPermittedException.createCallNotPermittedException(openCircuit);

        when(getSimilarProductsUseCase.getSimilarProducts("1"))
                .thenReturn(Mono.error(circuitOpen));

        webTestClient.get().uri("/product/1/similar")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.message").isEqualTo("Upstream temporarily unavailable");
    }

    @Test
    void returns504WhenUpstreamTimesOut() {
        when(getSimilarProductsUseCase.getSimilarProducts("1"))
                .thenReturn(Mono.error(new TimeoutException("upstream too slow")));

        webTestClient.get().uri("/product/1/similar")
                .exchange()
                .expectStatus().isEqualTo(504)
                .expectBody()
                .jsonPath("$.status").isEqualTo(504)
                .jsonPath("$.message").isEqualTo("Upstream did not respond in time");
    }

    @Test
    void returns500OnUnexpectedUseCaseError() {
        when(getSimilarProductsUseCase.getSimilarProducts("boom"))
                .thenReturn(Mono.error(new RuntimeException("upstream blew up")));

        webTestClient.get().uri("/product/boom/similar")
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.status").isEqualTo(500)
                .jsonPath("$.message").isEqualTo("Internal server error");
    }

    private static Product product(String id) {
        return new Product(id, "Product " + id, new BigDecimal("9.99"), true);
    }
}
