package com.example.holdings.quote;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 某檔股票最近一個有成交的交易日收盤價。 */
public record Quote(String code, String name, Market market, BigDecimal close, LocalDate date) {}
