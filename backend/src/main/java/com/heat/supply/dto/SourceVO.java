package com.heat.supply.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

/** 热源厂视图对象 */
@Data
@NoArgsConstructor
public class SourceVO {

    private Long id;
    private String name;
    private BigDecimal capacityMw;
    private String fuelType;
    private String status;
    private String manager;
    private String address;
    private LocalDate commissionDate;
    /** -1 表示该集合未随本次查询预加载 */
    private Integer stationCount;
    private List<String> stationCodes;

    public SourceVO(Long id, String name, BigDecimal capacityMw, String fuelType, String status,
                    String manager, String address, LocalDate commissionDate,
                    Integer stationCount, List<String> stationCodes) {
        this.id = id;
        this.name = name;
        this.capacityMw = capacityMw;
        this.fuelType = fuelType;
        this.status = status;
        this.manager = manager;
        this.address = address;
        this.commissionDate = commissionDate;
        this.stationCount = stationCount;
        this.stationCodes = stationCodes;
    }
}
