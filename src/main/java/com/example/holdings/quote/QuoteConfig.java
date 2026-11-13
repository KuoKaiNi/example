package com.example.holdings.quote;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuoteConfig {
    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of("Asia/Taipei"));
    }

    @Bean
    MarketDataSource marketDataSource(Clock clock) {
        return new OfficialMarketData(OfficialMarketData.httpFetcher(), clock);
    }

    @Bean
    QuoteService quoteService(MarketDataSource source, Clock clock) {
        return new QuoteService(source, clock);
    }
}
