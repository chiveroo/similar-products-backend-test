package dev.joseignacio.similar.adapter.out.http;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "external-api")
public record ExternalApiProperties(String baseUrl, Duration cacheTtl, long cacheMaxSize) {}
