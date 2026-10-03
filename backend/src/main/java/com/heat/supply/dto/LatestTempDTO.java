package com.heat.supply.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;
import lombok.NoArgsConstructor;

/** 手写 JPQL 投影：热用户最近一次室温抽测结果 */
@Data
@NoArgsConstructor
public class LatestTempDTO {

    private Long sampleId;
    private Long residentId;
    private LocalDate sampleDate;
    private BigDecimal temperature;
    private Boolean qualified;
    private String householdName;

    public LatestTempDTO(Long sampleId, Long residentId, LocalDate sampleDate, BigDecimal temperature,
                         Boolean qualified, String householdName) {
        this.sampleId = sampleId;
        this.residentId = residentId;
        this.sampleDate = sampleDate;
        this.temperature = temperature;
        this.qualified = qualified;
        this.householdName = householdName;
    }
}
