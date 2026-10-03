package com.heat.supply.dto;

import lombok.Data;

/** 阀门井开关 / 检修状态变更 */
@Data
public class ValveWellUpdateRequest {

    private String openState;
    private String repairStatus;
    private String remark;
}
