package dev.hpsoftware.torch.dto;

import dev.hpsoftware.torch.model.Product;

import java.time.Instant;

public record ProductResponse (
        Long id,
        String sku,
        String name,
        String description,
        String unit,
        int reorderLevel,
        int quantityOnHand,
        boolean active,
        Instant createdAt
        ) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getUnit(),
                product.getReorderLevel(),
                product.getQuantityOnHand(),
                product.isActive(),
                product.getCreatedAt());
    }
}
