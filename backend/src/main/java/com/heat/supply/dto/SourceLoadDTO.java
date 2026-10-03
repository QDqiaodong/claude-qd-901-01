package com.heat.supply.dto;

import java.math.BigDecimal;

import lombok.Data;
import lombok.NoArgsConstructor;

/** 手写 JPQL 投影：按热源汇总装机容量、供热面积与一次侧平均温差 */
@Data
@NoArgsConstructor
public class SourceLoadDTO {

    private Long sourceId;
    private String sourceName;
    private String fuelType;
    private BigDecimal capacityMw;
    private Long stationCount;
    private BigDecimal totalAreaWanM2;
    private Double avgPrimaryDelta;

    public SourceLoadDTO(Long sourceId, String sourceName, String fuelType, BigDecimal capacityMw,
                         Long stationCount, BigDecimal totalAreaWanM2, Double avgPrimaryDelta) {
        this.sourceId = sourceId;
        this.sourceName = sourceName;
        this.fuelType = fuelType;
        this.capacityMw = capacityMw;
        this.stationCount = stationCount;
        this.totalAreaWanM2 = totalAreaWanM2;
        this.avgPrimaryDelta = avgPrimaryDelta;
    }
}
