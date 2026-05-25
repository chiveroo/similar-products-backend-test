package dev.joseignacio.similar.application.port.out;

import reactor.core.publisher.Mono;

import java.util.List;

public interface FindSimilarIdsPort {

    Mono<List<String>> findSimilarIds(String productId);
}
