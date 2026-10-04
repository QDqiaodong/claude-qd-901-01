package com.heat.supply.dto;

import java.time.LocalDateTime;

import lombok.Data;

/** 归并审计记录视图对象 */
@Data
public class MergeLogVO {

    private Long id;
    private String mergeNo;
    private Long keptResidentId;
    private Long awayResidentId;
    private String keptAddress;
    private String awayAddress;
    private String meterNo;
    private String keptSnapshot;
    private String awaySnapshot;
    private String chosenSupplyStatus;
    private Integer sampleCount;
    private Integer orderCount;
    private Integer feeCount;
    private String status;
    private String failReason;
    private String operator;
    private LocalDateTime createdAt;
}
