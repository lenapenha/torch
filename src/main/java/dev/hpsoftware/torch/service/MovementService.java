package dev.hpsoftware.torch.service;

import dev.hpsoftware.torch.dto.CreateMovementRequest;
import dev.hpsoftware.torch.dto.MovementResponse;
import dev.hpsoftware.torch.dto.PageResponse;
import dev.hpsoftware.torch.enums.MovementType;
import dev.hpsoftware.torch.exception.InsufficientStockException;
import dev.hpsoftware.torch.exception.ResourceNotFoundException;
import dev.hpsoftware.torch.model.Product;
import dev.hpsoftware.torch.model.StockMovement;
import dev.hpsoftware.torch.repository.ProductRepository;
import dev.hpsoftware.torch.repository.StockMovementRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

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
        return recordMovement(productId, request, clock.instant());
    }

    // stamps the movement with the given instant instead of the clock's;
    // used by the seed loader to back-date history (the REST API can't back-date)
    @Transactional
    public MovementResponse recordMovement(Long productId, CreateMovementRequest request, Instant at) {
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
                at));

        return MovementResponse.from(movement);
    }


    public PageResponse<MovementResponse> listMovements(Long productId, int page, int size) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product with id " + productId + " not found");
        }

        Page<StockMovement> result = movementRepository
                .findByProductIdOrderByCreatedAtDescIdDesc(productId, PageRequest.of(page, size));

        return new PageResponse<>(
                result.getContent().stream().map(MovementResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }
}
