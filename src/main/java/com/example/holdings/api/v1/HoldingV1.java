package com.example.holdings.api.v1;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.example.holdings.holding.HoldingView;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** App 1.0 的持股物件。欄位、型別、名稱都不能改。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.ALWAYS)
public record HoldingV1(long id, String code, String name, String market, int lots, BigDecimal buyPrice,
                        BigDecimal currentPrice, LocalDate priceDate, Long pnl, BigDecimal returnPct) {

    public static HoldingV1 from(HoldingView v) {
        var h = v.holding();
        var q = v.quote();
        var p = v.pnl();
        return new HoldingV1(h.getId(), h.getCode(), nz(h.getName()), nz(h.getMarket()), h.getLots(), h.getBuyPrice(),
            q == null ? null : q.close(), q == null ? null : q.date(),
            p == null ? null : p.pnl(), p == null ? null : p.returnPct());
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
