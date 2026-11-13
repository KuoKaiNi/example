package com.example.holdings;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.example.holdings.quote.MarketDataSource;

/** 所有 @SpringBootTest 都改用假行情，測試不會連到證交所或櫃買中心。 */
@Configuration
public class TestMarketDataConfig {
    public static final FakeMarketData FAKE = new FakeMarketData();

    @Bean
    @Primary
    MarketDataSource fakeMarketData() {
        return FAKE;
    }
}
