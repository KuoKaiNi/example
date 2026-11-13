package com.example.holdings;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 模擬上線：資料庫停在 1.0（V1），裡面有使用者的資料，然後用 2.0 啟動跑剩下的 migration。
 * id 不能變，張數要正確換成股數。
 */
class MigrationTest {
    @Test
    void 從1點0的資料庫升級_持股id不變_張數轉成股數() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(ds);

        Flyway.configure().dataSource(ds).target("1").load().migrate();
        jdbc.update("INSERT INTO holding (id, code, name, market, lots, buy_price, created_at) VALUES "
            + "(7, '2330', '台積電', 'TWSE', 1, 1000.00, '2026-11-01 10:00:00'),"
            + "(9, '0050', '元大台灣50', 'TWSE', 2, 150.00, '2026-11-02 10:00:00'),"
            + "(12, '6488', NULL, NULL, 3, 700.50, '2026-11-03 10:00:00')");

        Flyway.configure().dataSource(ds).load().migrate();

        assertThat(jdbc.queryForList("SELECT id FROM holding ORDER BY id", Long.class)).containsExactly(7L, 9L, 12L);
        List<Map<String, Object>> trades = jdbc.queryForList(
            "SELECT holding_id, side, trade_date, shares, price FROM trade ORDER BY holding_id");
        assertThat(trades).hasSize(3);
        assertThat(trades.get(0)).containsEntry("side", "BUY").containsEntry("shares", 1000);
        assertThat(trades.get(1)).containsEntry("shares", 2000);
        assertThat(trades.get(2)).containsEntry("shares", 3000);
        assertThat(trades.get(2).get("price").toString()).isEqualTo("700.50");
        assertThat(trades.get(0).get("trade_date").toString()).isEqualTo("2026-11-01");
        // 舊欄位已移除
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = 'holding'",
            String.class)).doesNotContain("lots", "buy_price");
    }
}
