package com.heat.supply.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;
import lombok.NoArgsConstructor;

/** 采暖费台账视图对象 */
@Data
@NoArgsConstructor
public class FeeVO {

    private Long id;
    private Integer feeYear;
    private Long residentId;
    private String community;
    private String buildingNo;
    private String unitNo;
    private String roomNo;
    private String stationCode;
    private String supplyStatus;
    private BigDecimal billingArea;
    private BigDecimal unitPrice;
    private BigDecimal receivable;
    private BigDecimal paid;
    private BigDecimal arrears;
    private String payStatus;
    private LocalDate lastPayDate;
    private String remark;
    /** 待财务复核（同年度两边均有实收，复核前禁止登记缴费） */
    private boolean reviewFlag;
    /** 归并作废（金额冻结，不进台账/欠费统计） */
    private boolean voided;
    private String voidReason;

    public FeeVO(Long id, Integer feeYear, Long residentId, String community, String buildingNo, String unitNo,
                 String roomNo, String stationCode, String supplyStatus, BigDecimal billingArea,
                 BigDecimal unitPrice, BigDecimal receivable, BigDecimal paid, BigDecimal arrears,
                 String payStatus, LocalDate lastPayDate, String remark,
                 boolean reviewFlag, boolean voided, String voidReason) {
        this.id = id;
        this.feeYear = feeYear;
        this.residentId = residentId;
        this.community = community;
        this.buildingNo = buildingNo;
        this.unitNo = unitNo;
        this.roomNo = roomNo;
        this.stationCode = stationCode;
        this.supplyStatus = supplyStatus;
        this.billingArea = billingArea;
        this.unitPrice = unitPrice;
        this.receivable = receivable;
        this.paid = paid;
        this.arrears = arrears;
        this.payStatus = payStatus;
        this.lastPayDate = lastPayDate;
        this.remark = remark;
        this.reviewFlag = reviewFlag;
        this.voided = voided;
        this.voidReason = voidReason;
    }
}
