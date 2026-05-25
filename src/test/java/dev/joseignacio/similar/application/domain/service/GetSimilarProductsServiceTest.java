package dev.joseignacio.similar.application.domain.service;

import dev.joseignacio.similar.application.domain.exception.ProductNotFoundException;
import dev.joseignacio.similar.application.domain.model.Product;
import dev.joseignacio.similar.application.port.out.FindSimilarIdsPort;
import dev.joseignacio.similar.application.port.out.LoadProductPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSimilarProductsServiceTest {

    @Mock
    private FindSimilarIdsPort findSimilarIdsPort;

    @Mock
    private LoadProductPort loadProductPort;

    @InjectMocks
    private GetSimilarProductsService service;

    @Test
    void returnsSimilarProductsInTheOrderGivenByTheUpstream() {
        when(findSimilarIdsPort.findSimilarIds("1"))
                .thenReturn(Mono.just(List.of("2", "3", "4")));
        when(loadProductPort.loadProduct("2")).thenReturn(Mono.just(product("2")));
        when(loadProductPort.loadProduct("3")).thenReturn(Mono.just(product("3")));
        when(loadProductPort.loadProduct("4")).thenReturn(Mono.just(product("4")));

        StepVerifier.create(service.getSimilarProducts("1"))
                .assertNext(list -> assertThat(list)
                        .extracting(Product::id)
                        .containsExactly("2", "3", "4"))
                .verifyComplete();
    }

    @Test
    void returnsEmptyListWhenThereAreNoSimilarIds() {
        when(findSimilarIdsPort.findSimilarIds("1"))
                .thenReturn(Mono.just(List.of()));

        StepVerifier.create(service.getSimilarProducts("1"))
                .assertNext(list -> assertThat(list).isEmpty())
                .verifyComplete();

        verifyNoInteractions(loadProductPort);
    }

    @Test
    void deduplicatesRepeatedSimilarIdsBeforeLoading() {
        when(findSimilarIdsPort.findSimilarIds("1"))
                .thenReturn(Mono.just(List.of("2", "2", "3")));
        when(loadProductPort.loadProduct("2")).thenReturn(Mono.just(product("2")));
        when(loadProductPort.loadProduct("3")).thenReturn(Mono.just(product("3")));

        StepVerifier.create(service.getSimilarProducts("1"))
                .assertNext(list -> assertThat(list)
                        .extracting(Product::id)
                        .containsExactly("2", "3"))
                .verifyComplete();

        verify(loadProductPort, times(1)).loadProduct("2");
    }

    @Test
    void propagatesErrorWhenTheBaseProductCannotResolveSimilarIds() {
        when(findSimilarIdsPort.findSimilarIds("999"))
                .thenReturn(Mono.error(new ProductNotFoundException("999")));

        StepVerifier.create(service.getSimilarProducts("999"))
                .verifyError(ProductNotFoundException.class);

        verifyNoInteractions(loadProductPort);
    }

    @Test
    void filtersOutSimilarProductsThatFailToLoadIndividually() {
        when(findSimilarIdsPort.findSimilarIds("1"))
                .thenReturn(Mono.just(List.of("2", "3", "4")));
        when(loadProductPort.loadProduct("2")).thenReturn(Mono.just(product("2")));
        when(loadProductPort.loadProduct("3"))
                .thenReturn(Mono.error(new ProductNotFoundException("3")));
        when(loadProductPort.loadProduct("4"))
                .thenReturn(Mono.error(new RuntimeException("upstream blew up")));

        StepVerifier.create(service.getSimilarProducts("1"))
                .assertNext(list -> assertThat(list)
                        .extracting(Product::id)
                        .containsExactly("2"))
                .verifyComplete();
    }

    @Test
    void resolvesSimilarProductsInParallel() {
        when(findSimilarIdsPort.findSimilarIds("1"))
                .thenReturn(Mono.just(List.of("2", "3", "4")));
        when(loadProductPort.loadProduct(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return Mono.delay(Duration.ofSeconds(1)).map(tick -> product(id));
        });

        StepVerifier.withVirtualTime(() -> service.getSimilarProducts("1"))
                .expectSubscription()
                .thenAwait(Duration.ofSeconds(1))
                .assertNext(list -> assertThat(list).hasSize(3))
                .verifyComplete();
    }

    private static Product product(String id) {
        return new Product(id, "Product " + id, new BigDecimal("9.99"), true);
    }
}
