package com.heat.supply.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/** 隔离方案详情 VO（方案状态、阀门实况、受影响换热站三者在同一快照里保持一致） */
@Data
public class IsolationPlanVO {

    private Long id;
    private String planNo;
    private Long faultSegmentId;
    private String faultSegmentCode;
    private String faultSegmentLabel;
    private String faultReason;
    private String phase;
    private String strategy;
    private Boolean isolatedProven;
    private String proofNote;
    private List<String> blockedWellNos;
    private List<String> enclosedSegmentCodes;
    private List<String> forcedOpenWellNos;
    private List<String> affectedStationCodes;
    private Integer affectedStationCount;
    private BigDecimal affectedAreaWanM2;
    private Integer affectedHouseholds;
    private List<String> maintainableStationCodes;
    private String blockedChoiceNote;
    private Boolean optionExpand;
    private Boolean optionReject;
    private String trialNote;
    private String finishNote;
    private Boolean restoredProven;
    private LocalDateTime createdAt;
    private LocalDateTime activatedAt;
    private LocalDateTime isolatedAt;
    private LocalDateTime finishedAt;

    /** 当前下一步可执行的隔离步骤序号（全部完成则为 null） */
    private Integer nextIsolateStepNo;
    /** 当前下一步可执行的复供步骤序号 */
    private Integer nextRestoreStepNo;

    private List<IsolationStepVO> steps;
    private List<IsolationImpactVO> impacts;
    /** 扩大隔离 / 拒绝启用 取舍明细 */
    private IsolationChoiceVO expandOption;
    private IsolationChoiceVO rejectOption;

    /** 现场当前真实阀态（按方案相关井号裁剪） */
    private List<ValveLiveStateVO> liveValves;

    @Data
    public static class IsolationChoiceVO {
        private String key;
        private String title;
        private String consequence;
        private List<String> extraBlockedWells;
        private List<String> extraAffectedStationCodes;
        private List<String> maintainableStationCodes;
    }

    @Data
    public static class ValveLiveStateVO {
        private String wellNo;
        private String openState;
        private String repairStatus;
        private Boolean inoperableFlag;
        private Boolean egressGuard;
    }
}
