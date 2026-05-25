package dev.joseignacio.similar.adapter.in.web.dto;

import dev.joseignacio.similar.application.domain.model.Product;

import java.math.BigDecimal;

public record ProductResponse(String id, String name, BigDecimal price, boolean availability) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(product.id(), product.name(), product.price(), product.availability());
    }
}
