package com.example.holdings.holding;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "holding")
public class Holding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String code;

    private String name;
    private String market;

    @Column(nullable = false)
    private int lots;

    @Column(name = "buy_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal buyPrice;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Holding() {}

    public Holding(String code, int lots, BigDecimal buyPrice) {
        this.code = code;
        this.lots = lots;
        this.buyPrice = buyPrice;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getMarket() { return market; }
    public int getLots() { return lots; }
    public BigDecimal getBuyPrice() { return buyPrice; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
    public void setMarket(String market) { this.market = market; }
    public void setLots(int lots) { this.lots = lots; }
    public void setBuyPrice(BigDecimal buyPrice) { this.buyPrice = buyPrice; }
}
