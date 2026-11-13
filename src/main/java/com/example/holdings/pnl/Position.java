package com.example.holdings.pnl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 依時間順序（交易日、再依建立順序）重播一檔股票的所有買賣，算出現在的持股狀態。
 *
 * 成本用「移動平均」：
 * - 買進：股數加上去；成本加上「買進金額 + 買進手續費」。
 * - 賣出：從成本裡按比例扣掉「賣出股數 ÷ 賣出前股數」那一份（四捨五入到元），
 *        已實現損益 = 賣出金額 − 賣出手續費 − 證交稅 − 扣掉的那份成本。
 * 只有一筆買進時，未實現損益跟 1.0 的算法完全一樣。
 */
public final class Position {
    public enum Side { BUY, SELL }

    public record Entry(long id, Side side, LocalDate date, long shares, BigDecimal price) {}

    /** 每一筆交易算完之後的明細；買進的 realized 為 0。 */
    public record Line(Entry entry, long amount, long fee, long tax, long costAllocated, long realized, long sharesAfter) {}

    private final String code;
    private final List<Line> lines;
    private final long shares;
    private final long cost;          // 剩下持股的成本，含買進手續費
    private final long buyAmount;     // 剩下持股的買進金額，不含手續費（算平均買進價用）
    private final long realized;

    private Position(String code, List<Line> lines, long shares, long cost, long buyAmount, long realized) {
        this.code = code;
        this.lines = lines;
        this.shares = shares;
        this.cost = cost;
        this.buyAmount = buyAmount;
        this.realized = realized;
    }

    /**
     * entries 必須已經依交易日、id 排好。賣超過當時持有的股數丟 InsufficientSharesException。
     */
    public static Position replay(String code, List<Entry> entries) {
        long shares = 0, cost = 0, buyAmount = 0, realized = 0;
        List<Line> lines = new ArrayList<>();
        for (Entry e : entries) {
            long amount = Fees.amount(e.price(), e.shares());
            long fee = Fees.fee(amount);
            if (e.side() == Side.BUY) {
                shares += e.shares();
                cost += amount + fee;
                buyAmount += amount;
                lines.add(new Line(e, amount, fee, 0, 0, 0, shares));
            } else {
                if (e.shares() > shares) {
                    throw new InsufficientSharesException(e, shares);
                }
                long allocated = share(cost, e.shares(), shares);
                long allocatedAmount = share(buyAmount, e.shares(), shares);
                long tax = Fees.tax(code, amount);
                long r = amount - fee - tax - allocated;
                shares -= e.shares();
                cost -= allocated;
                buyAmount -= allocatedAmount;
                realized += r;
                lines.add(new Line(e, amount, fee, tax, allocated, r, shares));
            }
        }
        return new Position(code, List.copyOf(lines), shares, cost, buyAmount, realized);
    }

    /** total × part ÷ whole，四捨五入到元；全部賣光時直接回 total，不留尾數。 */
    private static long share(long total, long part, long whole) {
        if (part == whole) return total;
        return BigDecimal.valueOf(total).multiply(BigDecimal.valueOf(part))
            .divide(BigDecimal.valueOf(whole), 0, RoundingMode.HALF_UP).longValueExact();
    }

    public String code() { return code; }
    public List<Line> lines() { return lines; }
    public long shares() { return shares; }
    public long cost() { return cost; }
    public long realized() { return realized; }

    /** 平均成本（每股，含買進手續費），兩位小數；沒有持股回 null。 */
    public BigDecimal avgCost() {
        return shares == 0 ? null : BigDecimal.valueOf(cost).divide(BigDecimal.valueOf(shares), 2, RoundingMode.HALF_UP);
    }

    /** 平均買進價（每股，不含手續費），兩位小數；給 1.0 的 buy_price 用。 */
    public BigDecimal avgBuyPrice() {
        return shares == 0 ? null : BigDecimal.valueOf(buyAmount).divide(BigDecimal.valueOf(shares), 2, RoundingMode.HALF_UP);
    }

    /** 剩下的持股用收盤價全部賣出的未實現損益；沒有持股回 null。 */
    public Pnl unrealized(BigDecimal close) {
        if (shares == 0 || close == null) return null;
        long value = Fees.amount(close, shares);
        long sellFee = Fees.fee(value);
        long tax = Fees.tax(code, value);
        long pnl = value - sellFee - tax - cost;
        return new Pnl(buyAmount, cost - buyAmount, value, sellFee, tax, pnl, Fees.returnPct(pnl, cost));
    }

    public static class InsufficientSharesException extends RuntimeException {
        private final transient Entry entry;
        private final long available;

        public InsufficientSharesException(Entry entry, long available) {
            super(entry.date() + " 要賣 " + entry.shares() + " 股，但當時只持有 " + available + " 股");
            this.entry = entry;
            this.available = available;
        }

        public Entry entry() { return entry; }
        public long available() { return available; }
    }
}
