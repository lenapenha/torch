package dev.hpsoftware.torch.repository;

import dev.hpsoftware.torch.model.Product;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
public class ProductRepositoryTest {

    @Autowired
    ProductRepository productRepository;

    @Autowired
    EntityManager entityManager;

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

    @Test
    void findsOnlyActiveProductsOrderedByName() {
        Product bravo = newProduct("SKU-B");
        bravo.setName("Bravo");
        Product alpha = newProduct("SKU-A");
        alpha.setName("Alpha");
        Product charlie = newProduct("SKU-C");
        charlie.setName("Charlie");
        charlie.setActive(false);
        productRepository.saveAll(List.of(bravo, alpha, charlie));

        assertThat(productRepository.findByActiveTrueOrderByNameAsc())
                .extracting(Product::getName)
                .containsSubsequence("Alpha", "Bravo")
                .doesNotContain("Charlie");
    }

    @Test
    void searchMatchesNameOrSkuPartiallyIgnoringCaseAndSkipsInactive() {
        Product byName = newProduct("AAA-1");
        byName.setName("Blue Widget");
        Product bySku = newProduct("WIDGET-9");
        bySku.setName("Gadget");
        Product other = newProduct("ZZZ-1");
        other.setName("Sprocket");
        Product inactive = newProduct("AAA-2");
        inactive.setName("Old Widget");
        inactive.setActive(false);
        productRepository.saveAll(List.of(byName, bySku, other, inactive));

        assertThat(productRepository.search("widget"))
                .extracting(Product::getSku)
                .contains("AAA-1", "WIDGET-9")
                .doesNotContain("ZZZ-1", "AAA-2");
    }

    @Test
    void findLowStockReturnsActiveProductsAtOrBelowReorderLevelOrderedByName() {
        Product below = newProduct("LOW-1");
        below.setName("Alpha below");
        Product atLevel = newProduct("LOW-2");
        atLevel.setName("Bravo at level");
        Product healthy = newProduct("LOW-3");
        healthy.setName("Charlie healthy");
        Product inactive = newProduct("LOW-4");
        inactive.setName("Delta inactive");
        inactive.setActive(false);
        productRepository.saveAllAndFlush(List.of(atLevel, below, healthy, inactive));

        setStock(below, 3);
        setStock(atLevel, 10);
        setStock(healthy, 50);
        setStock(inactive, 0);
        entityManager.clear(); // bulk update bypasses the persistence context

        assertThat(productRepository.findLowStock())
                .extracting(Product::getSku)
                .containsExactly("LOW-1", "LOW-2");
    }

    @Test
    void searchLowStockCombinesTextMatchWithLowStock() {
        Product matchingLow = newProduct("LS-1");
        matchingLow.setName("Blue Widget");
        Product matchingAtLevel = newProduct("LS-2");
        matchingAtLevel.setName("Red WIDGET");
        Product matchingHealthy = newProduct("LS-3");
        matchingHealthy.setName("Green Widget");
        Product nonMatchingLow = newProduct("LS-4");
        nonMatchingLow.setName("Sprocket");
        Product matchingInactive = newProduct("LS-5");
        matchingInactive.setName("Old Widget");
        matchingInactive.setActive(false);
        productRepository.saveAllAndFlush(
                List.of(matchingAtLevel, matchingLow, matchingHealthy, nonMatchingLow, matchingInactive));

        setStock(matchingLow, 3);
        setStock(matchingAtLevel, 10);
        setStock(matchingHealthy, 50);
        setStock(nonMatchingLow, 1);
        setStock(matchingInactive, 0);
        entityManager.clear();

        assertThat(productRepository.searchLowStock("widget"))
                .extracting(Product::getSku)
                .containsExactly("LS-1", "LS-2");
    }


    private void setStock(Product product, int quantity) {
        entityManager.createQuery("update Product p set p.quantityOnHand = :q where p.id = :id")
                .setParameter("q", quantity)
                .setParameter("id", product.getId())
                .executeUpdate();
    }
}
