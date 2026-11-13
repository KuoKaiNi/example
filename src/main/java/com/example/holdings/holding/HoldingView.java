package com.example.holdings.holding;

import com.example.holdings.pnl.Fees;
import com.example.holdings.pnl.Pnl;
import com.example.holdings.quote.Quote;

/** 畫面與 API 用：持股 ＋ 最近收盤價 ＋ 盈虧（取不到收盤價時 quote、pnl 為 null）。 */
public record HoldingView(Holding holding, Quote quote, Pnl pnl) {
    public static HoldingView of(Holding h, Quote q) {
        long shares = h.getLots() * 1000L;
        Pnl p = q == null ? null : Pnl.unrealized(h.getCode(), shares, h.getBuyPrice(), q.close());
        return new HoldingView(h, q, p);
    }

    /** 買進金額 + 買進手續費；不需要收盤價。 */
    public long cost() {
        long amount = Fees.amount(holding.getBuyPrice(), holding.getLots() * 1000L);
        return amount + Fees.fee(amount);
    }

    public boolean hasQuote() { return quote != null; }
}
