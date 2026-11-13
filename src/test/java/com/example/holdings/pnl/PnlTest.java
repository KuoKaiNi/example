package com.example.holdings.pnl;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/** 預期值是照考題規則用另一套算法（Python）獨立算出來的，不是從程式輸出抄的。 */
class PnlTest {
    static Pnl lots(String code, int lots, String buy, String close) {
        return Pnl.unrealized(code, lots * 1000L, new BigDecimal(buy), new BigDecimal(close));
    }

    @Test
    void 例一_一般股票() {
        Pnl p = lots("2330", 1, "1000.00", "1100.00");
        assertThat(p.buyFee()).isEqualTo(1425);
        assertThat(p.sellFee()).isEqualTo(1567);      // 1,567.5 無條件捨去
        assertThat(p.tax()).isEqualTo(3300);          // 0.3%
        assertThat(p.pnl()).isEqualTo(93_708);
        assertThat(p.returnPct()).isEqualByComparingTo("9.36");
    }

    @Test
    void 例二_ETF證交稅千分之一() {
        Pnl p = lots("0050", 2, "150.00", "145.50");
        assertThat(p.tax()).isEqualTo(291);           // 0.1%，不是 873
        assertThat(p.pnl()).isEqualTo(-10_132);
        assertThat(p.returnPct()).isEqualByComparingTo("-3.37");
    }

    @Test
    void 例三_手續費低於20元收20元() {
        Pnl p = lots("2363", 1, "10.00", "10.50");
        assertThat(p.buyFee()).isEqualTo(20);         // 14.25 → 20
        assertThat(p.sellFee()).isEqualTo(20);        // 14.96 → 20
        assertThat(p.pnl()).isEqualTo(429);
        assertThat(p.returnPct()).isEqualByComparingTo("4.28");
    }

    @Test
    void 不賺不賠時收益率是負的_因為有費用() {
        Pnl p = lots("2330", 1, "1000", "1000");
        assertThat(p.pnl()).isEqualTo(-1425 - 1425 - 3000);
    }

    @Test
    void ETF判斷看00開頭() {
        assertThat(Fees.isEtf("0050")).isTrue();
        assertThat(Fees.isEtf("00878")).isTrue();
        assertThat(Fees.isEtf("2330")).isFalse();
        assertThat(Fees.isEtf("1101")).isFalse();
    }

    @Test
    void 總計_用API規格上的例子() {
        Pnl a = lots("2330", 1, "1000.00", "1100.00");
        Pnl b = lots("0050", 2, "150.00", "145.50");
        Summary s = new Summary.Builder().add(a.cost(), a).add(b.cost(), b).build();
        assertThat(s.totalCost()).isEqualTo(1_301_852);
        assertThat(s.marketValue()).isEqualTo(1_391_000);
        assertThat(s.pnl()).isEqualTo(83_576);
        assertThat(s.returnPct()).isEqualByComparingTo("6.42");
        assertThat(s.quoteMissing()).isZero();
    }

    @Test
    void 總計_取不到收盤價的只算成本() {
        Pnl a = lots("2330", 1, "1000.00", "1100.00");
        Summary s = new Summary.Builder().add(a.cost(), a).add(300_427, null).build();
        assertThat(s.totalCost()).isEqualTo(1_001_425 + 300_427);
        assertThat(s.marketValue()).isEqualTo(1_100_000);
        assertThat(s.pnl()).isEqualTo(93_708);
        assertThat(s.quoteMissing()).isEqualTo(1);
    }
}
