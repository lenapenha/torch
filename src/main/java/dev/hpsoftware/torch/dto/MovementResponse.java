package dev.hpsoftware.torch.dto;

import dev.hpsoftware.torch.enums.MovementType;
import dev.hpsoftware.torch.model.StockMovement;

import java.time.Instant;

public record MovementResponse(
    Long id, Long productId,
    MovementType type,
    int quantityDelta,
    int balanceAfter,
    String note,
    Instant createdAt
) {

    public static MovementResponse from(StockMovement movement) {
        return new MovementResponse(
                movement.getId(),
                movement.getProduct().getId(),
                movement.getType(),
                movement.getQuantityDelta(),
                movement.getBalanceAfter(),
                movement.getNote(),
                movement.getCreatedAt());
    }
}
