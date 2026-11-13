package com.example.holdings.api.v1;

import java.math.BigDecimal;

import com.example.holdings.pnl.Summary;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SummaryV1(long totalCost, long marketValue, long pnl, BigDecimal returnPct, int quoteMissing) {
    public static SummaryV1 from(Summary s) {
        return new SummaryV1(s.totalCost(), s.marketValue(), s.pnl(), s.returnPct(), s.quoteMissing());
    }
}
