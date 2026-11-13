package com.example.holdings.api;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

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
 * App 1.0 的契約。App 已發布不會再改，所以這個檔案在 v1.0.0 之後「只能加、不能改」：
 * 任何版本都要讓這裡全部通過。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiV1ContractTest {
    static final Set<String> HOLDING_FIELDS = Set.of("id", "code", "name", "market", "lots", "buy_price",
        "current_price", "price_date", "pnl", "return_pct");
    static final Set<String> SUMMARY_FIELDS = Set.of("total_cost", "market_value", "pnl", "return_pct", "quote_missing");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired QuoteService quotes;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM holding");
        quotes.clearCache();
        TestMarketDataConfig.FAKE.reset();
        TestMarketDataConfig.FAKE
            .put("2330", "台積電", Market.TWSE, "1100.00")
            .put("0050", "元大台灣50", Market.TWSE, "145.50")
            .put("6488", "環球晶", Market.TPEX, "760.00");
    }

    JsonNode call(ResultActions r) throws Exception {
        String body = r.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return body.isEmpty() ? null : json.readTree(body);
    }

    ResultActions create(String body) throws Exception {
        return mvc.perform(post("/api/v1/holdings").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    ResultActions update(long id, String body) throws Exception {
        return mvc.perform(put("/api/v1/holdings/" + id).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    static Set<String> fields(JsonNode n) {
        Set<String> s = new TreeSet<>();
        for (Iterator<String> it = n.fieldNames(); it.hasNext();) s.add(it.next());
        return s;
    }

    static void assertHoldingShape(JsonNode h) {
        assertThat(fields(h)).containsAll(HOLDING_FIELDS);
        assertThat(h.get("id").isIntegralNumber()).isTrue();
        assertThat(h.get("code").isTextual()).isTrue();
        assertThat(h.get("name").isTextual()).isTrue();
        assertThat(h.get("market").asText()).isIn("TWSE", "TPEX");
        assertThat(h.get("lots").isInt()).as("lots 要是整數").isTrue();
        assertThat(h.get("buy_price").isNumber()).isTrue();
        for (String f : new String[] {"current_price", "return_pct"}) {
            assertThat(h.get(f).isNumber() || h.get(f).isNull()).as(f).isTrue();
        }
        assertThat(h.get("pnl").isIntegralNumber() || h.get("pnl").isNull()).isTrue();
        assertThat(h.get("price_date").isNull() || h.get("price_date").asText().matches("\\d{4}-\\d{2}-\\d{2}")).isTrue();
    }

    static void assertError(JsonNode body, String code) {
        assertThat(fields(body)).containsExactly("error");
        assertThat(body.at("/error/code").asText()).isEqualTo(code);
        assertThat(body.at("/error/message").asText()).isNotBlank();
    }

    @Test
    void 空清單回空陣列() throws Exception {
        JsonNode list = call(mvc.perform(get("/api/v1/holdings")).andExpect(status().isOk()));
        assertThat(list.isArray()).isTrue();
        assertThat(list).isEmpty();
    }

    @Test
    void 新增_回201與完整持股物件_數字與考題例子相同() throws Exception {
        JsonNode h = call(create("{\"code\":\"2330\",\"lots\":1,\"buy_price\":1000}").andExpect(status().isCreated()));
        assertHoldingShape(h);
        assertThat(h.get("code").asText()).isEqualTo("2330");
        assertThat(h.get("name").asText()).isEqualTo("台積電");
        assertThat(h.get("market").asText()).isEqualTo("TWSE");
        assertThat(h.get("lots").asInt()).isEqualTo(1);
        assertThat(h.get("buy_price").decimalValue()).isEqualByComparingTo("1000");
        assertThat(h.get("current_price").decimalValue()).isEqualByComparingTo("1100");
        assertThat(h.get("price_date").asText()).isEqualTo("2026-11-12");
        assertThat(h.get("pnl").asLong()).isEqualTo(93_708);
        assertThat(h.get("return_pct").decimalValue()).isEqualByComparingTo("9.36");
    }

    @Test
    void 上櫃股票的market是TPEX() throws Exception {
        JsonNode h = call(create("{\"code\":\"6488\",\"lots\":1,\"buy_price\":700}").andExpect(status().isCreated()));
        assertThat(h.get("market").asText()).isEqualTo("TPEX");
    }

    @Test
    void 查單筆與清單() throws Exception {
        long id = call(create("{\"code\":\"0050\",\"lots\":2,\"buy_price\":150}")).get("id").asLong();
        JsonNode one = call(mvc.perform(get("/api/v1/holdings/" + id)).andExpect(status().isOk()));
        assertHoldingShape(one);
        assertThat(one.get("pnl").asLong()).isEqualTo(-10_132);
        JsonNode list = call(mvc.perform(get("/api/v1/holdings")).andExpect(status().isOk()));
        assertThat(list).hasSize(1);
        assertHoldingShape(list.get(0));
    }

    @Test
    void 錯誤情境() throws Exception {
        create("{\"code\":\"2330\",\"lots\":1,\"buy_price\":1000}");
        assertError(call(create("{\"code\":\"2330\",\"lots\":1,\"buy_price\":1000}").andExpect(status().isConflict())), "DUPLICATE_CODE");
        assertError(call(create("{\"code\":\"9999\",\"lots\":1,\"buy_price\":10}").andExpect(status().isNotFound())), "STOCK_NOT_FOUND");
        assertError(call(create("{\"code\":\"0050\",\"lots\":0,\"buy_price\":10}").andExpect(status().isBadRequest())), "VALIDATION_ERROR");
        assertError(call(create("{\"code\":\"0050\",\"lots\":1.5,\"buy_price\":10}").andExpect(status().isBadRequest())), "VALIDATION_ERROR");
        assertError(call(create("{\"code\":\"0050\",\"lots\":1}").andExpect(status().isBadRequest())), "VALIDATION_ERROR");
        assertError(call(create("不是 JSON").andExpect(status().isBadRequest())), "VALIDATION_ERROR");
        assertError(call(mvc.perform(get("/api/v1/holdings/999999")).andExpect(status().isNotFound())), "NOT_FOUND");
        assertError(call(update(999999, "{\"lots\":1,\"buy_price\":10}").andExpect(status().isNotFound())), "NOT_FOUND");
        assertError(call(mvc.perform(delete("/api/v1/holdings/999999")).andExpect(status().isNotFound())), "NOT_FOUND");
    }

    @Test
    void 修改_代號不能改() throws Exception {
        long id = call(create("{\"code\":\"2330\",\"lots\":1,\"buy_price\":1000}")).get("id").asLong();
        JsonNode h = call(update(id, "{\"code\":\"0050\",\"lots\":2,\"buy_price\":980.5}").andExpect(status().isOk()));
        assertHoldingShape(h);
        assertThat(h.get("id").asLong()).isEqualTo(id);
        assertThat(h.get("code").asText()).isEqualTo("2330");
        assertThat(h.get("lots").asInt()).isEqualTo(2);
        assertThat(h.get("buy_price").decimalValue()).isEqualByComparingTo("980.5");
        assertError(call(update(id, "{\"lots\":1000,\"buy_price\":10}").andExpect(status().isBadRequest())), "VALIDATION_ERROR");
    }

    @Test
    void 刪除回204沒有內容() throws Exception {
        long id = call(create("{\"code\":\"2330\",\"lots\":1,\"buy_price\":1000}")).get("id").asLong();
        String body = mvc.perform(delete("/api/v1/holdings/" + id)).andExpect(status().isNoContent())
            .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).isEmpty();
        mvc.perform(get("/api/v1/holdings/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void 總計_用規格上的例子() throws Exception {
        create("{\"code\":\"2330\",\"lots\":1,\"buy_price\":1000}");
        create("{\"code\":\"0050\",\"lots\":2,\"buy_price\":150}");
        JsonNode s = call(mvc.perform(get("/api/v1/summary")).andExpect(status().isOk()));
        assertThat(fields(s)).containsAll(SUMMARY_FIELDS);
        assertThat(s.get("total_cost").asLong()).isEqualTo(1_301_852);
        assertThat(s.get("market_value").asLong()).isEqualTo(1_391_000);
        assertThat(s.get("pnl").asLong()).isEqualTo(83_576);
        assertThat(s.get("return_pct").decimalValue()).isEqualByComparingTo("6.42");
        assertThat(s.get("quote_missing").asInt()).isZero();
    }

    @Test
    void 官方網站掛掉_清單照樣回200_價格欄位為null_總計記一筆缺價() throws Exception {
        create("{\"code\":\"2330\",\"lots\":1,\"buy_price\":1000}");
        quotes.clearCache();
        TestMarketDataConfig.FAKE.down = true;
        JsonNode h = call(mvc.perform(get("/api/v1/holdings")).andExpect(status().isOk())).get(0);
        assertHoldingShape(h);
        assertThat(h.get("current_price").isNull()).isTrue();
        assertThat(h.get("price_date").isNull()).isTrue();
        assertThat(h.get("pnl").isNull()).isTrue();
        assertThat(h.get("return_pct").isNull()).isTrue();
        JsonNode s = call(mvc.perform(get("/api/v1/summary")).andExpect(status().isOk()));
        assertThat(s.get("quote_missing").asInt()).isEqualTo(1);
        assertThat(s.get("total_cost").asLong()).isEqualTo(1_001_425);
    }
}
