package com.heat.supply.dto;

import java.time.LocalDateTime;

import lombok.Data;

/** 隔离方案阀门步骤 VO */
@Data
public class IsolationStepVO {

    private Long id;
    private Integer stepNo;
    private String stage;
    private Long valveWellId;
    private String wellNo;
    private String wellLocation;
    private Integer valveCaliberDn;
    private String actionKind;
    private String plannedState;
    private String fromState;
    private Boolean egressGuard;
    private String stepStatus;
    private String actualState;
    private String confirmedBy;
    private LocalDateTime confirmedAt;
    private String deviationReason;
    private String note;
    /** 阀门当前库内实况（用于前端比对「同一阀门已被他人改变」） */
    private String liveState;
}
