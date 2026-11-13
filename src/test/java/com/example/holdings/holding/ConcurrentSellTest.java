package com.example.holdings.holding;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.holdings.TestMarketDataConfig;
import com.example.holdings.common.ApiException;
import com.example.holdings.quote.Market;

/** 持有 1000 股，兩個請求同時各賣 700 股：只能有一個成功。 */
@SpringBootTest
class ConcurrentSellTest {
    @Autowired HoldingService service;
    @Autowired HoldingRepository repo;
    @Autowired com.example.holdings.quote.QuoteService quotes;

    @Test
    void 同時兩筆賣出_不會賣超過() throws Exception {
        repo.deleteAll();
        quotes.clearCache();
        TestMarketDataConfig.FAKE.reset();
        TestMarketDataConfig.FAKE.put("2330", "台積電", Market.TWSE, "1100");
        service.addTrade("2330", Trade.Side.BUY, LocalDate.of(2026, 10, 1), 1000, new BigDecimal("1000"));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    service.addTrade("2330", Trade.Side.SELL, LocalDate.of(2026, 10, 2), 700, new BigDecimal("1100"));
                    return "OK";
                } catch (ApiException e) {
                    return e.getCode();
                }
            }));
        }
        start.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<String> f : results) outcomes.add(f.get());
        pool.shutdown();

        assertThat(outcomes).containsExactlyInAnyOrder("OK", "INSUFFICIENT_SHARES");
        assertThat(service.list(false).get(0).position().shares()).isEqualTo(300);
    }
}
