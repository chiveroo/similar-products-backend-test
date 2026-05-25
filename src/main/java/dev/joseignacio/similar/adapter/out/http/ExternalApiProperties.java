package dev.joseignacio.similar.adapter.out.http;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external-api")
public record ExternalApiProperties(String baseUrl) {}
