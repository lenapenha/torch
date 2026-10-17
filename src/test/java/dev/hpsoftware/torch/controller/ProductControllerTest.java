package dev.hpsoftware.torch.controller;

import dev.hpsoftware.torch.dto.ProductResponse;
import dev.hpsoftware.torch.exception.DuplicateSkuException;
import dev.hpsoftware.torch.service.ProductService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
public class ProductControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProductService productService;

    @Test
    public void shouldReturnCreatedProductWith201AndBody() throws Exception {
        ProductResponse response = new ProductResponse(1L, "SKU-1", "Widget", "Description", "pcs",
                5, 0, true, Instant.parse("2026-01-01T00:00:00Z"));

        when(productService.createProduct(any())).thenReturn(response);

        String body = """                                                                                                                                         
                  {
                    "sku": "SKU-1",
                    "name": "Widget",
                    "description": "A widget",
                    "unit": "pcs",
                    "reorderLevel": 5
                  }
                  """;

        mockMvc.perform(post("/api/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.sku").value("SKU-1"))
                .andExpect(jsonPath("$.quantityOnHand").value(0));

    }

    @Test
    public void shouldReturnBadRequestWhenInvalidRequest() throws Exception {

        String body = """
                    {
                    "sku": " ",
                    "name": "Widget",
                    "description": "A widget",
                    "unit": "pcs"
                    }
                  """;

        mockMvc.perform(post("/api/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.sku").exists())
                .andExpect(jsonPath("$.errors.reorderLevel").exists());

        verifyNoInteractions(productService);
    }

    @Test
    public void shouldReturnConflictWhenSkuAlreadyExists() throws Exception {
        when(productService.createProduct(any()))
                .thenThrow(new DuplicateSkuException("SKU-3"));

        String body = """
                {
                "sku": "SKU-3",
                "name": "Widget",
                "description": "A widget",
                "unit": "pcs",
                "reorderLevel": 5
                }
                """;
        mockMvc.perform(post("/api/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate SKU"))
                .andExpect(jsonPath("$.detail").value("Product with sku SKU-3 already exists"));
    }

    @Test
    public void shouldReturnInternalServerErrorWhenUnexpectedExceptionThrown() throws Exception {
        when(productService.createProduct(any()))
                .thenThrow(new RuntimeException("boom"));

        String body = """
                {
                "sku": "SKU-3",
                "name": "Widget",
                "description": "A widget",
                "unit": "pcs",
                "reorderLevel": 5
                }
                """;

        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.title").value("Internal Server Error"))
            .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

}
