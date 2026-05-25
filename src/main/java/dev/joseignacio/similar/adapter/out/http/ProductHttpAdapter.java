package dev.joseignacio.similar.adapter.out.http;

import dev.joseignacio.similar.adapter.out.http.dto.ProductDetailResponse;
import dev.joseignacio.similar.application.domain.exception.ProductNotFoundException;
import dev.joseignacio.similar.application.domain.model.Product;
import dev.joseignacio.similar.application.port.out.FindSimilarIdsPort;
import dev.joseignacio.similar.application.port.out.LoadProductPort;
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

    ProductHttpAdapter(WebClient productWebClient) {
        this.webClient = productWebClient;
    }

    @Override
    public Mono<List<String>> findSimilarIds(String productId) {
        return webClient.get()
                .uri("/product/{id}/similarids", productId)
                .retrieve()
                .bodyToMono(STRING_LIST)
                .onErrorMap(WebClientResponseException.NotFound.class,
                        e -> new ProductNotFoundException(productId));
    }

    @Override
    public Mono<Product> loadProduct(String productId) {
        return webClient.get()
                .uri("/product/{id}", productId)
                .retrieve()
                .bodyToMono(ProductDetailResponse.class)
                .map(ProductDetailResponse::toDomain)
                .onErrorMap(WebClientResponseException.NotFound.class,
                        e -> new ProductNotFoundException(productId));
    }
}
