package com.example.holdings.api.v2;

import java.math.BigDecimal;

import com.example.holdings.pnl.Summary;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SummaryV2(long totalCost, long marketValue, long unrealizedPnl, BigDecimal unrealizedReturnPct,
                        long realizedPnl, int quoteMissing) {
    public static SummaryV2 from(Summary s) {
        return new SummaryV2(s.totalCost(), s.marketValue(), s.pnl(), s.returnPct(), s.realized(), s.quoteMissing());
    }
}
