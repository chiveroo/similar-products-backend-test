package dev.joseignacio.similar.application.port.in;

import dev.joseignacio.similar.application.domain.model.Product;
import reactor.core.publisher.Mono;

import java.util.List;

public interface GetSimilarProductsUseCase {

    Mono<List<Product>> getSimilarProducts(String productId);
}
