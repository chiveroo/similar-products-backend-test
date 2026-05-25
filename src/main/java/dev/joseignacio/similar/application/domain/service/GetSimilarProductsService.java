package dev.joseignacio.similar.application.domain.service;

import dev.joseignacio.similar.application.domain.model.Product;
import dev.joseignacio.similar.application.port.in.GetSimilarProductsUseCase;
import dev.joseignacio.similar.application.port.out.FindSimilarIdsPort;
import dev.joseignacio.similar.application.port.out.LoadProductPort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
class GetSimilarProductsService implements GetSimilarProductsUseCase {

    private static final int CONCURRENCY = 10;

    private final FindSimilarIdsPort findSimilarIdsPort;
    private final LoadProductPort loadProductPort;

    GetSimilarProductsService(FindSimilarIdsPort findSimilarIdsPort,
                              LoadProductPort loadProductPort) {
        this.findSimilarIdsPort = findSimilarIdsPort;
        this.loadProductPort = loadProductPort;
    }

    @Override
    public Mono<List<Product>> getSimilarProducts(String productId) {
        return findSimilarIdsPort.findSimilarIds(productId)
                .flatMapMany(Flux::fromIterable)
                .distinct()
                .flatMapSequential(this::loadIgnoringIndividualFailures, CONCURRENCY)
                .collectList();
    }

    private Mono<Product> loadIgnoringIndividualFailures(String productId) {
        return loadProductPort.loadProduct(productId).onErrorResume(e -> Mono.empty());
    }
}
