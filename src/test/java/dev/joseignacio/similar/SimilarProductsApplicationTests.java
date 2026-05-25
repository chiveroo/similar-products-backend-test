package dev.joseignacio.similar;

import dev.joseignacio.similar.application.port.out.FindSimilarIdsPort;
import dev.joseignacio.similar.application.port.out.LoadProductPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class SimilarProductsApplicationTests {

    // Outbound port implementations land in task #8 (HTTP adapter).
    // Until then, stub beans let the context boot for this smoke test.
    @MockitoBean
    private FindSimilarIdsPort findSimilarIdsPort;

    @MockitoBean
    private LoadProductPort loadProductPort;

    @Test
    void contextLoads() {
    }
}
