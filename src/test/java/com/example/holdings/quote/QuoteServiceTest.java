package com.example.holdings.quote;

import static org.assertj.core.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.holdings.FakeMarketData;

class QuoteServiceTest {
    static final ZoneId TPE = ZoneId.of("Asia/Taipei");
    final FakeMarketData fake = new FakeMarketData();
    final MutableClock clock = new MutableClock(ZonedDateTime.of(2026, 11, 13, 10, 0, 0, 0, TPE));
    QuoteService service;

    @BeforeEach
    void setUp() {
        fake.put("2330", "台積電", Market.TWSE, "1100");
        service = new QuoteService(fake, clock);
    }

    @Test
    void 同一天同一檔只查一次() {
        service.latestOrEmpty("2330", Market.TWSE);
        clock.set(ZonedDateTime.of(2026, 11, 13, 14, 59, 0, 0, TPE));
        service.latestOrEmpty("2330", Market.TWSE);
        assertThat(fake.calls).isEqualTo(1);
    }

    @Test
    void 下午三點前查過_三點後再查一次拿當天收盤_之後不再查() {
        service.latestOrEmpty("2330", Market.TWSE);
        clock.set(ZonedDateTime.of(2026, 11, 13, 15, 1, 0, 0, TPE));
        service.latestOrEmpty("2330", Market.TWSE);
        service.latestOrEmpty("2330", Market.TWSE);
        assertThat(fake.calls).isEqualTo(2);
    }

    @Test
    void 隔天重新查() {
        service.latestOrEmpty("2330", Market.TWSE);
        clock.set(ZonedDateTime.of(2026, 11, 14, 9, 0, 0, 0, TPE));
        service.latestOrEmpty("2330", Market.TWSE);
        assertThat(fake.calls).isEqualTo(2);
    }

    @Test
    void 官方掛掉_清單回empty_一分鐘內不重試() {
        fake.down = true;
        assertThat(service.latestOrEmpty("2330", Market.TWSE)).isEmpty();
        assertThat(service.latestOrEmpty("0050", Market.TWSE)).isEmpty();
        assertThat(fake.calls).isEqualTo(1);
        clock.set(clock.now().plusMinutes(2));
        service.latestOrEmpty("0050", Market.TWSE);
        assertThat(fake.calls).isEqualTo(2);
    }

    @Test
    void 官方掛掉_新增時的查詢要丟例外_不能當成查無此股() {
        fake.down = true;
        assertThatThrownBy(() -> service.lookup("2330")).isInstanceOf(QuoteSourceException.class);
    }

    @Test
    void 官方掛掉但之前查過_回舊價格() {
        service.latestOrEmpty("2330", Market.TWSE);
        clock.set(ZonedDateTime.of(2026, 11, 14, 9, 0, 0, 0, TPE));
        fake.down = true;
        assertThat(service.latestOrEmpty("2330", Market.TWSE)).isPresent();
    }

    @Test
    void lookup先上市後上櫃() {
        fake.put("6488", "環球晶", Market.TPEX, "760");
        assertThat(service.lookup("6488").orElseThrow().market()).isEqualTo(Market.TPEX);
        assertThat(service.lookup("9999")).isEmpty();
    }

    static class MutableClock extends Clock {
        private ZonedDateTime now;
        MutableClock(ZonedDateTime now) { this.now = now; }
        void set(ZonedDateTime t) { now = t; }
        ZonedDateTime now() { return now; }
        @Override public ZoneId getZone() { return TPE; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.toInstant(); }
    }
}
