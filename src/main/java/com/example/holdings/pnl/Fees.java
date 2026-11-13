package com.example.holdings.pnl;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 台股交易成本。
 * 手續費：成交金額 × 0.1425%，無條件捨去到元，每筆最低 20 元；買、賣各收一次。
 * 證交稅：只有賣出收；一般股票 0.3%，ETF（代號 00 開頭）0.1%；無條件捨去到元。
 */
public final class Fees {
    static final BigDecimal FEE_RATE = new BigDecimal("0.001425");
    static final long MIN_FEE = 20;
    static final BigDecimal STOCK_TAX = new BigDecimal("0.003");
    static final BigDecimal ETF_TAX = new BigDecimal("0.001");

    private Fees() {}

    /** 成交金額 = 價格 × 股數，無條件捨去到元（整張交易本來就是整數）。 */
    public static long amount(BigDecimal price, long shares) {
        return price.multiply(BigDecimal.valueOf(shares)).setScale(0, RoundingMode.FLOOR).longValueExact();
    }

    public static long fee(long amount) {
        long f = BigDecimal.valueOf(amount).multiply(FEE_RATE).setScale(0, RoundingMode.FLOOR).longValueExact();
        return Math.max(MIN_FEE, f);
    }

    public static long tax(String code, long amount) {
        BigDecimal rate = isEtf(code) ? ETF_TAX : STOCK_TAX;
        return BigDecimal.valueOf(amount).multiply(rate).setScale(0, RoundingMode.FLOOR).longValueExact();
    }

    /** 用伺服器存的代號判斷，不用使用者打的字。 */
    public static boolean isEtf(String code) {
        return code.startsWith("00");
    }

    /** 收益率（%）：四捨五入到小數第二位；成本為 0 時回 0。 */
    public static BigDecimal returnPct(long pnl, long cost) {
        if (cost == 0) return BigDecimal.ZERO.setScale(2);
        return BigDecimal.valueOf(pnl).multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(cost), 2, RoundingMode.HALF_UP);
    }
}
