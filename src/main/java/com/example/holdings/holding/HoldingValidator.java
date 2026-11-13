package com.example.holdings.holding;

import java.math.BigDecimal;
import java.util.regex.Pattern;

import com.example.holdings.common.ApiException;

/** 第 2 題的欄位規則；網頁與 API 共用同一套。 */
public final class HoldingValidator {
    private static final Pattern CODE = Pattern.compile("^[0-9]{4}[0-9A-Z]{0,2}$");
    private static final BigDecimal MAX_PRICE = new BigDecimal("99999.99");

    private HoldingValidator() {}

    public static String code(String raw) {
        String code = raw == null ? "" : raw.trim().toUpperCase();
        if (!CODE.matcher(code).matches()) {
            throw ApiException.validation("股票代號格式不對：要 4 到 6 碼，前 4 碼是數字");
        }
        return code;
    }

    public static int lots(Integer lots) {
        if (lots == null || lots < 1 || lots > 999) {
            throw ApiException.validation("張數要是 1 到 999 的整數");
        }
        return lots;
    }

    public static BigDecimal price(BigDecimal price) {
        if (price == null || price.signum() <= 0 || price.compareTo(MAX_PRICE) > 0) {
            throw ApiException.validation("買進價要大於 0、小於 100,000");
        }
        if (price.stripTrailingZeros().scale() > 2) {
            throw ApiException.validation("買進價最多兩位小數");
        }
        return price.setScale(2);
    }
}
