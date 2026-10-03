package com.heat.supply.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/** 手写 JPQL 投影：按换热站统计室温抽测户数与不达标次数 */
@Data
@NoArgsConstructor
public class StationTempStatDTO {

    private Long stationId;
    private String stationCode;
    private String stationName;
    private Long sampleCount;
    private Long unqualifiedCount;
    private Long householdCount;

    public StationTempStatDTO(Long stationId, String stationCode, String stationName,
                              Long sampleCount, Long unqualifiedCount, Long householdCount) {
        this.stationId = stationId;
        this.stationCode = stationCode;
        this.stationName = stationName;
        this.sampleCount = sampleCount;
        this.unqualifiedCount = unqualifiedCount;
        this.householdCount = householdCount;
    }
}
