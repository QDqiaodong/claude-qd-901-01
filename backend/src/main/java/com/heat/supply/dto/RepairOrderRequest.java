package com.heat.supply.dto;

import lombok.Data;

/** 报修工单新增 / 派工 */
@Data
public class RepairOrderRequest {

    private Long stationId;
    private Long residentId;
    private String faultType;
    private String assignee;
    private String handleResult;
    private String orderStatus;
}
