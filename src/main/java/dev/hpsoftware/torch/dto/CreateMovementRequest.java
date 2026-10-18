package dev.hpsoftware.torch.dto;

import dev.hpsoftware.torch.enums.MovementType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateMovementRequest(
    @NotNull
    MovementType type,

    @NotNull @Positive
    Integer quantity,

    @Size(max = 500)
    String note
) {}
