package com.example.holdings.api.v2;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.example.holdings.common.ApiException;
import com.example.holdings.holding.HoldingService;
import com.example.holdings.holding.HoldingView;
import com.example.holdings.holding.Trade;

/** 2.0 的 API，規格見 API.md。 */
@RestController
@RequestMapping("/api/v2")
public class HoldingApiV2 {
    private final HoldingService service;

    public HoldingApiV2(HoldingService service) {
        this.service = service;
    }

    @GetMapping("/holdings")
    public List<HoldingV2> list(@RequestParam(name = "include_closed", defaultValue = "false") boolean includeClosed) {
        return service.list(includeClosed).stream().map(HoldingV2::from).toList();
    }

    @GetMapping("/holdings/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        return detail(service.get(id));
    }

    @DeleteMapping("/holdings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHolding(@PathVariable long id) {
        service.deleteHolding(id);
    }

    @PostMapping("/trades")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addTrade(@RequestBody TradeRequestV2 req) {
        if (req == null) throw ApiException.validation("缺少資料");
        Trade t = service.addTrade(req.code(), side(req.side()), req.tradeDate(), req.shares(), req.price());
        Map<String, Object> out = detail(service.get(t.getHoldingId()));
        out.put("trade_id", t.getId());
        return out;
    }

    /** 改日期、股數、價格；body 的 code、side 會被忽略。 */
    @PutMapping("/trades/{id}")
    public Map<String, Object> updateTrade(@PathVariable long id, @RequestBody TradeRequestV2 req) {
        if (req == null) throw ApiException.validation("缺少資料");
        Trade t = service.updateTrade(id, req.tradeDate(), req.shares(), req.price());
        Map<String, Object> out = detail(service.get(t.getHoldingId()));
        out.put("trade_id", t.getId());
        return out;
    }

    @DeleteMapping("/trades/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTrade(@PathVariable long id) {
        service.deleteTrade(id);
    }

    @GetMapping("/summary")
    public SummaryV2 summary() {
        return SummaryV2.from(service.summary(service.list(true)));
    }

    private static Map<String, Object> detail(HoldingView v) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("holding", HoldingV2.from(v));
        out.put("trades", v.position().lines().stream().map(TradeV2::from).toList());
        return out;
    }

    private static Trade.Side side(String s) {
        if ("BUY".equals(s)) return Trade.Side.BUY;
        if ("SELL".equals(s)) return Trade.Side.SELL;
        throw ApiException.validation("side 要是 BUY 或 SELL");
    }
}
