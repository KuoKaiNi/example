package com.example.holdings.holding;

import com.example.holdings.quote.Quote;

/** 畫面與 API 用：持股 ＋ 最近收盤價（取不到時 quote 為 null）。 */
public record HoldingView(Holding holding, Quote quote) {
    public boolean hasQuote() { return quote != null; }
}
