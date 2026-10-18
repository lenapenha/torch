package dev.hpsoftware.torch.controller;

import dev.hpsoftware.torch.dto.ProductFilter;
import dev.hpsoftware.torch.dto.ProductResponse;
import dev.hpsoftware.torch.exception.DuplicateSkuException;
import dev.hpsoftware.torch.exception.ResourceNotFoundException;
import dev.hpsoftware.torch.service.ProductService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Test
    public void shouldReturnProductWhenIdExists() throws Exception {
        ProductResponse response = new ProductResponse(1L, "SKU-1", "Widget", "Description", "pcs",
                 5, 0, true, Instant.parse("2026-01-01T00:00:00Z"));

        when(productService.getProduct(1L)).thenReturn(response);

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.sku").value("SKU-1"))
                .andExpect(jsonPath("$.quantityOnHand").value(0));
    }

    @Test
    public void shouldReturnNotFoundWhenProductNotFound() throws Exception {
        when(productService.getProduct(99L))
                .thenThrow(new ResourceNotFoundException("Product with id 99 not found"));

        mockMvc.perform(get("/api/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.detail").value("Product with id 99 not found"));
    }

    @Test
    public void shouldReturnListOfProducts() throws Exception {
        ProductResponse response1 = new ProductResponse(1L, "SKU-1", "Widget", "Description", "pcs",
                   5, 0, true, Instant.parse("2026-01-01T00:00:00Z"));
        ProductResponse response2 = new ProductResponse(2L, "SKU-2", "Widget 2", "Description 2", "pcs",
                   5, 0, true, Instant.parse("2026-01-01T00:00:00Z"));

        when(productService.listProducts(skuFilter(null))).thenReturn(List.of(response1, response2));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sku").value("SKU-1"))
                .andExpect(jsonPath("$[1].sku").value("SKU-2"));
    }

    @Test
    public void shouldReturnEmptyListWhenNoProducts() throws Exception {
        when(productService.listProducts(skuFilter(null))).thenReturn(List.of());

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    public void shouldPassSkuParamToService() throws Exception {
        ProductResponse response = new ProductResponse(1L, "SKU-1", "Widget", "Description", "pcs",
                5, 0, true, Instant.parse("2026-01-01T00:00:00Z"));
        when(productService.listProducts(skuFilter("SKU-1"))).thenReturn(List.of(response));

        mockMvc.perform(get("/api/products").param("sku", "SKU-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sku").value("SKU-1"));
    }

    @Test
    public void shouldPassQParamToService() throws Exception {
        ProductResponse response = new ProductResponse(1L, "SKU-1",
                "Blue Widget", "Description", "pcs",
                5, 0, true, Instant.parse("2026-01-01T00:00:00Z"));
        when(productService.listProducts(new ProductFilter(null,
                "widget", null)))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/products").param("q", "widget"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Blue Widget"));
    }

    @Test
    public void shouldPassLowStockParamToService() throws Exception {
        ProductResponse response = new ProductResponse(1L, "SKU-1",
                "Widget", "Description", "pcs",
                5, 2, true, Instant.parse("2026-01-01T00:00:00Z"));
        when(productService.listProducts(new ProductFilter(null, null,
                true)))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/products").param("lowStock", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sku").value("SKU-1"));
    }

    @Test
    public void shouldPassLowStockAndQParamsToService() throws
            Exception {
        ProductResponse response = new ProductResponse(1L, "SKU-1",
                "Blue Widget", "Description", "pcs",
                5, 2, true, Instant.parse("2026-01-01T00:00:00Z"));
        when(productService.listProducts(new ProductFilter(null,
                "widget", true)))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/products").param("q",
                        "widget").param("lowStock", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Blue Widget"));
    }



    @Test
    public void shouldUpdateProductAndReturn200() throws Exception {
        ProductResponse response = new ProductResponse(1L, "SKU-1", "New Name", "New description", "box",
                8, 3, true, Instant.parse("2026-01-01T00:00:00Z"));
        when(productService.updateProduct(eq(1L), any())).thenReturn(response);

        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.sku").value("SKU-1"))
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.reorderLevel").value(8));
    }

    @Test
    public void shouldReturn404WhenUpdatingUnknownProduct() throws Exception {
        when(productService.updateProduct(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Product with id 99 not found"));

        mockMvc.perform(put("/api/products/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Product with id 99 not found"));
    }

    @Test
    public void shouldReturn400WhenUpdateBodyIsInvalid() throws Exception {
        String body = """
                {
                  "name": "",
                  "description": "New description",
                  "unit": "box",
                  "reorderLevel": -1
                }
                """;

        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.reorderLevel").exists());

        verifyNoInteractions(productService);
    }

    @Test
    public void shouldDeactivateProductAndReturn204() throws Exception {
        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isNoContent());

        verify(productService).deactivateProduct(1L);
    }

    @Test
    public void shouldReturn404WhenDeactivatingUnknownProduct() throws Exception {
        doThrow(new ResourceNotFoundException("Product with id 99 not found"))
                .when(productService).deactivateProduct(99L);

        mockMvc.perform(delete("/api/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Product with id 99 not found"));
    }

    private static ProductFilter skuFilter(String sku) {
        return new ProductFilter(sku, null, null);
    }

    private static final String UPDATE_BODY = """
            {
              "name": "New Name",
              "description": "New description",
              "unit": "box",
              "reorderLevel": 8
            }
            """;
}
