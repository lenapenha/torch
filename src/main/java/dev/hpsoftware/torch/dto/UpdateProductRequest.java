package dev.hpsoftware.torch.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateProductRequest (
    @NotBlank @Size(max = 200)
    String name,

    @NotBlank @Size(max = 1000)
    String description,

    @NotBlank @Size(max = 32)
    String unit,

    @NotNull @Min(0)
    Integer reorderLevel
) {}
