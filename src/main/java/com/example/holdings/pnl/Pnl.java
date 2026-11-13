package com.example.holdings.pnl;

import java.math.BigDecimal;

/**
 * 一筆持股「現在用收盤價全部賣掉」的試算結果，單位都是元。
 * cost = 買進金額 + 買進手續費；pnl = 賣出金額 − 賣出手續費 − 證交稅 − cost。
 */
public record Pnl(long buyAmount, long buyFee, long marketValue, long sellFee, long tax, long pnl, BigDecimal returnPct) {
    public long cost() { return buyAmount + buyFee; }

    public static Pnl unrealized(String code, long shares, BigDecimal buyPrice, BigDecimal close) {
        long buyAmount = Fees.amount(buyPrice, shares);
        long buyFee = Fees.fee(buyAmount);
        long value = Fees.amount(close, shares);
        long sellFee = Fees.fee(value);
        long tax = Fees.tax(code, value);
        long pnl = value - sellFee - tax - buyAmount - buyFee;
        return new Pnl(buyAmount, buyFee, value, sellFee, tax, pnl, Fees.returnPct(pnl, buyAmount + buyFee));
    }
}
