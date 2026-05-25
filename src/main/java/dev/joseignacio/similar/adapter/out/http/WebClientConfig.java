package dev.joseignacio.similar.adapter.out.http;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(ExternalApiProperties.class)
class WebClientConfig {

    @Bean
    WebClient productWebClient(WebClient.Builder builder, ExternalApiProperties properties) {
        return builder.baseUrl(properties.baseUrl()).build();
    }
}
