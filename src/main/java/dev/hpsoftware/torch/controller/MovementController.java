package dev.hpsoftware.torch.controller;

import dev.hpsoftware.torch.dto.CreateMovementRequest;
import dev.hpsoftware.torch.dto.MovementResponse;
import dev.hpsoftware.torch.service.MovementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/products/{productId}/movements")
public class MovementController {

    private final MovementService movementService;

    public MovementController(MovementService movementService) {
        this.movementService = movementService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MovementResponse recordMovement(@PathVariable Long productId,
            @Valid @RequestBody CreateMovementRequest request) {
        return movementService.recordMovement(productId, request);
    }

}
