package dev.hpsoftware.torch.service;

import dev.hpsoftware.torch.dto.CreateProductRequest;
import dev.hpsoftware.torch.dto.ProductResponse;
import dev.hpsoftware.torch.exception.DuplicateSkuException;
import dev.hpsoftware.torch.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    @Mock
    ProductRepository productRepository;

    private static final Clock clock =
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private ProductService service () {
        return new ProductService(productRepository, clock);
    }

    @Test
    void createProductStartsWithZeroStockAndClockTimestamp() {

        when(productRepository.existsBySku("SKU-1")).thenReturn(false);
        when(productRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ProductResponse response = service().createProduct(request());

        assertThat(response.sku()).isEqualTo("SKU-1");
        assertThat(response.quantityOnHand()).isZero();
        assertThat(response.active()).isTrue();
        assertThat(response.createdAt()).isEqualTo(clock.instant());
    }

    @Test
    void createProductFailsWhenSkuAlreadyExists() {
        when(productRepository.existsBySku("SKU-1")).thenReturn(true);

        assertThatThrownBy(() -> service().createProduct(request()))
            .isInstanceOf(DuplicateSkuException.class);

        verify(productRepository, never()).save(any());
    }


    private CreateProductRequest request() {
        return new CreateProductRequest("SKU-1", "Widget", "A widget", "pcs", 5);
    }
}
