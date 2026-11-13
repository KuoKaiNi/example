package com.example.holdings.holding;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.holdings.common.ApiException;
import com.example.holdings.quote.Market;
import com.example.holdings.quote.Quote;
import com.example.holdings.quote.QuoteService;
import com.example.holdings.quote.QuoteSourceException;

@Service
public class HoldingService {
    private final HoldingRepository repo;
    private final QuoteService quotes;

    public HoldingService(HoldingRepository repo, QuoteService quotes) {
        this.repo = repo;
        this.quotes = quotes;
    }

    /** 持股加上最近收盤價；查不到收盤價的那筆 quote 為 null，不影響其他筆。 */
    @Transactional
    public List<HoldingView> listWithQuotes() {
        return repo.findAllByOrderByIdAsc().stream().map(this::withQuote).toList();
    }

    @Transactional
    public HoldingView getWithQuote(long id) {
        return withQuote(get(id));
    }

    HoldingView withQuote(Holding h) {
        if (h.getMarket() == null) backfillMarket(h);
        Quote q = h.getMarket() == null ? null
            : quotes.latestOrEmpty(h.getCode(), Market.valueOf(h.getMarket())).orElse(null);
        return new HoldingView(h, q);
    }

    /** 第 2 題時期建的資料沒有名稱與市場別，第一次顯示時補上。 */
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

    @Transactional(readOnly = true)
    public List<Holding> list() {
        return repo.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public Holding get(long id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound(id));
    }

    @Transactional
    public Holding create(String rawCode, Integer lots, BigDecimal price) {
        String code = HoldingValidator.code(rawCode);
        int l = HoldingValidator.lots(lots);
        BigDecimal p = HoldingValidator.price(price);
        if (repo.existsByCode(code)) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_CODE", code + " 已在持股中");
        }
        Quote q = lookupOrThrow(code);
        Holding h = new Holding(code, l, p);
        h.setName(q.name());
        h.setMarket(q.market().name());
        return repo.save(h);
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
    public Holding update(long id, Integer lots, BigDecimal price) {
        Holding h = get(id);
        h.setLots(HoldingValidator.lots(lots));
        h.setBuyPrice(HoldingValidator.price(price));
        return h;
    }

    @Transactional
    public void delete(long id) {
        repo.delete(get(id));
    }
}
