package com.heat.supply.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 归并确认请求：保留档案 / 待归并档案 / 状态不一致时工作人员明确选择的供热状态。
 */
@Data
public class MergeConfirmRequest {

    @NotNull(message = "请选择保留档案")
    private Long keeperId;

    @NotNull(message = "请选择待归并档案")
    private Long mergedId;

    /** 供热状态不一致时必填：正常供热 / 已报停 */
    private String chosenSupplyStatus;

    /** 经办人 */
    private String operator;
}
