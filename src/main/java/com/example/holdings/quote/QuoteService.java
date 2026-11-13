package com.example.holdings.quote;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 包一層快取：
 * 1. 同一檔同一天只向官方查一次。例外：下午 3 點前查的，3 點後再查一次，才拿得到當天收盤價。
 * 2. 官方連不上時，1 分鐘內不再重試，免得清單頁每一筆都等 5 秒逾時。
 * 3. 連不上但之前查過，回之前的價格（日期照實回，畫面看得出是舊的）。
 */
public class QuoteService {
    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);
    static final LocalTime CLOSE_DATA_READY = LocalTime.of(15, 0);
    static final Duration RETRY_AFTER_FAILURE = Duration.ofMinutes(1);

    private final MarketDataSource source;
    private final Clock clock;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private volatile Instant sourceDownUntil = Instant.MIN;

    private record Cached(Optional<Quote> quote, ZonedDateTime fetchedAt) {}

    public QuoteService(MarketDataSource source, Clock clock) {
        this.source = source;
        this.clock = clock;
    }

    /** 新增持股時用：不知道市場別，先查上市再查上櫃。連不上丟 QuoteSourceException。 */
    public Optional<Quote> lookup(String code) {
        Optional<Quote> twse = fetch(code, Market.TWSE, true);
        return twse.isPresent() ? twse : fetch(code, Market.TPEX, true);
    }

    /** 清單頁用：查不到或連不上都回 empty，不讓整頁失敗。 */
    public Optional<Quote> latestOrEmpty(String code, Market market) {
        try {
            return fetch(code, market, false);
        } catch (QuoteSourceException e) {
            return Optional.empty();
        }
    }

    private Optional<Quote> fetch(String code, Market market, boolean throwWhenDown) {
        String key = market + ":" + code;
        Cached hit = cache.get(key);
        ZonedDateTime now = ZonedDateTime.now(clock);
        if (hit != null && fresh(hit.fetchedAt(), now)) return hit.quote();

        if (now.toInstant().isBefore(sourceDownUntil)) {
            return fallback(hit, throwWhenDown, null);
        }
        try {
            Optional<Quote> q = source.latest(code, market);
            cache.put(key, new Cached(q, now));
            return q;
        } catch (QuoteSourceException e) {
            log.warn("查 {} 行情失敗：{}", key, e.getMessage());
            sourceDownUntil = now.toInstant().plus(RETRY_AFTER_FAILURE);
            return fallback(hit, throwWhenDown, e);
        }
    }

    private Optional<Quote> fallback(Cached hit, boolean throwWhenDown, QuoteSourceException cause) {
        if (hit != null && hit.quote().isPresent()) return hit.quote();
        if (throwWhenDown) {
            throw cause != null ? cause : new QuoteSourceException("官方網站剛剛連不上，1 分鐘後再試");
        }
        return Optional.empty();
    }

    /** 清掉快取與「官方掛掉」的狀態（測試用）。 */
    public void clearCache() {
        cache.clear();
        sourceDownUntil = Instant.MIN;
    }

    static boolean fresh(ZonedDateTime fetchedAt, ZonedDateTime now) {
        LocalDate today = now.toLocalDate();
        if (!fetchedAt.toLocalDate().equals(today)) return false;
        boolean fetchedBeforeReady = fetchedAt.toLocalTime().isBefore(CLOSE_DATA_READY);
        boolean nowAfterReady = !now.toLocalTime().isBefore(CLOSE_DATA_READY);
        return !(fetchedBeforeReady && nowAfterReady);
    }
}
