package com.example.holdings.holding;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.example.holdings.common.ApiException;
import com.example.holdings.pnl.Position;
import com.example.holdings.pnl.Summary;
import com.example.holdings.quote.Market;
import com.example.holdings.quote.Quote;
import com.example.holdings.quote.QuoteService;
import com.example.holdings.quote.QuoteSourceException;

/**
 * 寫入交易的方法一律「先鎖持股主檔、再讀交易紀錄、重算、寫入」，並用 READ_COMMITTED：
 * MySQL 預設 REPEATABLE READ 會沿用交易開始時的快照，就算等到鎖，也讀不到對方剛 commit 的賣出，
 * 兩筆同時賣出會一起通過檢查（H2 預設 READ COMMITTED，單元測試抓不到，是在 MySQL 實測才發現的）。
 */
@Service
public class HoldingService {
    private final HoldingRepository holdings;
    private final TradeRepository trades;
    private final QuoteService quotes;
    private final Clock clock;

    public HoldingService(HoldingRepository holdings, TradeRepository trades, QuoteService quotes, Clock clock) {
        this.holdings = holdings;
        this.trades = trades;
        this.quotes = quotes;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- 查詢

    /** includeClosed=false 只回還有持股的；true 連已經全部賣掉的也回（看已實現損益用）。 */
    @Transactional
    public List<HoldingView> list(boolean includeClosed) {
        Map<Long, List<Trade>> byHolding = trades.findAllByOrderByHoldingIdAscTradeDateAscIdAsc().stream()
            .collect(Collectors.groupingBy(Trade::getHoldingId));
        List<HoldingView> out = new ArrayList<>();
        for (Holding h : holdings.findAllByOrderByIdAsc()) {
            HoldingView v = view(h, byHolding.getOrDefault(h.getId(), List.of()));
            if (includeClosed || v.open()) out.add(v);
        }
        return out;
    }

    @Transactional
    public HoldingView get(long id) {
        Holding h = holdings.findById(id).orElseThrow(() -> ApiException.notFound(id));
        return view(h, trades.findByHoldingIdOrderByTradeDateAscIdAsc(id));
    }

    @Transactional(readOnly = true)
    public List<Trade> tradesOf(long holdingId) {
        return trades.findByHoldingIdOrderByTradeDateAscIdAsc(holdingId);
    }

    public Summary summary(List<HoldingView> views) {
        Summary.Builder b = new Summary.Builder();
        views.stream().filter(HoldingView::open).forEach(v -> b.add(v.cost(), v.unrealized()));
        views.forEach(v -> b.addRealized(v.position().realized()));
        return b.build();
    }

    private HoldingView view(Holding h, List<Trade> ts) {
        Position p = Position.replay(h.getCode(), ts.stream().map(HoldingService::entry).toList());
        if (p.shares() == 0) return new HoldingView(h, p, null, null);
        if (h.getMarket() == null) backfillMarket(h);
        Quote q = h.getMarket() == null ? null
            : quotes.latestOrEmpty(h.getCode(), Market.valueOf(h.getMarket())).orElse(null);
        return new HoldingView(h, p, q, q == null ? null : p.unrealized(q.close()));
    }

    static Position.Entry entry(Trade t) {
        return new Position.Entry(t.getId(), Position.Side.valueOf(t.getSide().name()), t.getTradeDate(),
            t.getShares(), t.getPrice());
    }

    /** 1.0 第 2 題時期建的資料沒有名稱與市場別，第一次顯示時補上。 */
    private void backfillMarket(Holding h) {
        try {
            quotes.lookup(h.getCode()).ifPresent(q -> {
                h.setName(q.name());
                h.setMarket(q.market().name());
            });
        } catch (QuoteSourceException ignored) {
            // 連不上就下次再補
        }
    }

    // ---------------------------------------------------------------- 2.0：買賣

    /** 記一筆買進或賣出。買進的代號還沒有持股時，先查官方確認代號再建主檔。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Trade addTrade(String rawCode, Trade.Side side, LocalDate date, Integer rawShares, BigDecimal rawPrice) {
        String code = HoldingValidator.code(rawCode);
        if (side == null) throw ApiException.validation("要指定買進或賣出");
        int shares = HoldingValidator.shares(rawShares);
        BigDecimal price = HoldingValidator.price(rawPrice);
        LocalDate d = HoldingValidator.date(date, LocalDate.now(clock));

        Optional<Holding> existing = holdings.findByCode(code);
        if (existing.isEmpty() && side == Trade.Side.SELL) {
            throw insufficient("沒有 " + code + " 的持股，不能賣出");
        }
        Holding h = existing.orElseGet(() -> createHolding(code));
        return addTradeTo(h.getId(), side, d, shares, price);
    }

    private Trade addTradeTo(long holdingId, Trade.Side side, LocalDate date, int shares, BigDecimal price) {
        Holding h = holdings.lockById(holdingId).orElseThrow(() -> ApiException.notFound(holdingId));
        List<Trade> current = trades.findByHoldingIdOrderByTradeDateAscIdAsc(holdingId);
        Trade t = new Trade(holdingId, side, date, shares, price);
        replayOrThrow(h.getCode(), withNew(current, t));
        return trades.save(t);
    }

    /** 新的一筆排在同一天既有交易的後面（跟存檔後 id 最大的順序一樣）。 */
    private static List<Position.Entry> withNew(List<Trade> current, Trade t) {
        List<Position.Entry> es = new ArrayList<>(current.stream().map(HoldingService::entry).toList());
        Position.Entry ne = new Position.Entry(Long.MAX_VALUE, Position.Side.valueOf(t.getSide().name()),
            t.getTradeDate(), t.getShares(), t.getPrice());
        int i = 0;
        while (i < es.size() && !es.get(i).date().isAfter(ne.date())) i++;
        es.add(i, ne);
        return es;
    }

    /** 改一筆交易的日期、股數、價格（買賣方向不能改）。改完整串重算，有賣超就不給改。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Trade updateTrade(long tradeId, LocalDate date, Integer rawShares, BigDecimal rawPrice) {
        int shares = HoldingValidator.shares(rawShares);
        BigDecimal price = HoldingValidator.price(rawPrice);
        LocalDate d = HoldingValidator.date(date, LocalDate.now(clock));
        Trade t = trades.findById(tradeId).orElseThrow(() ->
            new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "找不到編號 " + tradeId + " 的交易"));
        Holding h = holdings.lockById(t.getHoldingId()).orElseThrow();
        List<Position.Entry> es = new ArrayList<>();
        for (Trade x : trades.findByHoldingIdOrderByTradeDateAscIdAsc(h.getId())) {
            es.add(x.getId().equals(tradeId)
                ? new Position.Entry(x.getId(), Position.Side.valueOf(x.getSide().name()), d, shares, price)
                : entry(x));
        }
        es.sort(java.util.Comparator.comparing(Position.Entry::date).thenComparing(Position.Entry::id));
        replayOrThrow(h.getCode(), es);
        t.setTradeDate(d);
        t.setShares(shares);
        t.setPrice(price);
        return t;
    }

    /** 刪一筆交易；刪完沒有交易的話整檔一起刪，回 true。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean deleteTrade(long tradeId) {
        Trade t = trades.findById(tradeId).orElseThrow(() ->
            new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "找不到編號 " + tradeId + " 的交易"));
        Holding h = holdings.lockById(t.getHoldingId()).orElseThrow();
        List<Position.Entry> rest = trades.findByHoldingIdOrderByTradeDateAscIdAsc(h.getId()).stream()
            .filter(x -> !x.getId().equals(tradeId)).map(HoldingService::entry).toList();
        try {
            Position.replay(h.getCode(), rest);
        } catch (Position.InsufficientSharesException e) {
            throw new ApiException(HttpStatus.CONFLICT, "TRADE_CONFLICT",
                "刪掉這筆之後，" + e.entry().date() + " 那筆賣出會超過當時持有的股數，請先刪掉那筆賣出");
        }
        trades.delete(t);
        if (rest.isEmpty()) holdings.delete(h);
        return rest.isEmpty();
    }

    private void replayOrThrow(String code, List<Position.Entry> entries) {
        try {
            Position.replay(code, entries);
        } catch (Position.InsufficientSharesException e) {
            throw insufficient(e.getMessage());
        }
    }

    private static ApiException insufficient(String message) {
        return new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_SHARES", message);
    }

    private Holding createHolding(String code) {
        Quote q = lookupOrThrow(code);
        try {
            return holdings.saveAndFlush(new Holding(code, q.name(), q.market().name()));
        } catch (DataIntegrityViolationException e) {
            // 另一個請求剛好同時建了同一檔
            return holdings.findByCode(code).orElseThrow(() -> e);
        }
    }

    private Quote lookupOrThrow(String code) {
        Optional<Quote> q;
        try {
            q = quotes.lookup(code);
        } catch (QuoteSourceException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "QUOTE_SOURCE_DOWN",
                "暫時連不上證交所／櫃買中心，沒辦法確認代號，請稍後再試");
        }
        return q.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STOCK_NOT_FOUND",
            "上市、上櫃都查不到代號 " + code));
    }

    @Transactional
    public void deleteHolding(long id) {
        Holding h = holdings.findById(id).orElseThrow(() -> ApiException.notFound(id));
        holdings.delete(h);   // trade 由資料庫 ON DELETE CASCADE 一起刪
    }

    // ---------------------------------------------------------------- 1.0 相容（App 1.0 不會更新）

    /** 1.0 只看得到還有持股的。已經全部賣掉的，對 1.0 來說等於不存在。 */
    @Transactional
    public HoldingView getOpen(long id) {
        HoldingView v = get(id);
        if (!v.open()) throw ApiException.notFound(id);
        return v;
    }

    /**
     * 1.0 的新增：同代號還有持股回 409（跟 1.0 一樣，App 1.0 只會「新增一檔」）；
     * 之前全部賣掉的同代號，記成同一檔的新買進，id 沿用。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long v1Create(String rawCode, Integer rawLots, BigDecimal rawPrice) {
        String code = HoldingValidator.code(rawCode);
        int lots = HoldingValidator.lots(rawLots);
        BigDecimal price = HoldingValidator.price(rawPrice);
        Optional<Holding> existing = holdings.findByCode(code);
        if (existing.isPresent() && get(existing.get().getId()).open()) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_CODE", code + " 已在持股中");
        }
        Holding h = existing.orElseGet(() -> createHolding(code));
        addTradeTo(h.getId(), Trade.Side.BUY, LocalDate.now(clock), lots * 1000, price);
        return h.getId();
    }

    /**
     * 1.0 的修改：只有一筆買進時直接改那一筆；
     * 2.0 已經記了多筆買賣的，1.0 的「張數＋買進價」表達不了，回 409 請使用者到網頁改。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void v1Update(long id, Integer rawLots, BigDecimal rawPrice) {
        getOpen(id);
        int lots = HoldingValidator.lots(rawLots);
        BigDecimal price = HoldingValidator.price(rawPrice);
        holdings.lockById(id);
        List<Trade> ts = trades.findByHoldingIdOrderByTradeDateAscIdAsc(id);
        if (ts.size() != 1 || ts.get(0).getSide() != Trade.Side.BUY) {
            throw new ApiException(HttpStatus.CONFLICT, "MULTIPLE_TRADES",
                "這檔在網頁版記了 " + ts.size() + " 筆買賣，App 1.0 沒辦法修改，請到網頁版改");
        }
        ts.get(0).setShares(lots * 1000);
        ts.get(0).setPrice(price);
    }

    /** 1.0 的刪除：整檔連同所有買賣紀錄一起刪。 */
    @Transactional
    public void v1Delete(long id) {
        getOpen(id);
        deleteHolding(id);
    }
}
