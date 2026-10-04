package com.heat.supply.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 财务复核完成请求：归并后同年度两条台账均有实收，财务核对后完成收口。
 */
@Data
public class FeeReviewResolveRequest {

    /** 保留档案名下、本复核组的任一台账 id（通常为保留原档那条） */
    @NotNull(message = "请选择要完成复核的台账记录")
    private Long feeId;

    /** 财务确认后的应收金额（默认取保留档案原始应收） */
    private java.math.BigDecimal confirmedReceivable;

    /** 复核意见 */
    private String reviewNote;
}
