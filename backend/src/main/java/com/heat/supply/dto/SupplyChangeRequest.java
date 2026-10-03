package com.heat.supply.dto;

import lombok.Data;

/** 报停 / 恢复供热 */
@Data
public class SupplyChangeRequest {

    /** 正常供热 / 已报停 */
    private String supplyStatus;
}
