package dev.joseignacio.similar.domain.port.in;

import dev.joseignacio.similar.domain.model.Product;
import reactor.core.publisher.Mono;

import java.util.List;

public interface GetSimilarProductsUseCase {

    Mono<List<Product>> getSimilarProducts(String productId);
}
