package com.example.holdings.pnl;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.holdings.pnl.Position.Entry;
import com.example.holdings.pnl.Position.Side;

/** 預期值是照規則用 Python 另外算的（算式寫在註解），不是從程式輸出抄的。 */
class PositionTest {
    static Entry buy(long id, int day, long shares, String price) {
        return new Entry(id, Side.BUY, LocalDate.of(2026, 10, day), shares, new BigDecimal(price));
    }

    static Entry sell(long id, int day, long shares, String price) {
        return new Entry(id, Side.SELL, LocalDate.of(2026, 10, day), shares, new BigDecimal(price));
    }

    @Test
    void 只有一筆買進時跟1點0的算法一樣() {
        Position p = Position.replay("2330", List.of(buy(1, 1, 1000, "1000")));
        Pnl u = p.unrealized(new BigDecimal("1100"));
        Pnl v1 = Pnl.unrealized("2330", 1000, new BigDecimal("1000"), new BigDecimal("1100"));
        assertThat(u.pnl()).isEqualTo(v1.pnl()).isEqualTo(93_708);
        assertThat(u.returnPct()).isEqualByComparingTo(v1.returnPct());
        assertThat(p.avgBuyPrice()).isEqualByComparingTo("1000.00");
    }

    @Test
    void 分兩次買_平均成本含手續費() {
        // 1000 股 @1000：1,000,000 + 1,425 ；500 股 @1100：550,000 + 783
        Position p = Position.replay("2330", List.of(buy(1, 1, 1000, "1000"), buy(2, 15, 500, "1100")));
        assertThat(p.shares()).isEqualTo(1500);
        assertThat(p.cost()).isEqualTo(1_552_208);
        assertThat(p.avgCost()).isEqualByComparingTo("1034.81");      // 1,552,208 ÷ 1,500
        assertThat(p.avgBuyPrice()).isEqualByComparingTo("1033.33");  // 1,550,000 ÷ 1,500
        // 收盤 1100：市值 1,650,000，手續費 2,351，稅 4,950
        assertThat(p.unrealized(new BigDecimal("1100")).pnl()).isEqualTo(1_650_000 - 2_351 - 4_950 - 1_552_208);
    }

    @Test
    void 部分賣出_已實現用移動平均成本() {
        // 先買 1500 股（成本 1,552,208），賣 600 股 @1200：
        // 金額 720,000、手續費 1,026、稅 2,160；分攤成本 1,552,208×600/1500 = 620,883.2 → 620,883
        // 已實現 = 720,000 − 1,026 − 2,160 − 620,883 = 95,931
        Position p = Position.replay("2330", List.of(buy(1, 1, 1000, "1000"), buy(2, 15, 500, "1100"),
            sell(3, 20, 600, "1200")));
        assertThat(p.realized()).isEqualTo(95_931);
        assertThat(p.shares()).isEqualTo(900);
        assertThat(p.cost()).isEqualTo(1_552_208 - 620_883);
        assertThat(p.lines().get(2).tax()).isEqualTo(2_160);
    }

    @Test
    void 全部賣光_成本歸零_沒有未實現() {
        Position p = Position.replay("0050", List.of(buy(1, 1, 2000, "150"), sell(2, 2, 2000, "145.5")));
        // 賣出：291,000 − 414 − 291 − 300,427 = −10,132（跟 1.0 例二一樣）
        assertThat(p.realized()).isEqualTo(-10_132);
        assertThat(p.shares()).isZero();
        assertThat(p.cost()).isZero();
        assertThat(p.avgCost()).isNull();
        assertThat(p.unrealized(new BigDecimal("150"))).isNull();
    }

    @Test
    void 零股手續費一樣最低20元() {
        // 10 股 @50 = 500，手續費 0.7125 → 最低 20
        Position p = Position.replay("2330", List.of(buy(1, 1, 10, "50")));
        assertThat(p.lines().get(0).fee()).isEqualTo(20);
        assertThat(p.cost()).isEqualTo(520);
    }

    @Test
    void 賣超過當時持有股數丟例外() {
        assertThatThrownBy(() -> Position.replay("2330", List.of(buy(1, 1, 1000, "1000"), sell(2, 2, 1001, "1000"))))
            .isInstanceOf(Position.InsufficientSharesException.class)
            .hasMessageContaining("只持有 1000 股");
    }
}
