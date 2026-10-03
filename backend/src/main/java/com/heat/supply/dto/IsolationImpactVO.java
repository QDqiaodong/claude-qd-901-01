package com.heat.supply.dto;

import java.math.BigDecimal;

import lombok.Data;

/** 隔离试算的换热站供热后果 VO */
@Data
public class IsolationImpactVO {

    private Long stationId;
    private String stationCode;
    private String stationName;
    private String tapWellNo;
    /** 停暖 / 保供 / 原本停暖 */
    private String result;
    private String reason;
    private BigDecimal areaWanM2;
    private Integer households;
}
