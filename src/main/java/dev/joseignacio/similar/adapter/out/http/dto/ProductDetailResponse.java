package dev.joseignacio.similar.adapter.out.http.dto;

import dev.joseignacio.similar.application.domain.model.Product;

import java.math.BigDecimal;

public record ProductDetailResponse(String id, String name, BigDecimal price, boolean availability) {

    public Product toDomain() {
        return new Product(id, name, price, availability);
    }
}
