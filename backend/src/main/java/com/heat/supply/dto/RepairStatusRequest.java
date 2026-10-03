package com.heat.supply.dto;

import lombok.Data;

/** 看板拖拽推进工单状态 */
@Data
public class RepairStatusRequest {

    private String orderStatus;
    private String assignee;
    private String handleResult;
}
