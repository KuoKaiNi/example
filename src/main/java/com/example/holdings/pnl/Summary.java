package com.example.holdings.pnl;

import java.math.BigDecimal;

/**
 * 總計。取不到收盤價的持股：成本算進 totalCost，不算進 marketValue 與 pnl，筆數記在 quoteMissing。
 */
public record Summary(long totalCost, long marketValue, long pnl, BigDecimal returnPct, int quoteMissing) {

    public static class Builder {
        private long totalCost, marketValue, pnl;
        private int quoteMissing;

        public Builder add(long cost, Pnl p) {
            totalCost += cost;
            if (p == null) {
                quoteMissing++;
            } else {
                marketValue += p.marketValue();
                pnl += p.pnl();
            }
            return this;
        }

        public Summary build() {
            return new Summary(totalCost, marketValue, pnl, Fees.returnPct(pnl, totalCost), quoteMissing);
        }
    }
}
