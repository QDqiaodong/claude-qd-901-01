package com.heat.supply.dto;

import java.util.List;

import lombok.Data;

/**
 * 归并确认结果。
 * idempotent=true 表示本次请求命中的是此前已成功的归并：直接返回原结果，
 * 不再搬移任何引用、不重算任何金额（重复确认幂等）。
 */
@Data
public class MergeResultVO {

    private String mergeNo;
    private boolean idempotent;
    private Long keptResidentId;
    private Long awayResidentId;
    private String chosenSupplyStatus;
    private int sampleMigrated;
    private int orderMigrated;
    private int feeHandled;
    private List<FeeYearResult> feeResults;
    private String createdAt;

    /** 每个收费年度的处置回执 */
    @Data
    public static class FeeYearResult {
        private Integer feeYear;
        private String action;
        private String actionText;
        /** RECALC 后保留档案该年度金额；KEEP_REVIEW 时为 null（原始金额未动） */
        private java.math.BigDecimal receivable;
        private java.math.BigDecimal paid;
        private java.math.BigDecimal arrears;
        private boolean reviewRequired;
        /** 该年度实际处置的台账行数 */
        private int touchedRows;
        private String note;
    }
}
