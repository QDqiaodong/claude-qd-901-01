package com.heat.supply.dto;

import java.math.BigDecimal;

import lombok.Data;
import lombok.NoArgsConstructor;

/** 热用户视图对象 */
@Data
@NoArgsConstructor
public class ResidentVO {

    private Long id;
    private String community;
    private String buildingNo;
    private String unitNo;
    private String roomNo;
    private BigDecimal areaM2;
    private String meterNo;
    private BigDecimal totalHeatGj;
    private Long stationId;
    private String stationCode;
    private String stationName;
    private String supplyStatus;
    private String contactPhone;
    private BigDecimal arrears;

    public ResidentVO(Long id, String community, String buildingNo, String unitNo, String roomNo,
                      BigDecimal areaM2, String meterNo, BigDecimal totalHeatGj, Long stationId,
                      String stationCode, String stationName, String supplyStatus, String contactPhone,
                      BigDecimal arrears) {
        this.id = id;
        this.community = community;
        this.buildingNo = buildingNo;
        this.unitNo = unitNo;
        this.roomNo = roomNo;
        this.areaM2 = areaM2;
        this.meterNo = meterNo;
        this.totalHeatGj = totalHeatGj;
        this.stationId = stationId;
        this.stationCode = stationCode;
        this.stationName = stationName;
        this.supplyStatus = supplyStatus;
        this.contactPhone = contactPhone;
        this.arrears = arrears;
    }
}
