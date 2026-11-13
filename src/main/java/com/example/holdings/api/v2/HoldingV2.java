package com.example.holdings.api.v2;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.example.holdings.holding.HoldingView;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.ALWAYS)
public record HoldingV2(long id, String code, String name, String market, long shares, BigDecimal avgCost,
                        long cost, BigDecimal currentPrice, LocalDate priceDate, Long marketValue,
                        Long unrealizedPnl, BigDecimal unrealizedReturnPct, long realizedPnl) {

    public static HoldingV2 from(HoldingView v) {
        var h = v.holding();
        var pos = v.position();
        var q = v.quote();
        var u = v.unrealized();
        return new HoldingV2(h.getId(), h.getCode(), nz(h.getName()), nz(h.getMarket()), pos.shares(), pos.avgCost(),
            pos.cost(), q == null ? null : q.close(), q == null ? null : q.date(),
            u == null ? null : u.marketValue(), u == null ? null : u.pnl(), u == null ? null : u.returnPct(),
            pos.realized());
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
