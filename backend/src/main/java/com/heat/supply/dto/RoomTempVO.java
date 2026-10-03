package com.heat.supply.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;
import lombok.NoArgsConstructor;

/** 室温抽测视图对象 */
@Data
@NoArgsConstructor
public class RoomTempVO {

    private Long id;
    private Long residentId;
    private String community;
    private String buildingNo;
    private String unitNo;
    private String roomNo;
    private Long stationId;
    private String stationCode;
    private LocalDate sampleDate;
    private BigDecimal temperature;
    private Boolean qualified;
    private Boolean overheated;
    private String householdName;
    private String surveyor;
    private String remark;
    private Boolean residentEagerLoaded;
    /** 录入时自动生成报修工单时的工单号 */
    private String linkedOrderNo;

    public RoomTempVO(Long id, Long residentId, String community, String buildingNo, String unitNo, String roomNo,
                      Long stationId, String stationCode, LocalDate sampleDate, BigDecimal temperature,
                      Boolean qualified, Boolean overheated, String householdName, String surveyor, String remark,
                      Boolean residentEagerLoaded) {
        this.id = id;
        this.residentId = residentId;
        this.community = community;
        this.buildingNo = buildingNo;
        this.unitNo = unitNo;
        this.roomNo = roomNo;
        this.stationId = stationId;
        this.stationCode = stationCode;
        this.sampleDate = sampleDate;
        this.temperature = temperature;
        this.qualified = qualified;
        this.overheated = overheated;
        this.householdName = householdName;
        this.surveyor = surveyor;
        this.remark = remark;
        this.residentEagerLoaded = residentEagerLoaded;
    }
}
