package dev.hpsoftware.torch.repository;

import dev.hpsoftware.torch.model.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class ProductRepositoryTest {

    @Autowired
    ProductRepository productRepository;

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void findsProductByIdForUpdate() {

        Product saved = productRepository.save(newProduct("SKU-1"));

        productRepository.findByIdForUpdate(1L).ifPresent(System.out::println);

        assertThat(productRepository.findByIdForUpdate(saved.getId()))
                .isPresent()
                .get()
                .extracting(Product::getSku)
                .isEqualTo("SKU-1");
    }

    private Product newProduct(String sku) {
        return new Product(sku, "Product " + sku, "Description", "pcs", 10, CLOCK.instant());
    }

    @Test
    void findByIdForUpdateReturnsEmptyForUnknownId() {
        assertThat(productRepository.findByIdForUpdate(-1L)).isEmpty();
    }

    @Test
    void findsProductBySku() {
        productRepository.save(newProduct("SKU-2"));

        assertThat(productRepository.findBySku("SKU-2")).isPresent();
        assertThat(productRepository.findBySku("NOPE")).isEmpty();
    }

    @Test
    void existsBySku() {
        productRepository.save(newProduct("SKU-3"));

        assertThat(productRepository.existsBySku("SKU-3")).isTrue();
        assertThat(productRepository.existsBySku("NOPE")).isFalse();
    }

    @Test
    void newProductStartsWithZeroStockAndActive() {
        Product saved = productRepository.save(newProduct("SKU-4"));

        assertThat(saved.getQuantityOnHand()).isZero();
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getCreatedAt()).isEqualTo(CLOCK.instant());
    }
}
