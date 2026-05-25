package dev.joseignacio.similar.application.domain.model;

import java.math.BigDecimal;

public record Product(String id, String name, BigDecimal price, boolean availability) {}
