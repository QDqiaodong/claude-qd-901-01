package com.heat.supply.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

/** 缴费登记 */
@Data
public class PayRequest {

    private BigDecimal amount;
    private LocalDate payDate;
}
