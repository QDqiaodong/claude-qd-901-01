package com.heat.supply.dto;

import java.math.BigDecimal;

import lombok.Data;
import lombok.NoArgsConstructor;

/** 手写 JPQL 投影：欠费金额排行 */
@Data
@NoArgsConstructor
public class ArrearsRankDTO {

    private Long feeId;
    private Long residentId;
    private Integer feeYear;
    private String community;
    private String buildingNo;
    private String unitNo;
    private String roomNo;
    private String householdName;
    private String stationCode;
    private BigDecimal receivable;
    private BigDecimal paid;
    private BigDecimal arrears;
    private String payStatus;

    public ArrearsRankDTO(Long feeId, Long residentId, Integer feeYear, String community, String buildingNo,
                          String unitNo, String roomNo, String stationCode,
                          BigDecimal receivable, BigDecimal paid, BigDecimal arrears, String payStatus) {
        this.feeId = feeId;
        this.residentId = residentId;
        this.feeYear = feeYear;
        this.community = community;
        this.buildingNo = buildingNo;
        this.unitNo = unitNo;
        this.roomNo = roomNo;
        this.stationCode = stationCode;
        this.receivable = receivable;
        this.paid = paid;
        this.arrears = arrears;
        this.payStatus = payStatus;
    }
}
