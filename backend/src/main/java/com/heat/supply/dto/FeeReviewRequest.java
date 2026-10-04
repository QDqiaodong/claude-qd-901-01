package com.heat.supply.dto;

import lombok.Data;

/** 财务复核请求：同年度待复核台账成组放行，仅登记复核结论，金额一律不动 */
@Data
public class FeeReviewRequest {

    /** 复核备注（可选） */
    private String note;
}
