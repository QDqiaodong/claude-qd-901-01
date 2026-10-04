package com.heat.supply.dto;

import java.math.BigDecimal;

import lombok.Data;

/** 同一收费年度两边台账的对比与处置预案（预检生成，确认时在服务端重新计算，不信前端） */
@Data
public class MergeFeeYearPlan {

    private Integer feeYear;

    /** 处置方式：RECALC（都未缴费，按保留档案计费面积重算）/ KEEP_REVIEW（任一方已有实收，保留原始金额待财务复核）/ MIGRATE（仅一方有记录，原样迁移） */
    private String action;

    private String actionText;

    private Long keptFeeId;
    private Long awayFeeId;

    private BigDecimal keptReceivable;
    private BigDecimal keptPaid;
    private BigDecimal awayReceivable;
    private BigDecimal awayPaid;

    /** RECALC 时的重算结果：保留档案计费面积 × 单价 */
    private BigDecimal recalcBillingArea;
    private BigDecimal recalcReceivable;
    private BigDecimal recalcArrears;

    /** 复核前禁止登记缴费 / 重算后按保留档案面积计费 等说明 */
    private String note;
}
