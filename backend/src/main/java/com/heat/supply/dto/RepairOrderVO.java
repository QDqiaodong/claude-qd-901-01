package com.heat.supply.dto;

import java.time.LocalDate;

import lombok.Data;
import lombok.NoArgsConstructor;

/** 报修工单视图对象 */
@Data
@NoArgsConstructor
public class RepairOrderVO {

    private Long id;
    private String orderNo;
    private LocalDate reportedDate;
    private Long stationId;
    private String stationCode;
    private Long residentId;
    private String residentLabel;
    private String faultType;
    private String assignee;
    private String handleResult;
    private String orderStatus;
    private LocalDate closedDate;
    /** 下一状态（看板拖拽用） */
    private String nextStatus;

    public RepairOrderVO(Long id, String orderNo, LocalDate reportedDate, Long stationId, String stationCode,
                         Long residentId, String residentLabel, String faultType, String assignee,
                         String handleResult, String orderStatus, LocalDate closedDate, String nextStatus) {
        this.id = id;
        this.orderNo = orderNo;
        this.reportedDate = reportedDate;
        this.stationId = stationId;
        this.stationCode = stationCode;
        this.residentId = residentId;
        this.residentLabel = residentLabel;
        this.faultType = faultType;
        this.assignee = assignee;
        this.handleResult = handleResult;
        this.orderStatus = orderStatus;
        this.closedDate = closedDate;
        this.nextStatus = nextStatus;
    }
}
