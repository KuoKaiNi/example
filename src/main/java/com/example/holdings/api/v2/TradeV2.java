package com.example.holdings.api.v2;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.example.holdings.pnl.Position;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** 一筆買賣與當時算出來的費用；買進的 tax、realized_pnl 為 0。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TradeV2(long id, String side, LocalDate tradeDate, long shares, BigDecimal price, long amount,
                      long fee, long tax, long realizedPnl, long sharesAfter) {

    public static TradeV2 from(Position.Line l) {
        var e = l.entry();
        return new TradeV2(e.id(), e.side().name(), e.date(), e.shares(), e.price(), l.amount(), l.fee(), l.tax(),
            l.realized(), l.sharesAfter());
    }
}
