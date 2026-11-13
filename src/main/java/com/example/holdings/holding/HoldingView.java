package com.example.holdings.holding;

import com.example.holdings.pnl.Pnl;
import com.example.holdings.pnl.Position;
import com.example.holdings.quote.Quote;

/**
 * 畫面與 API 用：持股主檔 ＋ 重播交易後的狀態 ＋ 最近收盤價 ＋ 未實現損益。
 * 取不到收盤價時 quote、unrealized 為 null；已經全部賣掉的持股 unrealized 也是 null。
 */
public record HoldingView(Holding holding, Position position, Quote quote, Pnl unrealized) {
    public boolean open() { return position.shares() > 0; }
    public long cost() { return position.cost(); }
}
