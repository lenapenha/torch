package dev.hpsoftware.torch.seed;

import dev.hpsoftware.torch.dto.CreateMovementRequest;
import dev.hpsoftware.torch.dto.CreateProductRequest;
import dev.hpsoftware.torch.dto.ProductResponse;
import dev.hpsoftware.torch.enums.MovementType;
import dev.hpsoftware.torch.repository.ProductRepository;
import dev.hpsoftware.torch.service.MovementService;
import dev.hpsoftware.torch.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
@Profile("seed")
public class SeedDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataLoader.class);

    private static final int HISTORY_DAYS = 60;

    // what we want each product to look like at the end; random movements in between are
    // corrected by one last movement so the final state is always exactly finalStock
    private record SeedProduct(String sku, String name, String description, String unit,
                               int reorderLevel, int initialStock, int finalStock) {}

    private static final List<SeedProduct> PRODUCTS = List.of(
            // healthy: stock above reorder level
            new SeedProduct("NB-A5", "Notebook A5", "Ruled A5 notebook, 80 pages", "pcs", 20, 120, 85),
            new SeedProduct("PEN-BLK", "Ballpoint pen black", "Box of 12 black ballpoint pens", "box", 10, 60, 42),
            new SeedProduct("PAP-A4", "Printer paper A4", "A4 80g paper, 500 sheets", "ream", 30, 200, 140),
            new SeedProduct("USB-C1", "USB-C cable 1m", "USB-C to USB-C charging cable, 1 metre", "pcs", 15, 80, 52),
            new SeedProduct("STP-STD", "Stapler", "Standard desktop stapler", "pcs", 5, 25, 18),
            new SeedProduct("FLD-MAN", "Manila folders", "Pack of 25 manila folders", "pack", 20, 150, 96),
            new SeedProduct("MRK-WB", "Whiteboard markers", "Pack of 4 assorted whiteboard markers", "pack", 12, 70, 38),
            // low stock: at or below reorder level
            new SeedProduct("TNR-85A", "Toner cartridge 85A", "Black toner cartridge for laser printer", "pcs", 8, 30, 5),
            new SeedProduct("BAT-AA", "AA batteries", "Pack of 4 AA alkaline batteries", "pack", 25, 120, 14),
            new SeedProduct("TAP-CLR", "Clear tape", "Clear adhesive tape roll, 19mm", "roll", 15, 90, 9),
            new SeedProduct("ENV-DL", "Envelopes DL", "Box of 50 DL envelopes", "box", 10, 40, 7),
            // out of stock
            new SeedProduct("GLU-STK", "Glue sticks", "20g glue stick", "pcs", 15, 60, 0));

    private final ProductService productService;
    private final MovementService movementService;
    private final ProductRepository productRepository;
    private final Clock clock;

    public SeedDataLoader(ProductService productService,
                          MovementService movementService,
                          ProductRepository productRepository,
                          Clock clock) {
        this.productService = productService;
        this.movementService = movementService;
        this.productRepository = productRepository;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (productRepository.count() > 0) {
            log.info("Seed data skipped: products already exist");
            return;
        }

        List<ProductResponse> products = new ArrayList<>();
        int[] balances = new int[PRODUCTS.size()];
        for (SeedProduct seed : PRODUCTS) {
            products.add(productService.createProduct(new CreateProductRequest(
                    seed.sku(), seed.name(), seed.description(), seed.unit(), seed.reorderLevel())));
        }

        // midnight today (UTC); every movement is placed relative to it
        Instant today = clock.instant().truncatedTo(ChronoUnit.DAYS);
        Random random = new Random(42); // fixed seed: reviewers always see the same data

        // day -60: initial inventory is a RECEIPT, like any other stock
        for (int i = 0; i < PRODUCTS.size(); i++) {
            SeedProduct seed = PRODUCTS.get(i);
            Instant at = today.minus(HISTORY_DAYS, ChronoUnit.DAYS).plus(8, ChronoUnit.HOURS);
            record(products.get(i), MovementType.RECEIPT, seed.initialStock(), "Initial stock", at);
            balances[i] = seed.initialStock();
        }

        // days -59..-2: random sales and restocks; day by day so history is chronological
        for (int day = HISTORY_DAYS - 1; day >= 2; day--) {
            for (int i = 0; i < PRODUCTS.size(); i++) {
                SeedProduct seed = PRODUCTS.get(i);
                Instant at = today.minus(day, ChronoUnit.DAYS).plus(9 + random.nextInt(8), ChronoUnit.HOURS);
                int roll = random.nextInt(100);

                if (roll < 35) {
                    int quantity = Math.min(1 + random.nextInt(Math.max(2, seed.initialStock() / 12)), balances[i]);
                    if (quantity > 0) { // never issue more than we have: the service would reject it
                        record(products.get(i), MovementType.ISSUE, quantity, "Sale", at);
                        balances[i] -= quantity;
                    }
                } else if (roll < 42) {
                    int quantity = Math.max(1, seed.initialStock() / 3);
                    record(products.get(i), MovementType.RECEIPT, quantity, "Restock", at);
                    balances[i] += quantity;
                }
            }
        }

        // day -1: one last movement brings each product to its target final stock
        for (int i = 0; i < PRODUCTS.size(); i++) {
            int difference = PRODUCTS.get(i).finalStock() - balances[i];
            Instant at = today.minus(1, ChronoUnit.DAYS).plus(17, ChronoUnit.HOURS);
            if (difference > 0) {
                record(products.get(i), MovementType.RECEIPT, difference, "Supplier delivery", at);
            } else if (difference < 0) {
                record(products.get(i), MovementType.ISSUE, -difference, "Bulk order", at);
            }
        }

        log.info("Seed data loaded: {} products", PRODUCTS.size());
    }

    private void record(ProductResponse product, MovementType type, int quantity, String note, Instant at) {
        movementService.recordMovement(product.id(), new CreateMovementRequest(type, quantity, note), at);
    }
}
