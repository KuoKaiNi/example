package com.example.holdings.api.v1;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.example.holdings.holding.HoldingView;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * App 1.0 的持股物件。欄位、型別、名稱都不能改。
 * 2.0 之後的對應方式：
 * - lots：持有股數 ÷ 1000 無條件捨去（App 1.0 只吃整數）；零股部分看不到張數，但 pnl 有算進去
 * - buy_price：剩下持股的平均買進價（不含手續費）
 * - pnl、return_pct：剩下「全部」持股（含零股）的未實現損益；已實現損益 1.0 不顯示
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.ALWAYS)
public record HoldingV1(long id, String code, String name, String market, int lots, BigDecimal buyPrice,
                        BigDecimal currentPrice, LocalDate priceDate, Long pnl, BigDecimal returnPct) {

    public static HoldingV1 from(HoldingView v) {
        var h = v.holding();
        var q = v.quote();
        var p = v.unrealized();
        var pos = v.position();
        return new HoldingV1(h.getId(), h.getCode(), nz(h.getName()), nz(h.getMarket()),
            (int) (pos.shares() / 1000), pos.avgBuyPrice(),
            q == null ? null : q.close(), q == null ? null : q.date(),
            p == null ? null : p.pnl(), p == null ? null : p.returnPct());
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
