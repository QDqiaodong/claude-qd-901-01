package com.heat.supply.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

/** 室温抽测热力格子矩阵 + 底部汇总 */
@Data
@NoArgsConstructor
public class TempGridVO {

    private String community;
    private List<String> buildings;
    private List<String> units;
    private List<TempCellVO> cells;
    private Integer totalHouseholds;
    private Integer sampledHouseholds;
    private Integer qualifiedCount;
    private Integer unqualifiedCount;
    private BigDecimal unqualifiedRate;

    public TempGridVO(String community, List<String> buildings, List<String> units, List<TempCellVO> cells,
                      Integer totalHouseholds, Integer sampledHouseholds, Integer qualifiedCount,
                      Integer unqualifiedCount, BigDecimal unqualifiedRate) {
        this.community = community;
        this.buildings = buildings;
        this.units = units;
        this.cells = cells;
        this.totalHouseholds = totalHouseholds;
        this.sampledHouseholds = sampledHouseholds;
        this.qualifiedCount = qualifiedCount;
        this.unqualifiedCount = unqualifiedCount;
        this.unqualifiedRate = unqualifiedRate;
    }
}
