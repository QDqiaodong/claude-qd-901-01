package com.heat.supply.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 归并预检结果：一次看清地址、热表号、换热站、供热状态，
 * 以及双方关联的室温抽测、报修记录、各年度采暖费与拟处置方式。
 */
@Data
@NoArgsConstructor
public class MergePreviewVO {

    private ProfileSide keeper;
    private ProfileSide merged;

    /** 地址是否确实指向同一住房（小区/楼栋/单元/房号完全一致） */
    private boolean sameAddress;

    /** 热表号是否一致；不一致必须停止归并 */
    private boolean meterMatch;

    /** 所属换热站是否一致；不一致必须停止归并 */
    private boolean stationMatch;

    /** 硬阻断原因清单（非空即禁止归并） */
    private List<String> blockers;

    /** 供热状态是否一致；不一致时必须由工作人员明确归并后状态 */
    private boolean supplyStatusMatch;

    /** 归并后供热状态的影响说明（抽测 / 报停 / 缴费） */
    private List<SupplyStatusImpact> supplyStatusImpacts;

    /** 室温抽测引用清单（归并后整体改挂保留档案，编号/日期/结果不变） */
    private List<SampleRef> samples;

    /** 报修工单引用清单（归并后整体改挂保留档案，工单号/时间/处理结果不变） */
    private List<OrderRef> orders;

    /** 各收费年度比对与拟处置方式 */
    private List<FeeYearPlan> feeYears;

    /** 两边抽测条数、报修条数、收费条数汇总 */
    private int keeperSampleCount;
    private int mergedSampleCount;
    private int keeperOrderCount;
    private int mergedOrderCount;
    private int keeperFeeCount;
    private int mergedFeeCount;

    /** 是否允许进入确认（地址同户 + 热表一致 + 换热站一致；供热状态另需 chosenSupplyStatus） */
    public boolean isMergeable() {
        return sameAddress && meterMatch && stationMatch && blockers != null && blockers.isEmpty();
    }

    /** 归并档案一侧的档案与引用快照 */
    @Data
    @NoArgsConstructor
    public static class ProfileSide {
        private Long residentId;
        private String community;
        private String buildingNo;
        private String unitNo;
        private String roomNo;
        private String fullAddress;
        private BigDecimal areaM2;
        private String meterNo;
        private Long stationId;
        private String stationCode;
        private String stationName;
        private String supplyStatus;
        private String contactPhone;
        private boolean merged;
        private Long mergedIntoId;
    }

    /** 供热状态二选一的影响说明 */
    @Data
    @NoArgsConstructor
    public static class SupplyStatusImpact {
        /** 正常供热 / 已报停 */
        private String supplyStatus;
        private String tempImpact;
        private String orderImpact;
        private String feeImpact;
    }

    /** 室温抽测引用 */
    @Data
    @NoArgsConstructor
    public static class SampleRef {
        private Long id;
        private String side;
        private LocalDate sampleDate;
        private BigDecimal temperature;
        private Boolean qualified;
        private String householdName;
        private String surveyor;
    }

    /** 报修工单引用 */
    @Data
    @NoArgsConstructor
    public static class OrderRef {
        private Long id;
        private String side;
        private String orderNo;
        private LocalDate reportedDate;
        private String faultType;
        private String orderStatus;
        private String assignee;
        private String handleResult;
        private LocalDate closedDate;
    }

    /**
     * 某收费年度的两边台账与拟处置方式。
     * action：
     *   KEEP_ONLY  仅一方有台账，原样改挂保留档案；
     *   RECOMPUTE  两边都未发生缴费，按保留档案计费面积重算，重复行作废；
     *   REVIEW     任一方已有实收，保留原始金额，两边标待财务复核，复核前禁止登记缴费。
     */
    @Data
    @NoArgsConstructor
    public static class FeeYearPlan {
        private Integer feeYear;
        private FeeRow keeperRow;
        private FeeRow mergedRow;
        private String action;
        private String actionLabel;
        private String actionNote;
        /** RECOMPUTE 时按保留档案计费面积重算后的应收 */
        private BigDecimal recomputedReceivable;
        /** REVIEW 时提示：复核前禁止继续登记该年度缴费 */
        private boolean payBlockedUntilReview;
    }

    @Data
    @NoArgsConstructor
    public static class FeeRow {
        private Long id;
        private BigDecimal billingArea;
        private BigDecimal unitPrice;
        private BigDecimal receivable;
        private BigDecimal paid;
        private BigDecimal arrears;
        private String payStatus;
        private LocalDate lastPayDate;
        private boolean reviewFlag;
        private boolean voided;
    }
}
