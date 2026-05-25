package dev.joseignacio.similar.adapter.in.web;

import dev.joseignacio.similar.adapter.in.web.dto.ProductResponse;
import dev.joseignacio.similar.application.port.in.GetSimilarProductsUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/product")
class ProductController {

    private final GetSimilarProductsUseCase getSimilarProductsUseCase;

    ProductController(GetSimilarProductsUseCase getSimilarProductsUseCase) {
        this.getSimilarProductsUseCase = getSimilarProductsUseCase;
    }

    @GetMapping("/{productId}/similar")
    Mono<List<ProductResponse>> getSimilarProducts(@PathVariable String productId) {
        return getSimilarProductsUseCase.getSimilarProducts(productId)
                .map(products -> products.stream().map(ProductResponse::from).toList());
    }
}
