package com.heat.supply.dto;

import lombok.Data;

/** 重复热用户档案归并请求 */
@Data
public class ResidentMergeRequest {

    /** 保留档案 id */
    private Long keptResidentId;

    /** 待归并档案 id */
    private Long awayResidentId;

    /**
     * 供热状态不一致时工作人员明确采用的状态（正常供热 / 已报停）。
     * 预检返回 supplyConflict=true 时必填且必须是双方状态之一；状态一致时留空。
     */
    private String chosenSupplyStatus;

    /** 地址确认：必须显式勾选「两边地址确实指向同一住房」才允许归并 */
    private Boolean addressConfirmed;

    /** 经办人 */
    private String operator;
}
