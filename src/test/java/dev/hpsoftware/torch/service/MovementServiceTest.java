package dev.hpsoftware.torch.service;

import dev.hpsoftware.torch.dto.CreateMovementRequest;
import dev.hpsoftware.torch.dto.MovementResponse;
import dev.hpsoftware.torch.enums.MovementType;
import dev.hpsoftware.torch.exception.InsufficientStockException;
import dev.hpsoftware.torch.exception.ResourceNotFoundException;
import dev.hpsoftware.torch.model.Product;
import dev.hpsoftware.torch.repository.ProductRepository;
import dev.hpsoftware.torch.repository.StockMovementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MovementServiceTest {

    @Mock
    ProductRepository productRepository;

    @Mock
    StockMovementRepository movementRepository;

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private MovementService service() {
        return new MovementService(productRepository, movementRepository, clock);
    }

    private Product newProduct() {
        return new Product("SKU-1", "Widget", "A widget", "pcs", 5, NOW);
    }

    @Test
    void receiptIncreasesStockAndRecordsMovement() {
        Product product = newProduct();
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(movementRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MovementResponse response = service().recordMovement(1L,
                new CreateMovementRequest(MovementType.RECEIPT, 25, "Initial stock"));

        assertThat(response.type()).isEqualTo(MovementType.RECEIPT);
        assertThat(response.quantityDelta()).isEqualTo(25);
        assertThat(response.balanceAfter()).isEqualTo(25);
        assertThat(response.note()).isEqualTo("Initial stock");
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(product.getQuantityOnHand()).isEqualTo(25);
    }

    @Test
    void secondReceiptAddsToExistingBalance() {
        Product product = newProduct();
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(movementRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service().recordMovement(1L, new CreateMovementRequest(MovementType.RECEIPT, 10, null));
        MovementResponse second = service().recordMovement(1L,
                new CreateMovementRequest(MovementType.RECEIPT, 5, null));

        assertThat(second.balanceAfter()).isEqualTo(15);
        assertThat(product.getQuantityOnHand()).isEqualTo(15);
    }

    @Test
    void throwsNotFoundWhenProductDoesNotExist() {
        when(productRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().recordMovement(99L,
                new CreateMovementRequest(MovementType.RECEIPT, 5, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product with id 99 not found");

        verify(movementRepository, never()).save(any());
    }

    @Test
    void issueDecreasesStockAndRecordsNegativeDelta() {
        Product product = newProduct();
        product.applyDelta(25);
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(movementRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MovementResponse response = service().recordMovement(1L,
                new CreateMovementRequest(MovementType.ISSUE, 10, "Sold"));

        assertThat(response.type()).isEqualTo(MovementType.ISSUE);
        assertThat(response.quantityDelta()).isEqualTo(-10);
        assertThat(response.balanceAfter()).isEqualTo(15);
        assertThat(product.getQuantityOnHand()).isEqualTo(15);
    }

    @Test
    void issueOfExactlyAvailableStockLeavesZero() {
        Product product = newProduct();
        product.applyDelta(10);
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(movementRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MovementResponse response = service().recordMovement(1L,
                new CreateMovementRequest(MovementType.ISSUE, 10, null));

        assertThat(response.balanceAfter()).isZero();
        assertThat(product.getQuantityOnHand()).isZero();
    }

    @Test
    void issueBeyondAvailableStockIsRejectedAndLeavesStockUnchanged() {
        Product product = newProduct();
        product.applyDelta(25);
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service().recordMovement(1L,
                new CreateMovementRequest(MovementType.ISSUE, 30, null)))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Insufficient stock: requested 30, available 25");

        assertThat(product.getQuantityOnHand()).isEqualTo(25);
        verify(movementRepository, never()).save(any());
    }

}
