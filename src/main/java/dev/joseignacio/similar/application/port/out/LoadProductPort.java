package dev.joseignacio.similar.application.port.out;

import dev.joseignacio.similar.application.domain.model.Product;
import reactor.core.publisher.Mono;

public interface LoadProductPort {

    Mono<Product> loadProduct(String productId);
}
