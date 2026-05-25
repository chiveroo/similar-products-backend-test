package dev.joseignacio.similar.adapter.out.http;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ResilienceConfig {

    static final String PRODUCT_DETAILS_INSTANCE = "productDetails";

    @Bean
    CircuitBreaker productCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker(PRODUCT_DETAILS_INSTANCE);
    }

    @Bean
    TimeLimiter productTimeLimiter(TimeLimiterRegistry registry) {
        return registry.timeLimiter(PRODUCT_DETAILS_INSTANCE);
    }
}
