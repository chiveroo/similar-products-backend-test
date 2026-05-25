package dev.joseignacio.similar.domain.port.out;

import dev.joseignacio.similar.domain.model.Product;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ProductRepository {

    Mono<List<String>> findSimilarIds(String productId);

    Mono<Product> findById(String productId);
}
