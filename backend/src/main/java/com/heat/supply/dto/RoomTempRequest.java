package com.heat.supply.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

/** 室温抽测录入 */
@Data
public class RoomTempRequest {

    private Long residentId;
    private LocalDate sampleDate;
    private BigDecimal temperature;
    private String householdName;
    private String surveyor;
    private String remark;
    /** 不达标时是否自动生成报修工单 */
    private Boolean autoOrder;
}
