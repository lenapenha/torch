package dev.hpsoftware.torch.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ConcurrencyIT extends BaseIntegrationTest {
    /**
     * This test simulates a race condition between threads
     * It creates a product with stock 7, then sends 10
     * parallel ISSUE requests of 1 unit each. A latch releases them all at once.
      */

    // 10 parallel issues of 1 unit against stock of 7: exactly 7 succeed, and 3 fail
    // (10 threads is the max: the default connection pool has 10 connections)
    private static final int THREADS = 10;
    private static final int STOCK = 7;

    private static final String PRODUCT_BODY = """
            {
            "sku": "SKU-1",
            "name": "Widget",
            "description": "A widget",
            "unit": "pcs",
            "reorderLevel": 5}
            """;

    @Test
    void concurrentIssuesNeverOversell() throws Exception {
        //set up: product with stock 7
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCT_BODY))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/products/1/movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "RECEIPT", "quantity": 7, "note": "initial"}
                                """))
                .andExpect(status().isCreated());

        final String issueOne = """
                {"type": "ISSUE", "quantity": 1, "note": "race"}
                """;

        //issue 1 unit from 10 threads at the same time (the latch releases them together)
        List<Integer> statuses;
        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            statuses = new ArrayList<>();

            try {
                for (int i = 0; i < THREADS; i++) {
                    results.add(pool.submit(() -> {
                        start.await();
                        return mockMvc.perform(post("/api/products/1/movements")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(issueOne))
                                .andReturn().getResponse().getStatus();
                    }));
                }
                start.countDown();

                for (Future<Integer> result : results) {
                    statuses.add(result.get());
                }
            } finally {
                pool.shutdown();
            }
        }

        //verify that exactly 7 issues succeeded, 3 were rejected, and nothing else (no 500)
        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(STOCK);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(THREADS - STOCK);

        //verify that the stock is 0, never negative
        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityOnHand").value(0));

        //verify that the history has 8 movements: 1 receipt + 7 issues (rejected ones are not recorded)
        mockMvc.perform(get("/api/products/1/movements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(STOCK + 1));

        //verify the ledger invariant: the sum of all deltas equals the balance
        Integer sumOfDeltas = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(quantity_delta), 0) FROM stock_movement", Integer.class);
        assertThat(sumOfDeltas).isZero();
    }
}
