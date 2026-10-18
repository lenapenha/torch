package dev.hpsoftware.torch.service;

import dev.hpsoftware.torch.dto.CreateMovementRequest;
import dev.hpsoftware.torch.dto.MovementResponse;
import dev.hpsoftware.torch.enums.MovementType;
import dev.hpsoftware.torch.exception.InsufficientStockException;
import dev.hpsoftware.torch.exception.ResourceNotFoundException;
import dev.hpsoftware.torch.model.Product;
import dev.hpsoftware.torch.model.StockMovement;
import dev.hpsoftware.torch.repository.ProductRepository;
import dev.hpsoftware.torch.repository.StockMovementRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public class MovementService {

    private final ProductRepository productRepository;
    private final StockMovementRepository movementRepository;
    private final Clock clock;

    public MovementService(ProductRepository productRepository,
                           StockMovementRepository movementRepository,
                           Clock clock) {
        this.productRepository = productRepository;
        this.movementRepository = movementRepository;
        this.clock = clock;
    }

    @Transactional
    public MovementResponse recordMovement(Long productId, CreateMovementRequest request) {
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product with id " + productId + " not found"));

        // RECEIPT adds stock, ISSUE removes it; TODO: ADJUSTMENT is deferred
        int delta = request.type() == MovementType.ISSUE
                ? -request.quantity()
                : request.quantity();

        //before changing the balance, check whether it would go negative
        if (product.getQuantityOnHand() + delta < 0) {
            throw new InsufficientStockException(
                "Insufficient stock: requested " + request.quantity()
                        + ", available " + product.getQuantityOnHand());
        }

        product.applyDelta(delta);

        StockMovement movement = movementRepository.save(new StockMovement(
                product,
                request.type(),
                delta,
                product.getQuantityOnHand(),
                request.note(),
                clock.instant()));

        return MovementResponse.from(movement);
    }
}
