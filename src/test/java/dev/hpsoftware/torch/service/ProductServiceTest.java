package dev.hpsoftware.torch.service;

import dev.hpsoftware.torch.dto.CreateProductRequest;
import dev.hpsoftware.torch.dto.ProductFilter;
import dev.hpsoftware.torch.dto.ProductResponse;
import dev.hpsoftware.torch.dto.UpdateProductRequest;
import dev.hpsoftware.torch.exception.DuplicateSkuException;
import dev.hpsoftware.torch.exception.ResourceNotFoundException;
import dev.hpsoftware.torch.model.Product;
import dev.hpsoftware.torch.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    @Mock
    ProductRepository productRepository;

    private static final Clock clock =
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private ProductService service() {
        return new ProductService(productRepository, clock);
    }

    @Test
    void createProductStartsWithZeroStockAndClockTimestamp() {
        CreateProductRequest request =
                new CreateProductRequest("SKU-1", "Widget", "A widget", "pcs", 5);

        when(productRepository.existsBySku("SKU-1")).thenReturn(false);
        when(productRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ProductResponse response = service().createProduct(request);

        assertThat(response.sku()).isEqualTo("SKU-1");
        assertThat(response.quantityOnHand()).isZero();
        assertThat(response.active()).isTrue();
        assertThat(response.createdAt()).isEqualTo(clock.instant());
    }

    @Test
    void createProductFailsWhenSkuAlreadyExists() {
        CreateProductRequest request =
                new CreateProductRequest("SKU-1", "Widget", "A widget", "pcs", 5);
        when(productRepository.existsBySku("SKU-1")).thenReturn(true);

        assertThatThrownBy(() -> service().createProduct(request))
                .isInstanceOf(DuplicateSkuException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void getProductReturnsProductWhenFound() {
        Product product = new Product("SKU-1", "Widget", "A widget", "pcs", 5, clock.instant());
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        ProductResponse response = service().getProduct(1L);

        assertThat(response.sku()).isEqualTo("SKU-1");
        assertThat(response.quantityOnHand()).isZero();
        assertThat(response.active()).isTrue();
        assertThat(response.createdAt()).isEqualTo(clock.instant());
    }

    @Test
    void getProductReturnsNotFoundWhenNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getProduct(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product with id 99 not found");
    }

    @Test
    void listProductsMapsRepositoryResultsToResponses() {
        Product product1 = new Product("SKU-1", "Widget", "A widget", "pcs", 5, clock.instant());
        Product product2 = new Product("SKU-2", "Widget 2", "A widget 2", "pcs", 5, clock.instant());

        when(productRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(product1, product2));

        List<ProductResponse> result = service().listProducts(skuFilter(null));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).sku()).isEqualTo("SKU-1");
        assertThat(result.get(1).sku()).isEqualTo("SKU-2");
    }

    @Test
    void listProductsBySkuReturnsSingleMatch() {
        Product product = new Product("SKU-1", "Widget", "A widget", "pcs", 5, clock.instant());
        when(productRepository.findBySku("SKU-1")).thenReturn(Optional.of(product));

        List<ProductResponse> result = service().listProducts(skuFilter("SKU-1"));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().sku()).isEqualTo("SKU-1");
        verify(productRepository, never()).findByActiveTrueOrderByNameAsc();
    }

    @Test
    void listProductsBySkuReturnsEmptyListWhenNotFound() {
        when(productRepository.findBySku("NOPE")).thenReturn(Optional.empty());

        assertThat(service().listProducts(skuFilter("NOPE"))).isEmpty();
    }

    @Test
    void listProductsWithBlankSkuFallsBackToFullList() {
        when(productRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of());

        assertThat(service().listProducts(skuFilter("  "))).isEmpty();

        verify(productRepository, never()).findBySku(any());
    }

    private static ProductFilter skuFilter(String sku) {
        return new ProductFilter(sku, null, null);
    }

    @Test
    void listProductsWithQSearchesByText() {
        Product product = new Product("SKU-1", "Blue Widget", "widget", "pcs", 5, clock.instant());
        when(productRepository.search("widget")).thenReturn(List.of(product));

        List<ProductResponse> result = service().listProducts(new
                ProductFilter(null, "widget", null));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Blue Widget");
        verify(productRepository,
                never()).findByActiveTrueOrderByNameAsc();
    }

    @Test
    void listProductsTrimsQBeforeSearching() {
        when(productRepository.search("widget")).thenReturn(List.of());

        service().listProducts(new ProductFilter(null, "  widget ", null));

        verify(productRepository).search("widget");
    }

    @Test
    void listProductsPrefersSkuOverQ() {
        Product product = new Product("SKU-1", "Widget", "A widget", "pcs", 5,
                clock.instant());
        when(productRepository.findBySku("SKU-1")).thenReturn(Optional.of(product));

        List<ProductResponse> result = service().listProducts(new
                ProductFilter("SKU-1", "gadget", null));

        assertThat(result).hasSize(1);
        verify(productRepository, never()).search(any());
    }


    @Test
    void listProductsWithBlankQFallsBackToFullList() {
        when(productRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of());

        assertThat(service().listProducts(new ProductFilter(null, "  ",
                null))).isEmpty();

        verify(productRepository, never()).search(any());
    }

    @Test
    void listProductsWithLowStockUsesLowStockQuery() {
        Product product = new Product("SKU-1", "Widget", "A widget", "pcs", 5, clock.instant());
        when(productRepository.findLowStock()).thenReturn(List.of(product));

        List<ProductResponse> result =
                service().listProducts(new ProductFilter(null, null, true));

        assertThat(result).hasSize(1);
        verify(productRepository,
                never()).findByActiveTrueOrderByNameAsc();
    }

    @Test
    void listProductsWithLowStockAndQSearchesLowStockByText() {
        when(productRepository.searchLowStock("widget")).thenReturn(List.of());

        service().listProducts(new ProductFilter(null, "  widget", true));

        verify(productRepository).searchLowStock("widget");
        verify(productRepository, never()).search(any());
        verify(productRepository, never()).findLowStock();
    }

    @Test
    void listProductsPrefersSkuOverLowStock() {
        Product product = new Product("SKU-1", "Widget", "A widget", "pcs", 5, clock.instant());
        when(productRepository.findBySku("SKU-1")).thenReturn(Optional.of(product));

        service().listProducts(new ProductFilter("SKU-1", null,
                true));

        verify(productRepository, never()).findLowStock();
    }

    @Test
    void listProductsWithLowStockFalseIsIgnored() {
        when(productRepository.findByActiveTrueOrderByNameAsc())
                .thenReturn(List.of());

        service().listProducts(new ProductFilter(null, null,
                false));

        verify(productRepository, never()).findLowStock();
    }

    @Test
    void updateProductChangesEditableFieldsAndKeepsSkuAndStock() {
        Product product = new Product("SKU-1", "Widget", "A widget", "pcs", 5, clock.instant());
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        ProductResponse result = service().updateProduct(1L,
                new UpdateProductRequest("New Name", "New description", "box", 8));

        assertThat(result.sku()).isEqualTo("SKU-1");
        assertThat(result.name()).isEqualTo("New Name");
        assertThat(result.description()).isEqualTo("New description");
        assertThat(result.unit()).isEqualTo("box");
        assertThat(result.reorderLevel()).isEqualTo(8);
        assertThat(result.quantityOnHand()).isZero();
    }

    @Test
    void updateProductFailsWhenProductNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().updateProduct(99L,
                new UpdateProductRequest("New Name", "New description", "box", 8)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product with id 99 not found");
    }

    @Test
    void deactivateProductMarksProductInactive() {
        Product product = new Product("SKU-1", "Widget", "A widget", "pcs", 5, clock.instant());
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        service().deactivateProduct(1L);

        assertThat(product.isActive()).isFalse();
    }

    @Test
    void deactivateProductIsIdempotentWhenAlreadyInactive() {
        Product product = new Product("SKU-1", "Widget", "A widget", "pcs", 5, clock.instant());
        product.setActive(false);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        service().deactivateProduct(1L);

        assertThat(product.isActive()).isFalse();
    }

    @Test
    void deactivateProductFailsWhenProductNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().deactivateProduct(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product with id 99 not found");
    }

}
