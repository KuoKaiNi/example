package com.example.holdings.holding;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.regex.Pattern;

import com.example.holdings.common.ApiException;

/** 欄位規則；網頁、v1、v2 共用。 */
public final class HoldingValidator {
    private static final Pattern CODE = Pattern.compile("^[0-9]{4}[0-9A-Z]{0,2}$");
    private static final BigDecimal MAX_PRICE = new BigDecimal("99999.99");
    static final int MAX_SHARES = 9_999_999;

    private HoldingValidator() {}

    public static String code(String raw) {
        String code = raw == null ? "" : raw.trim().toUpperCase();
        if (!CODE.matcher(code).matches()) {
            throw ApiException.validation("股票代號格式不對：要 4 到 6 碼，前 4 碼是數字");
        }
        return code;
    }

    /** 1.0 用：張數 1～999。 */
    public static int lots(Integer lots) {
        if (lots == null || lots < 1 || lots > 999) {
            throw ApiException.validation("張數要是 1 到 999 的整數");
        }
        return lots;
    }

    /** 2.0 用：股數，可以是零股。 */
    public static int shares(Integer shares) {
        if (shares == null || shares < 1 || shares > MAX_SHARES) {
            throw ApiException.validation("股數要是 1 到 9,999,999 的整數");
        }
        return shares;
    }

    public static BigDecimal price(BigDecimal price) {
        if (price == null || price.signum() <= 0 || price.compareTo(MAX_PRICE) > 0) {
            throw ApiException.validation("價格要大於 0、小於 100,000");
        }
        if (price.stripTrailingZeros().scale() > 2) {
            throw ApiException.validation("價格最多兩位小數");
        }
        return price.setScale(2);
    }

    public static LocalDate date(LocalDate date, LocalDate today) {
        if (date == null) throw ApiException.validation("缺少交易日期");
        if (date.isAfter(today)) throw ApiException.validation("交易日期不能是未來");
        if (date.getYear() < 1990) throw ApiException.validation("交易日期太早");
        return date;
    }
}
