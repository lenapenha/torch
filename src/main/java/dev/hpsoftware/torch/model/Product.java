package dev.hpsoftware.torch.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false, length = 32)
    private String unit;

    @Column(name = "reorder_level", nullable = false)
    private int reorderLevel;

    @Column(name = "quantity_on_hand", nullable = false)
    private int quantityOnHand;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected Product() {}

    public Product(String sku, String name, String description, String unit, int reorderLevel,  Instant createdAt) {
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.unit = unit;
        this.reorderLevel = reorderLevel;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getUnit() {
        return unit;
    }

    public int getReorderLevel() {
        return reorderLevel;
    }

    public int getQuantityOnHand() {
        return quantityOnHand;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public void setReorderLevel(int reorderLevel) {
        this.reorderLevel = reorderLevel;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
