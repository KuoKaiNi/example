package com.example.holdings.holding;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.holdings.TestMarketDataConfig;
import com.example.holdings.common.ApiException;
import com.example.holdings.quote.Market;
import com.example.holdings.quote.QuoteService;

@SpringBootTest
class HoldingServiceTest {
    static final LocalDate D1 = LocalDate.of(2026, 10, 1);
    static final LocalDate D2 = LocalDate.of(2026, 10, 15);

    @Autowired HoldingService service;
    @Autowired HoldingRepository repo;
    @Autowired QuoteService quotes;

    @BeforeEach
    void clean() {
        repo.deleteAll();
        quotes.clearCache();
        TestMarketDataConfig.FAKE.reset();
        TestMarketDataConfig.FAKE
            .put("2330", "台積電", Market.TWSE, "1100.00")
            .put("0050", "元大台灣50", Market.TWSE, "145.50")
            .put("6488", "環球晶", Market.TPEX, "760.00");
    }

    static void assertCode(Throwable t, String code) {
        assertThat(t).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }

    @Test
    void 買進新代號會建主檔_自動判斷上市上櫃() {
        service.addTrade("2330", Trade.Side.BUY, D1, 1000, new BigDecimal("1000"));
        service.addTrade("6488", Trade.Side.BUY, D1, 300, new BigDecimal("700"));
        var list = service.list(false);
        assertThat(list).extracting(v -> v.holding().getMarket()).containsExactly("TWSE", "TPEX");
        assertThat(list).extracting(v -> v.holding().getName()).containsExactly("台積電", "環球晶");
        assertThat(list.get(1).position().shares()).isEqualTo(300);
    }

    @Test
    void 同代號分次買進合成一檔() {
        service.addTrade("2330", Trade.Side.BUY, D1, 1000, new BigDecimal("1000"));
        service.addTrade("2330", Trade.Side.BUY, D2, 500, new BigDecimal("1100"));
        assertThat(service.list(false)).hasSize(1);
        assertThat(service.list(false).get(0).position().shares()).isEqualTo(1500);
    }

    @Test
    void 賣超過持有股數回409() {
        service.addTrade("2330", Trade.Side.BUY, D1, 1000, new BigDecimal("1000"));
        assertCode(catchThrowable(() -> service.addTrade("2330", Trade.Side.SELL, D2, 1001, new BigDecimal("1100"))),
            "INSUFFICIENT_SHARES");
        assertCode(catchThrowable(() -> service.addTrade("0050", Trade.Side.SELL, D2, 1, new BigDecimal("100"))),
            "INSUFFICIENT_SHARES");
    }

    @Test
    void 補記更早日期的賣出_要以當時股數檢查() {
        service.addTrade("2330", Trade.Side.BUY, D2, 1000, new BigDecimal("1000"));
        // 10/1 那時還沒買，不能賣
        assertCode(catchThrowable(() -> service.addTrade("2330", Trade.Side.SELL, D1, 100, new BigDecimal("900"))),
            "INSUFFICIENT_SHARES");
    }

    @Test
    void 刪掉買進會讓後面的賣出不成立_回409() {
        Trade buy = service.addTrade("2330", Trade.Side.BUY, D1, 1000, new BigDecimal("1000"));
        service.addTrade("2330", Trade.Side.SELL, D2, 600, new BigDecimal("1100"));
        assertCode(catchThrowable(() -> service.deleteTrade(buy.getId())), "TRADE_CONFLICT");
    }

    @Test
    void 刪掉最後一筆交易時整檔一起刪() {
        Trade buy = service.addTrade("2330", Trade.Side.BUY, D1, 1000, new BigDecimal("1000"));
        assertThat(service.deleteTrade(buy.getId())).isTrue();
        assertThat(service.list(true)).isEmpty();
    }

    @Test
    void 不能記未來日期() {
        assertCode(catchThrowable(() -> service.addTrade("2330", Trade.Side.BUY, LocalDate.now().plusDays(2), 1,
            BigDecimal.TEN)), "VALIDATION_ERROR");
    }

    @Test
    void 上市上櫃都查不到回404_官方掛掉回503() {
        assertCode(catchThrowable(() -> service.addTrade("9999", Trade.Side.BUY, D1, 1, BigDecimal.TEN)), "STOCK_NOT_FOUND");
        TestMarketDataConfig.FAKE.down = true;
        assertCode(catchThrowable(() -> service.addTrade("0050", Trade.Side.BUY, D1, 1, BigDecimal.TEN)), "QUOTE_SOURCE_DOWN");
        assertThat(service.list(true)).isEmpty();
    }

    @Test
    void 官方網站掛掉時清單照樣顯示_收盤價為null() {
        service.addTrade("2330", Trade.Side.BUY, D1, 1000, new BigDecimal("1000"));
        quotes.clearCache();
        TestMarketDataConfig.FAKE.down = true;
        var list = service.list(false);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).quote()).isNull();
        assertThat(list.get(0).unrealized()).isNull();
    }
}
