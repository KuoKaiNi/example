package com.example.holdings.api;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.example.holdings.TestMarketDataConfig;
import com.example.holdings.quote.Market;
import com.example.holdings.quote.QuoteService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 2.0 網頁記了零股、多筆買賣之後，App 1.0 看到的樣子。
 * 規則寫在 UPGRADE.md「App 1.0 會看到什麼」。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiV1CompatTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired QuoteService quotes;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM holding");
        quotes.clearCache();
        TestMarketDataConfig.FAKE.reset();
        TestMarketDataConfig.FAKE.put("2330", "台積電", Market.TWSE, "1100.00").put("0050", "元大台灣50", Market.TWSE, "145.50");
    }

    JsonNode read(ResultActions r) throws Exception {
        String s = r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return s.isEmpty() ? null : json.readTree(s);
    }

    ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b, String body) throws Exception {
        return mvc.perform(b.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    long v1Create(String code, int lots, String price) throws Exception {
        return read(send(post("/api/v1/holdings"), "{\"code\":\"" + code + "\",\"lots\":" + lots + ",\"buy_price\":" + price + "}")
            .andExpect(status().isCreated())).get("id").asLong();
    }

    void v2Trade(String code, String side, String date, int shares, String price) throws Exception {
        send(post("/api/v2/trades"), "{\"code\":\"" + code + "\",\"side\":\"" + side + "\",\"trade_date\":\"" + date
            + "\",\"shares\":" + shares + ",\"price\":" + price + "}").andExpect(status().isCreated());
    }

    @Test
    void 零股_lots無條件捨去成整數_pnl算全部股數() throws Exception {
        long id = v1Create("2330", 1, "1000");
        v2Trade("2330", "BUY", "2026-10-15", 500, "1100");
        JsonNode h = read(mvc.perform(get("/api/v1/holdings/" + id)).andExpect(status().isOk()));
        assertThat(h.get("id").asLong()).isEqualTo(id);
        assertThat(h.get("lots").isInt()).isTrue();
        assertThat(h.get("lots").asInt()).isEqualTo(1);                       // 1500 股 → 1 張
        assertThat(h.get("buy_price").decimalValue()).isEqualByComparingTo("1033.33");
        assertThat(h.get("pnl").asLong()).isEqualTo(1_650_000 - 2_351 - 4_950 - 1_552_208);
    }

    @Test
    void 只剩零股時lots是0() throws Exception {
        long id = v1Create("2330", 1, "1000");
        v2Trade("2330", "SELL", java.time.LocalDate.now().toString(), 700, "1100");
        JsonNode h = read(mvc.perform(get("/api/v1/holdings/" + id)).andExpect(status().isOk()));
        assertThat(h.get("lots").asInt()).isZero();
        assertThat(h.get("pnl").isIntegralNumber()).isTrue();
    }

    @Test
    void 有多筆買賣時App1點0不能修改_回409與可顯示的訊息() throws Exception {
        long id = v1Create("2330", 1, "1000");
        v2Trade("2330", "BUY", "2026-10-15", 500, "1100");
        JsonNode e = read(send(put("/api/v1/holdings/" + id), "{\"lots\":3,\"buy_price\":900}").andExpect(status().isConflict()));
        assertThat(e.at("/error/code").asText()).isEqualTo("MULTIPLE_TRADES");
        assertThat(e.at("/error/message").asText()).contains("網頁版");
        // 資料沒有被改到
        JsonNode h = read(mvc.perform(get("/api/v1/holdings/" + id)));
        assertThat(h.get("lots").asInt()).isEqualTo(1);
        assertThat(h.get("buy_price").decimalValue()).isEqualByComparingTo("1033.33");
    }

    @Test
    void 只有一筆買進時App1點0照樣能改() throws Exception {
        long id = v1Create("2330", 1, "1000");
        JsonNode h = read(send(put("/api/v1/holdings/" + id), "{\"lots\":3,\"buy_price\":900}").andExpect(status().isOk()));
        assertThat(h.get("lots").asInt()).isEqualTo(3);
        JsonNode d = read(mvc.perform(get("/api/v2/holdings/" + id)));
        assertThat(d.at("/trades/0/shares").asLong()).isEqualTo(3000);
    }

    @Test
    void 在2點0全部賣掉的_App1點0看不到_同代號再新增會沿用原本的id() throws Exception {
        long id = v1Create("0050", 2, "150");
        v2Trade("0050", "SELL", java.time.LocalDate.now().toString(), 2000, "145.5");
        assertThat(read(mvc.perform(get("/api/v1/holdings")))).isEmpty();
        mvc.perform(get("/api/v1/holdings/" + id)).andExpect(status().isNotFound());
        assertThat(read(mvc.perform(get("/api/v1/summary"))).get("total_cost").asLong()).isZero();
        long again = v1Create("0050", 1, "140");
        assertThat(again).isEqualTo(id);
        // 2.0 看得到完整歷史：先前的已實現損益還在
        JsonNode d = read(mvc.perform(get("/api/v2/holdings/" + id)));
        assertThat(d.at("/holding/realized_pnl").asLong()).isEqualTo(-10_132);
        assertThat(d.get("trades")).hasSize(3);
    }

    @Test
    void 從1點0轉來的買進_改成實際日期後可以補記更早的賣出() throws Exception {
        long id = v1Create("2330", 1, "1000");
        long buyId = read(mvc.perform(get("/api/v2/holdings/" + id))).at("/trades/0/id").asLong();
        send(post("/api/v2/trades"), "{\"code\":\"2330\",\"side\":\"SELL\",\"trade_date\":\"2026-09-01\",\"shares\":100,\"price\":900}")
            .andExpect(status().isConflict());
        send(put("/api/v2/trades/" + buyId), "{\"trade_date\":\"2026-08-01\",\"shares\":1000,\"price\":1000}")
            .andExpect(status().isOk());
        v2Trade("2330", "SELL", "2026-09-01", 100, "900");
        // id 沒變，App 1.0 照樣看得到
        assertThat(read(mvc.perform(get("/api/v1/holdings/" + id)).andExpect(status().isOk())).get("lots").asInt()).isZero();
    }

    @Test
    void App1點0刪除_整檔連同買賣紀錄一起刪() throws Exception {
        long id = v1Create("2330", 1, "1000");
        v2Trade("2330", "BUY", "2026-10-15", 500, "1100");
        mvc.perform(delete("/api/v1/holdings/" + id)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trade", Integer.class)).isZero();
    }

    @Test
    void summary只算未實現_已實現不混進去() throws Exception {
        v1Create("2330", 1, "1000");
        v2Trade("2330", "SELL", java.time.LocalDate.now().toString(), 500, "1200");
        JsonNode s = read(mvc.perform(get("/api/v1/summary")));
        // 剩 500 股：成本 1,001,425 − 500,713 = 500,712；市值 550,000；費用 783、稅 1,650
        assertThat(s.get("total_cost").asLong()).isEqualTo(500_712);
        assertThat(s.get("pnl").asLong()).isEqualTo(550_000 - 783 - 1_650 - 500_712);
    }
}
