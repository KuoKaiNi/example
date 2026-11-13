package com.example.holdings.holding;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.*;

@Entity
@Table(name = "trade")
public class Trade {
    public enum Side { BUY, SELL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "holding_id", nullable = false)
    private Long holdingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private Side side;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(nullable = false)
    private int shares;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    protected Trade() {}

    public Trade(Long holdingId, Side side, LocalDate tradeDate, int shares, BigDecimal price) {
        this.holdingId = holdingId;
        this.side = side;
        this.tradeDate = tradeDate;
        this.shares = shares;
        this.price = price;
    }

    public Long getId() { return id; }
    public Long getHoldingId() { return holdingId; }
    public Side getSide() { return side; }
    public LocalDate getTradeDate() { return tradeDate; }
    public int getShares() { return shares; }
    public BigDecimal getPrice() { return price; }

    public void setTradeDate(LocalDate tradeDate) { this.tradeDate = tradeDate; }
    public void setShares(int shares) { this.shares = shares; }
    public void setPrice(BigDecimal price) { this.price = price; }
}
