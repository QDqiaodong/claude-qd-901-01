package com.heat.supply.dto;

import java.math.BigDecimal;

import lombok.Data;

/** 换热站运行参数登记：一次/二次侧温度 + 循环泵 + 状态 */
@Data
public class StationRuntimeRequest {

    private BigDecimal primarySupplyTemp;
    private BigDecimal primaryReturnTemp;
    private BigDecimal secondarySupplyTemp;
    private BigDecimal secondaryReturnTemp;
    private BigDecimal designDeltaT;
    private Integer runningPumpCount;
    private String status;
    private String manager;
}
