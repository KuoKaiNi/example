package com.example.holdings.holding;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.holdings.common.ApiException;

@SpringBootTest
class HoldingServiceTest {
    @Autowired HoldingService service;
    @Autowired HoldingRepository repo;

    @BeforeEach
    void clean() { repo.deleteAll(); }

    @Test
    void 新增後查得到() {
        Holding h = service.create(" 2330 ", 1, new BigDecimal("1000"));
        assertThat(h.getCode()).isEqualTo("2330");
        assertThat(service.list()).hasSize(1);
    }

    @Test
    void 同代號重複新增回409且不儲存() {
        service.create("2330", 1, new BigDecimal("1000"));
        assertThatThrownBy(() -> service.create("2330", 2, new BigDecimal("900")))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("DUPLICATE_CODE"));
        assertThat(service.list()).hasSize(1);
    }

    @Test
    void 欄位驗證() {
        assertThatThrownBy(() -> service.create("23", 1, BigDecimal.TEN)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.create("2330", 0, BigDecimal.TEN)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.create("2330", 1000, BigDecimal.TEN)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.create("2330", 1, new BigDecimal("10.123"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.create("2330", 1, BigDecimal.ZERO)).isInstanceOf(ApiException.class);
        assertThat(service.list()).isEmpty();
    }

    @Test
    void 修改與刪除() {
        long id = service.create("0050", 2, new BigDecimal("150")).getId();
        service.update(id, 3, new BigDecimal("148.5"));
        assertThat(service.get(id).getLots()).isEqualTo(3);
        service.delete(id);
        assertThatThrownBy(() -> service.get(id))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_FOUND"));
    }
}
