package com.heat.supply.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

/** 归并预检：单方档案信息 + 其关联的抽测 / 报修 / 各年度采暖费 */
@Data
public class MergeSideVO {

    private Long residentId;
    private String legacyCode;
    private String community;
    private String buildingNo;
    private String unitNo;
    private String roomNo;
    /** 完整地址 */
    private String addressText;
    private BigDecimal areaM2;
    private String meterNo;
    private Long stationId;
    private String stationCode;
    private String stationName;
    private String supplyStatus;
    private String contactPhone;
    private BigDecimal totalArrears;
    private boolean archived;
    private Long mergedIntoId;

    private List<MergeSampleRow> samples;
    private List<MergeOrderRow> orders;
    private List<MergeFeeRow> fees;

    /** 归并预检里的抽测行（业务字段原样展示，归并不改编号/时间/结果） */
    @Data
    public static class MergeSampleRow {
        private Long id;
        private String sampleDate;
        private BigDecimal temperature;
        private Boolean qualified;
        private String householdName;
        private String surveyor;
    }

    /** 归并预检里的报修行 */
    @Data
    public static class MergeOrderRow {
        private Long id;
        private String orderNo;
        private String reportedDate;
        private String faultType;
        private String assignee;
        private String handleResult;
        private String orderStatus;
    }

    /** 归并预检里的年度采暖费行 */
    @Data
    public static class MergeFeeRow {
        private Long id;
        private Integer feeYear;
        private BigDecimal billingArea;
        private BigDecimal unitPrice;
        private BigDecimal receivable;
        private BigDecimal paid;
        private BigDecimal arrears;
        private String payStatus;
        private String lastPayDate;
        private String remark;
        private boolean reviewRequired;
        private boolean reviewed;
    }
}
