package dev.hpsoftware.torch.integration;

import dev.hpsoftware.torch.seed.SeedDataLoader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

// adds the "seed" profile to the inherited "test" one, so the SeedDataLoader bean exists.
// It also runs once at context startup, but BaseIntegrationTest truncates the tables
// before each test, so every test runs the loader itself on an empty database.
@ActiveProfiles("seed")
public class SeedDataLoaderIT extends BaseIntegrationTest {

    @Autowired
    SeedDataLoader loader;

    private int count(String sql) {
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class);
        return result == null ? 0 : result;
    }

    // 12 products exist, and there are more than 12 movements
    @Test
    void seedsTwelveProductsWithHistory() throws Exception {
        loader.run(null);

        assertThat(count("select count(*) from product")).isEqualTo(12);
        assertThat(count("select count(*) from stock_movement")).isGreaterThan(12);
    }

    // For every product, the sum of quantity_delta equals quantity_on_hand.
    // This is the ledger invariant.
    @Test
    void ledgerSumMatchesQuantityOnHandForEveryProduct() throws Exception {
        loader.run(null);

        int mismatches = count("""
                select count(*) from product p
                where p.quantity_on_hand <>
                      coalesce((select sum(m.quantity_delta) from stock_movement m where m.product_id = p.id), 0)
                """);

        assertThat(mismatches).isZero();
    }

    // For every product, the latest movement's balance_after equals quantity_on_hand.
    @Test
    void lastMovementBalanceAfterMatchesQuantityOnHand() throws Exception {
        loader.run(null);

        int mismatches = count("""
                select count(*) from product p
                where p.quantity_on_hand <>
                      (select m.balance_after from stock_movement m
                       where m.product_id = p.id order by m.created_at desc, m.id desc limit 1)
                """);

        assertThat(mismatches).isZero();
    }

    //Exactly 4 products are low stock (above 0 and at or below the reorder level)
    // and exactly 1 is out of stock
    @Test
    void containsLowStockAndOutOfStockProducts() throws Exception {
        loader.run(null);

        assertThat(count("select count(*) from product "
                + "where quantity_on_hand > 0 and quantity_on_hand <= reorder_level")).isEqualTo(4);
        assertThat(count("select count(*) from product where quantity_on_hand = 0")).isEqualTo(1);
    }

    //History goes back more than 55 days
    @Test
    void historyIsBackDatedAboutSixtyDays() throws Exception {
        loader.run(null);

        assertThat(count("select count(*) from stock_movement "
                + "where created_at < now() - interval '55 days'")).isPositive();
    }

    //Running the loader a second time adds nothing.
    @Test
    void runningTwiceDoesNotDuplicateData() throws Exception {
        loader.run(null);
        int products = count("select count(*) from product");
        int movements = count("select count(*) from stock_movement");

        loader.run(null);

        assertThat(count("select count(*) from product")).isEqualTo(products);
        assertThat(count("select count(*) from stock_movement")).isEqualTo(movements);
    }
}
