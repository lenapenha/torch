package dev.hpsoftware.torch.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class InventoryFlowIT extends BaseIntegrationTest {

    @Test
    void createProductStartsWithZeroStock() throws Exception {
        //create the product
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCT_BODY))
                .andExpect(status().isCreated())
                //verify that the product gets id 1 and starts with stock 0
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.quantityOnHand").value(0));
    }

    @Test
    void receiveAddsStock() throws Exception {
        createProduct(); //create the product with stock 0

        //receive 10 units
        mockMvc.perform(post("/api/products/1/movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "RECEIPT", "quantity": 10, "note": "test"}
                                """))
                .andExpect(status().isCreated())
                //verify that the delta is +10 and the balance is 10
                .andExpect(jsonPath("$.quantityDelta").value(10))
                .andExpect(jsonPath("$.balanceAfter").value(10));
    }

    @Test
    void issueSubtractsStock() throws Exception {
        createProduct(); //create the product with stock 0
        receive(); //stock is now 10

        //issue 4 units
        mockMvc.perform(post("/api/products/1/movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "ISSUE", "quantity": 4, "note": "test"}
                                """))
                .andExpect(status().isCreated())
                //verify that the delta is -4 (negative) and the balance is 6
                .andExpect(jsonPath("$.quantityDelta").value(-4))
                .andExpect(jsonPath("$.balanceAfter").value(6));
    }

    @Test
    void productShowsCurrentBalance() throws Exception {
        createProduct(); //create the product with stock 0
        receive();  //stock is now 10
        issue(); //issue with quantity 4, stock is now 6

        //get the product
        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                //verify that the current stock is 6 (10 received - 4 issued)
                .andExpect(jsonPath("$.quantityOnHand").value(6));
    }

    @Test
    void historyListsMovementsNewestFirst() throws Exception {
        createProduct(); //create the product with stock 0
        receive();  //stock is now 10
        issue(); //issue with quantity 4, stock is now 6

        //get the movement history
        mockMvc.perform(get("/api/products/1/movements"))
                .andExpect(status().isOk())
                //verify that there are 2 movements (stock and issue)
                .andExpect(jsonPath("$.totalElements").value(2))
                //verify that the newest (issue) comes first, then the receipt
                .andExpect(jsonPath("$.content[0].type").value("ISSUE"))
                .andExpect(jsonPath("$.content[1].type").value("RECEIPT"))
                //verify the balance after each movement
                .andExpect(jsonPath("$.content[0].balanceAfter").value(6))
                .andExpect(jsonPath("$.content[1].balanceAfter").value(10));
    }

    @Test
    void rejectedIssueLeavesStockUnchanged() throws Exception {
        createProduct(); //create the product with stock 0
        receive(); //stock is now 10

        final String body = """
                {
                "type": "ISSUE",
                "quantity": 15,
                "note": "too much"
                }
                """;

        //perform the issue with quantity 15, which should fail because there is only 10 available
        mockMvc.perform(post("/api/products/1/movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Insufficient stock"));

        //verify that the stock is still 10
        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityOnHand").value(10));

        //verify that only the receipt is in the history, the rejected issue was not recorded
        mockMvc.perform(get("/api/products/1/movements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // --- setup helpers: perform the call and require success, so a broken
    // precondition fails here instead of inside the assertions under test ---

    private static final String PRODUCT_BODY = """
            {
            "sku": "SKU-1",
            "name": "Widget",
            "description": "A widget",
            "unit": "pcs",
            "reorderLevel": 5}
            """;

    private void createProduct() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCT_BODY))
                .andExpect(status().isCreated());
    }

    private void receive() throws Exception {
        final String body = """
                {
                "type": "RECEIPT",
                "quantity": 10,
                "note": "test"
                }
                """;

        mockMvc.perform(post("/api/products/1/movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private void issue() throws Exception {
        final String body = """
                {
                "type": "ISSUE",
                "quantity": 4,
                "note": "test"
                }
                """;

        mockMvc.perform(post("/api/products/1/movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }
}
