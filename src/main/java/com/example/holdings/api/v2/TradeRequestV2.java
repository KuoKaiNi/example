package com.example.holdings.api.v2;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TradeRequestV2(String code, String side, LocalDate tradeDate, Integer shares, BigDecimal price) {}
