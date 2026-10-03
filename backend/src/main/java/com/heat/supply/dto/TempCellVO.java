package com.heat.supply.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

/** 室温热力格子：楼栋 × 单元 */
@Data
@NoArgsConstructor
public class TempCellVO {

    private String buildingNo;
    private String unitNo;
    private List<Long> residentIds;
    private Integer householdCount;
    private Integer sampleCount;
    /** 该格子最近一次抽测室温 */
    private BigDecimal latestTemp;
    private LocalDate latestDate;
    private Boolean qualified;
    private Boolean overheated;
    private Integer unqualifiedCount;
    private Long latestSampleId;
    private List<String> households;

    public TempCellVO(String buildingNo, String unitNo, List<Long> residentIds, Integer householdCount,
                      Integer sampleCount, BigDecimal latestTemp, LocalDate latestDate, Boolean qualified,
                      Boolean overheated, Integer unqualifiedCount, Long latestSampleId, List<String> households) {
        this.buildingNo = buildingNo;
        this.unitNo = unitNo;
        this.residentIds = residentIds;
        this.householdCount = householdCount;
        this.sampleCount = sampleCount;
        this.latestTemp = latestTemp;
        this.latestDate = latestDate;
        this.qualified = qualified;
        this.overheated = overheated;
        this.unqualifiedCount = unqualifiedCount;
        this.latestSampleId = latestSampleId;
        this.households = households;
    }
}
