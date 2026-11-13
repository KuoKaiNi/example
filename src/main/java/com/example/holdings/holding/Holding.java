package com.example.holdings.holding;

import java.time.LocalDateTime;

import jakarta.persistence.*;

/** 持股主檔：一檔股票一筆。股數、成本由 trade 算出來，不存在這裡。 */
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

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Holding() {}

    public Holding(String code, String name, String market) {
        this.code = code;
        this.name = name;
        this.market = market;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getMarket() { return market; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
    public void setMarket(String market) { this.market = market; }
}
