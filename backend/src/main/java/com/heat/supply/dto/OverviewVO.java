package com.heat.supply.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

/** 运行总览（Redis 缓存对象） */
@Data
@NoArgsConstructor
public class OverviewVO {

    private Long sourceCount;
    private Long stationCount;
    private Long runningStationCount;
    private Long residentCount;
    private Long unclosedOrderCount;
    private BigDecimal totalAreaWanM2;
    private BigDecimal totalCapacityMw;
    private BigDecimal unqualifiedRate;
    private BigDecimal totalArrears;
    private List<SourceLoadDTO> sourceLoads;

    public OverviewVO(Long sourceCount, Long stationCount, Long runningStationCount, Long residentCount,
                      Long unclosedOrderCount, BigDecimal totalAreaWanM2, BigDecimal totalCapacityMw,
                      BigDecimal unqualifiedRate, BigDecimal totalArrears, List<SourceLoadDTO> sourceLoads) {
        this.sourceCount = sourceCount;
        this.stationCount = stationCount;
        this.runningStationCount = runningStationCount;
        this.residentCount = residentCount;
        this.unclosedOrderCount = unclosedOrderCount;
        this.totalAreaWanM2 = totalAreaWanM2;
        this.totalCapacityMw = totalCapacityMw;
        this.unqualifiedRate = unqualifiedRate;
        this.totalArrears = totalArrears;
        this.sourceLoads = sourceLoads;
    }
}
