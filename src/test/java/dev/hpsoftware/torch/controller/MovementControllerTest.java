package dev.hpsoftware.torch.controller;

import dev.hpsoftware.torch.dto.MovementResponse;
import dev.hpsoftware.torch.enums.MovementType;
import dev.hpsoftware.torch.exception.InsufficientStockException;
import dev.hpsoftware.torch.exception.ResourceNotFoundException;
import dev.hpsoftware.torch.service.MovementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MovementController.class)
public class MovementControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MovementService movementService;

    @Test
    public void shouldRecordReceiptAndReturn201() throws Exception {
        MovementResponse response = new MovementResponse(10L, 1L, MovementType.RECEIPT, 25, 25,
                "Initial stock", Instant.parse("2026-01-01T00:00:00Z"));
        when(movementService.recordMovement(eq(1L), any())).thenReturn(response);

        String body = """
                {
                  "type": "RECEIPT",
                  "quantity": 25,
                  "note": "Initial stock"
                }
                """;

        mockMvc.perform(post("/api/products/1/movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.productId").value(1))
                .andExpect(jsonPath("$.type").value("RECEIPT"))
                .andExpect(jsonPath("$.quantityDelta").value(25))
                .andExpect(jsonPath("$.balanceAfter").value(25))
                .andExpect(jsonPath("$.note").value("Initial stock"));
    }

    @Test
    public void shouldReturn404WhenProductDoesNotExist() throws Exception {
        when(movementService.recordMovement(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Product with id 99 not found"));

        mockMvc.perform(post("/api/products/99/movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "RECEIPT", "quantity": 5}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Product with id 99 not found"));
    }

    @Test
    public void shouldReturn400WhenQuantityIsZeroOrNegative() throws Exception {
        mockMvc.perform(post("/api/products/1/movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "RECEIPT", "quantity": 0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.quantity").exists());

        mockMvc.perform(post("/api/products/1/movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "RECEIPT", "quantity": -5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.quantity").exists());

        verifyNoInteractions(movementService);
    }

    @Test
    public void shouldReturn409WhenIssueExceedsStock() throws Exception {
        when(movementService.recordMovement(eq(1L), any()))
                .thenThrow(new InsufficientStockException("Insufficient stock: requested 30, available 25"));

        mockMvc.perform(post("/api/products/1/movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "ISSUE", "quantity": 30}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Insufficient stock: requested 30, available 25"));
    }

    @Test
    public void shouldReturn400WhenTypeIsMissing() throws Exception {
        mockMvc.perform(post("/api/products/1/movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.type").exists());

        verifyNoInteractions(movementService);
    }
}
