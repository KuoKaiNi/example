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

@SpringBootTest
@AutoConfigureMockMvc
class ApiV2Test {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired QuoteService quotes;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM holding");
        quotes.clearCache();
        TestMarketDataConfig.FAKE.reset();
        TestMarketDataConfig.FAKE.put("2330", "台積電", Market.TWSE, "1100.00");
    }

    JsonNode read(ResultActions r) throws Exception {
        String s = r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return s.isEmpty() ? null : json.readTree(s);
    }

    ResultActions trade(String body) throws Exception {
        return mvc.perform(post("/api/v2/trades").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void 買兩次賣一次_回持股與每筆明細() throws Exception {
        trade("{\"code\":\"2330\",\"side\":\"BUY\",\"trade_date\":\"2026-10-01\",\"shares\":1000,\"price\":1000}").andExpect(status().isCreated());
        trade("{\"code\":\"2330\",\"side\":\"BUY\",\"trade_date\":\"2026-10-15\",\"shares\":500,\"price\":1100}").andExpect(status().isCreated());
        JsonNode r = read(trade("{\"code\":\"2330\",\"side\":\"SELL\",\"trade_date\":\"2026-10-20\",\"shares\":600,\"price\":1200}")
            .andExpect(status().isCreated()));
        assertThat(r.get("trade_id").isIntegralNumber()).isTrue();
        JsonNode h = r.get("holding");
        assertThat(h.get("shares").asLong()).isEqualTo(900);
        assertThat(h.get("realized_pnl").asLong()).isEqualTo(95_931);
        assertThat(h.get("cost").asLong()).isEqualTo(1_552_208 - 620_883);
        JsonNode sell = r.get("trades").get(2);
        assertThat(sell.get("side").asText()).isEqualTo("SELL");
        assertThat(sell.get("tax").asLong()).isEqualTo(2_160);
        assertThat(sell.get("realized_pnl").asLong()).isEqualTo(95_931);
        assertThat(sell.get("shares_after").asLong()).isEqualTo(900);

        JsonNode s = read(mvc.perform(get("/api/v2/summary")).andExpect(status().isOk()));
        assertThat(s.get("realized_pnl").asLong()).isEqualTo(95_931);
    }

    @Test
    void 錯誤情境() throws Exception {
        assertThat(read(trade("{\"code\":\"2330\",\"side\":\"SELL\",\"trade_date\":\"2026-10-01\",\"shares\":1,\"price\":1}")
            .andExpect(status().isConflict())).at("/error/code").asText()).isEqualTo("INSUFFICIENT_SHARES");
        assertThat(read(trade("{\"code\":\"2330\",\"side\":\"HOLD\",\"trade_date\":\"2026-10-01\",\"shares\":1,\"price\":1}")
            .andExpect(status().isBadRequest())).at("/error/code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(read(trade("{\"code\":\"2330\",\"side\":\"BUY\",\"trade_date\":\"2026/10/01\",\"shares\":1,\"price\":1}")
            .andExpect(status().isBadRequest())).at("/error/code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(read(trade("{\"code\":\"2330\",\"side\":\"BUY\",\"trade_date\":\"2026-10-01\",\"shares\":0.5,\"price\":1}")
            .andExpect(status().isBadRequest())).at("/error/code").asText()).isEqualTo("VALIDATION_ERROR");
        mvc.perform(delete("/api/v2/trades/999999")).andExpect(status().isNotFound());
    }

    @Test
    void 刪交易() throws Exception {
        long buy = read(trade("{\"code\":\"2330\",\"side\":\"BUY\",\"trade_date\":\"2026-10-01\",\"shares\":1000,\"price\":1000}")).get("trade_id").asLong();
        trade("{\"code\":\"2330\",\"side\":\"SELL\",\"trade_date\":\"2026-10-20\",\"shares\":600,\"price\":1200}");
        assertThat(read(mvc.perform(delete("/api/v2/trades/" + buy)).andExpect(status().isConflict()))
            .at("/error/code").asText()).isEqualTo("TRADE_CONFLICT");
    }
}
