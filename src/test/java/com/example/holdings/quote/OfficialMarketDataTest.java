package com.example.holdings.quote;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** 用官方網站實際回傳的格式當樣本，不連網。 */
class OfficialMarketDataTest {
    static final String TWSE_2330 = """
        {"stat":"OK","date":"20261101","title":"115年11月 2330 台積電           各日成交資訊",
         "fields":["日期","成交股數","成交金額","開盤價","最高價","最低價","收盤價","漲跌價差","成交筆數","註記"],
         "data":[["115/11/02","41,165,068","95,950,896,264","2,310.00","2,345.00","2,300.00","2,330.00","+20.00","120,000",""],
                 ["115/11/03","30,000,000","70,000,000,000","2,330.00","2,340.00","2,310.00","2,315.00","-15.00","100,000",""]]}
        """;
    static final String TWSE_NOT_FOUND = "{\"stat\":\"很抱歉，沒有符合條件的資料!\",\"total\":0}";
    static final String TPEX_6488 = """
        {"tables":[{"title":"個股日成交資訊","subtitle":"6488 環球晶 115年11月","date":"20261101",
          "data":[["115/11/02","10,402","7,831,069","740.00","770.00","731.00","760.00","4.00","22,796"],
                  ["115/11/03","11,326","8,013,367","740.00","740.00","684.00","685.00","-75.00","39,962"],
                  ["115/11/04","0","0","--","--","--","--","--","0"]]}],
         "code":"6488","name":"環球晶","stat":"ok"}
        """;
    static final String TPEX_NOT_FOUND = """
        {"tables":[{"title":"個股日成交資訊","subtitle":"2330  115年11月","date":"20261101","data":[]}],
         "code":"2330","name":"","stat":"ok"}
        """;

    final Map<String, String> pages = new HashMap<>();
    final List<String> requested = new ArrayList<>();
    final Clock nov13 = Clock.fixed(ZonedDateTime.of(2026, 11, 13, 10, 0, 0, 0, ZoneId.of("Asia/Taipei")).toInstant(), ZoneId.of("Asia/Taipei"));
    final OfficialMarketData md = new OfficialMarketData(url -> {
        requested.add(url);
        return pages.entrySet().stream().filter(e -> url.contains(e.getKey())).map(Map.Entry::getValue)
            .findFirst().orElse(url.contains("twse") ? TWSE_NOT_FOUND : TPEX_NOT_FOUND);
    }, nov13);

    @Test
    void 上市_取最後一個交易日收盤價_民國年轉西元_千分位去掉() {
        pages.put("date=20261101&stockNo=2330", TWSE_2330);
        Quote q = md.latest("2330", Market.TWSE).orElseThrow();
        assertThat(q.close()).isEqualByComparingTo("2315.00");
        assertThat(q.date()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(q.name()).isEqualTo("台積電");
        assertThat(q.market()).isEqualTo(Market.TWSE);
    }

    @Test
    void 上櫃_沒成交的列是雙橫線要跳過() {
        pages.put("code=6488&date=2026/11/01", TPEX_6488);
        Quote q = md.latest("6488", Market.TPEX).orElseThrow();
        assertThat(q.close()).isEqualByComparingTo("685.00");
        assertThat(q.date()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(q.name()).isEqualTo("環球晶");
    }

    @Test
    void 代號送錯市場不會報錯_只是查不到() {
        assertThat(md.latest("2330", Market.TPEX)).isEmpty();
        assertThat(md.latest("6488", Market.TWSE)).isEmpty();
    }

    @Test
    void 當月還沒資料時查上個月() {
        pages.put("date=20261001&stockNo=2330", TWSE_2330.replace("115/11/0", "115/10/2"));
        Quote q = md.latest("2330", Market.TWSE).orElseThrow();
        assertThat(q.date()).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(requested).hasSize(2);
        assertThat(requested.get(0)).contains("date=20261101");
        assertThat(requested.get(1)).contains("date=20261001");
    }

    @Test
    void 回傳不是JSON時丟QuoteSourceException() {
        pages.put("stockNo=2330", "<html>系統維護中</html>");
        assertThatThrownBy(() -> md.latest("2330", Market.TWSE)).isInstanceOf(QuoteSourceException.class);
    }

    @Test
    void 小工具() {
        assertThat(OfficialMarketData.rocDate("115/06/05")).isEqualTo(LocalDate.of(2026, 6, 5));
        assertThat(OfficialMarketData.number("1,234.50")).isEqualByComparingTo(new BigDecimal("1234.50"));
        assertThat(OfficialMarketData.number("--")).isNull();
        assertThat(OfficialMarketData.number("")).isNull();
        assertThat(OfficialMarketData.nameAfterCode("2330  115年11月", "2330")).isEmpty();
    }
}
