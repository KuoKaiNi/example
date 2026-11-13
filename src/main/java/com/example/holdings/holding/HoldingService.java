package com.example.holdings.holding;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.holdings.common.ApiException;

@Service
public class HoldingService {
    private final HoldingRepository repo;

    public HoldingService(HoldingRepository repo) {
        this.repo = repo;
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
        return repo.save(new Holding(code, l, p));
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
