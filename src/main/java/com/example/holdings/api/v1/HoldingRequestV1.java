package com.example.holdings.api.v1;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** POST 用 code、lots、buy_price；PUT 只看 lots、buy_price（代號不能改）。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record HoldingRequestV1(String code, Integer lots, BigDecimal buyPrice) {}
