package com.example.holdings.quote;

import java.util.Optional;

public interface MarketDataSource {
    /**
     * 查某市場某檔最近收盤價。查無此股回 empty；連不上丟 QuoteSourceException。
     */
    Optional<Quote> latest(String code, Market market);
}
