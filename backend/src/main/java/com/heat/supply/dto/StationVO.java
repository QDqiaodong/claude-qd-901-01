package com.heat.supply.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;
import lombok.NoArgsConstructor;

/** 换热站视图对象（含一次侧负荷率） */
@Data
@NoArgsConstructor
public class StationVO {

    private Long id;
    private String code;
    private String name;
    private Long sourceId;
    private String sourceName;
    private String fuelType;
    private BigDecimal areaWanM2;
    private BigDecimal primarySupplyTemp;
    private BigDecimal primaryReturnTemp;
    private BigDecimal secondarySupplyTemp;
    private BigDecimal secondaryReturnTemp;
    private BigDecimal designDeltaT;
    private BigDecimal primaryDelta;
    private BigDecimal secondaryDelta;
    /** 负荷率 = 一次侧实际温差 / 设计温差 */
    private BigDecimal loadRate;
    private String loadLevel;
    private Integer pumpCount;
    private Integer runningPumpCount;
    private String status;
    private String manager;
    private String address;
    private LocalDate lastMaintainDate;
    private Long openOrderCount;

    public StationVO(Long id, String code, String name, Long sourceId, String sourceName, String fuelType,
                     BigDecimal areaWanM2, BigDecimal primarySupplyTemp, BigDecimal primaryReturnTemp,
                     BigDecimal secondarySupplyTemp, BigDecimal secondaryReturnTemp, BigDecimal designDeltaT,
                     BigDecimal primaryDelta, BigDecimal secondaryDelta, BigDecimal loadRate, String loadLevel,
                     Integer pumpCount, Integer runningPumpCount, String status, String manager, String address,
                     LocalDate lastMaintainDate, Long openOrderCount) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.sourceId = sourceId;
        this.sourceName = sourceName;
        this.fuelType = fuelType;
        this.areaWanM2 = areaWanM2;
        this.primarySupplyTemp = primarySupplyTemp;
        this.primaryReturnTemp = primaryReturnTemp;
        this.secondarySupplyTemp = secondarySupplyTemp;
        this.secondaryReturnTemp = secondaryReturnTemp;
        this.designDeltaT = designDeltaT;
        this.primaryDelta = primaryDelta;
        this.secondaryDelta = secondaryDelta;
        this.loadRate = loadRate;
        this.loadLevel = loadLevel;
        this.pumpCount = pumpCount;
        this.runningPumpCount = runningPumpCount;
        this.status = status;
        this.manager = manager;
        this.address = address;
        this.lastMaintainDate = lastMaintainDate;
        this.openOrderCount = openOrderCount;
    }
}
