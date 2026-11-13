package com.example.holdings.api.v1;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.example.holdings.common.ApiException;
import com.example.holdings.holding.HoldingService;

/**
 * App 1.0 用的 API，規格見《App 1.0 API 規格》。App 已發布、不會更新，回應格式不能改。
 * 2.0 改成一檔多筆買賣之後，這裡把新資料轉成 1.0 看得懂的樣子，規則寫在 HoldingV1 與 UPGRADE.md。
 */
@RestController
@RequestMapping("/api/v1")
public class HoldingApiV1 {
    private final HoldingService service;

    public HoldingApiV1(HoldingService service) {
        this.service = service;
    }

    @GetMapping("/holdings")
    public List<HoldingV1> list() {
        return service.list(false).stream().map(HoldingV1::from).toList();
    }

    @GetMapping("/holdings/{id}")
    public HoldingV1 get(@PathVariable long id) {
        return HoldingV1.from(service.getOpen(id));
    }

    @PostMapping("/holdings")
    @ResponseStatus(HttpStatus.CREATED)
    public HoldingV1 create(@RequestBody HoldingRequestV1 req) {
        if (req == null) throw ApiException.validation("缺少資料");
        long id = service.v1Create(req.code(), req.lots(), req.buyPrice());
        return HoldingV1.from(service.getOpen(id));
    }

    @PutMapping("/holdings/{id}")
    public HoldingV1 update(@PathVariable long id, @RequestBody HoldingRequestV1 req) {
        if (req == null) throw ApiException.validation("缺少資料");
        service.v1Update(id, req.lots(), req.buyPrice());
        return HoldingV1.from(service.getOpen(id));
    }

    @DeleteMapping("/holdings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.v1Delete(id);
    }

    @GetMapping("/summary")
    public SummaryV1 summary() {
        return SummaryV1.from(service.summary(service.list(false)));
    }
}
