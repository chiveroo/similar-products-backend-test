package dev.joseignacio.similar.adapter.out.http;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.joseignacio.similar.application.domain.model.Product;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class CacheConfig {

    @Bean
    AsyncCache<String, Product> productCache(ExternalApiProperties properties) {
        return Caffeine.newBuilder()
                .maximumSize(properties.cacheMaxSize())
                .expireAfterWrite(properties.cacheTtl())
                .buildAsync();
    }
}
