package dev.joseignacio.similar.adapter.out.http;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.joseignacio.similar.application.domain.exception.ProductNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class ProductHttpAdapterTest {

    @RegisterExtension
    static final WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private ProductHttpAdapter adapter;

    @BeforeEach
    void setUp() {
        WebClient webClient = WebClient.builder().baseUrl(wireMock.baseUrl()).build();
        adapter = new ProductHttpAdapter(webClient);
    }

    @Test
    void findSimilarIds_returnsParsedList() {
        wireMock.stubFor(get(urlEqualTo("/product/1/similarids"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[\"2\",\"3\",\"4\"]")));

        StepVerifier.create(adapter.findSimilarIds("1"))
                .assertNext(ids -> assertThat(ids).containsExactly("2", "3", "4"))
                .verifyComplete();
    }

    @Test
    void findSimilarIds_throwsProductNotFoundOn404() {
        wireMock.stubFor(get(urlEqualTo("/product/999/similarids"))
                .willReturn(aResponse().withStatus(404)));

        StepVerifier.create(adapter.findSimilarIds("999"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ProductNotFoundException.class)
                        .hasMessageContaining("999"))
                .verify();
    }

    @Test
    void findSimilarIds_propagatesUpstreamServerError() {
        wireMock.stubFor(get(urlEqualTo("/product/6/similarids"))
                .willReturn(aResponse().withStatus(500)));

        StepVerifier.create(adapter.findSimilarIds("6"))
                .expectError(WebClientResponseException.InternalServerError.class)
                .verify();
    }

    @Test
    void loadProduct_returnsMappedDomainProduct() {
        wireMock.stubFor(get(urlEqualTo("/product/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"1","name":"Shirt","price":9.99,"availability":true}
                                """)));

        StepVerifier.create(adapter.loadProduct("1"))
                .assertNext(product -> {
                    assertThat(product.id()).isEqualTo("1");
                    assertThat(product.name()).isEqualTo("Shirt");
                    assertThat(product.price()).isEqualByComparingTo(new BigDecimal("9.99"));
                    assertThat(product.availability()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void loadProduct_throwsProductNotFoundOn404() {
        wireMock.stubFor(get(urlEqualTo("/product/5"))
                .willReturn(aResponse().withStatus(404)));

        StepVerifier.create(adapter.loadProduct("5"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ProductNotFoundException.class)
                        .hasMessageContaining("5"))
                .verify();
    }

    @Test
    void loadProduct_propagatesUpstreamServerError() {
        wireMock.stubFor(get(urlEqualTo("/product/6"))
                .willReturn(aResponse().withStatus(500)));

        StepVerifier.create(adapter.loadProduct("6"))
                .expectError(WebClientResponseException.InternalServerError.class)
                .verify();
    }
}
