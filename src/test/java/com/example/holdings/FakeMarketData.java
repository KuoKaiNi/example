package com.example.holdings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.example.holdings.quote.Market;
import com.example.holdings.quote.MarketDataSource;
import com.example.holdings.quote.Quote;
import com.example.holdings.quote.QuoteSourceException;

/** 測試用行情：不連網，價格由測試自己放。down=true 模擬官方網站掛掉。 */
public class FakeMarketData implements MarketDataSource {
    public final Map<String, Quote> quotes = new ConcurrentHashMap<>();
    public volatile boolean down;
    public int calls;

    public FakeMarketData put(String code, String name, Market market, String close) {
        quotes.put(market + ":" + code, new Quote(code, name, market, new BigDecimal(close), LocalDate.of(2026, 11, 12)));
        return this;
    }

    public void reset() {
        quotes.clear();
        down = false;
        calls = 0;
    }

    @Override
    public synchronized Optional<Quote> latest(String code, Market market) {
        calls++;
        if (down) throw new QuoteSourceException("測試：官方網站掛了");
        return Optional.ofNullable(quotes.get(market + ":" + code));
    }
}
