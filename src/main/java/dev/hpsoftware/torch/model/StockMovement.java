package dev.hpsoftware.torch.model;

import dev.hpsoftware.torch.enums.MovementType;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "stock_movement")
public class StockMovement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MovementType type;

    @Column(name = "quantity_delta", nullable = false)
    private int quantityDelta;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StockMovement() {}

    public StockMovement(Product product, MovementType type, int quantityDelta, int balanceAfter, String note, Instant createdAt) {
        this.product = product;
        this.type = type;
        this.quantityDelta = quantityDelta;
        this.balanceAfter = balanceAfter;
        this.note = note;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public MovementType getType() {
        return type;
    }

    public int getQuantityDelta() {
        return quantityDelta;
    }

    public int getBalanceAfter() {
        return balanceAfter;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
