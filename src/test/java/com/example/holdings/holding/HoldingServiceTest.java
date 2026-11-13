package com.example.holdings.holding;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;

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

    @Test
    void 新增後查得到() {
        Holding h = service.create(" 2330 ", 1, new BigDecimal("1000"));
        assertThat(h.getCode()).isEqualTo("2330");
        assertThat(service.list()).hasSize(1);
    }

    @Test
    void 同代號重複新增回409且不儲存() {
        service.create("2330", 1, new BigDecimal("1000"));
        assertThatThrownBy(() -> service.create("2330", 2, new BigDecimal("900")))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("DUPLICATE_CODE"));
        assertThat(service.list()).hasSize(1);
    }

    @Test
    void 欄位驗證() {
        assertThatThrownBy(() -> service.create("23", 1, BigDecimal.TEN)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.create("2330", 0, BigDecimal.TEN)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.create("2330", 1000, BigDecimal.TEN)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.create("2330", 1, new BigDecimal("10.123"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.create("2330", 1, BigDecimal.ZERO)).isInstanceOf(ApiException.class);
        assertThat(service.list()).isEmpty();
    }

    @Test
    void 新增時自動判斷上市或上櫃並記下名稱() {
        Holding twse = service.create("2330", 1, new BigDecimal("1000"));
        Holding tpex = service.create("6488", 1, new BigDecimal("700"));
        assertThat(twse.getMarket()).isEqualTo("TWSE");
        assertThat(twse.getName()).isEqualTo("台積電");
        assertThat(tpex.getMarket()).isEqualTo("TPEX");
        assertThat(tpex.getName()).isEqualTo("環球晶");
    }

    @Test
    void 上市上櫃都查不到回404且不儲存() {
        assertThatThrownBy(() -> service.create("9999", 1, BigDecimal.TEN))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("STOCK_NOT_FOUND"));
        assertThat(service.list()).isEmpty();
    }

    @Test
    void 官方網站掛掉時新增回503_清單照樣顯示未取得() {
        service.create("2330", 1, new BigDecimal("1000"));
        quotes.clearCache();
        TestMarketDataConfig.FAKE.down = true;
        assertThatThrownBy(() -> service.create("0050", 1, BigDecimal.TEN))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("QUOTE_SOURCE_DOWN"));
        var list = service.listWithQuotes();
        assertThat(list).hasSize(1);
        assertThat(list.get(0).quote()).isNull();
    }

    @Test
    void 修改與刪除() {
        long id = service.create("0050", 2, new BigDecimal("150")).getId();
        service.update(id, 3, new BigDecimal("148.5"));
        assertThat(service.get(id).getLots()).isEqualTo(3);
        service.delete(id);
        assertThatThrownBy(() -> service.get(id))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_FOUND"));
    }
}
