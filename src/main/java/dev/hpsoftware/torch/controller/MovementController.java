package dev.hpsoftware.torch.controller;

import dev.hpsoftware.torch.dto.CreateMovementRequest;
import dev.hpsoftware.torch.dto.MovementResponse;
import dev.hpsoftware.torch.dto.PageResponse;
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

    @GetMapping
    public PageResponse<MovementResponse> listMovements(@PathVariable Long productId,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return movementService.listMovements(productId, page, size);
    }
}
