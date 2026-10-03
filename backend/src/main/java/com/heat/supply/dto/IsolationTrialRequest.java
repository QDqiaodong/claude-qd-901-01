package com.heat.supply.dto;

import java.util.List;

import lombok.Data;

/**
 * 隔离方案试算请求：选定故障管段 + 现场阀态覆盖（人工调整 / 现场不可操作）+ 人工取舍。
 * planId 为空表示新建试算；带 planId 表示对试算中/已暂停方案重新试算。
 */
@Data
public class IsolationTrialRequest {

    /** 故障管段 id */
    private Long segmentId;

    /** 故障现象 / 原因 */
    private String faultReason;

    /** 重新试算时指定已有方案 */
    private Long planId;

    /** 取舍：最小隔离 / 扩大隔离（证明失败且存在扩大选项时生效） */
    private String strategy;

    /** 人工调整：阀门现场状态覆盖 */
    private List<ValveOverride> valveOverrides;

    /** 人工追加关闭的井号 */
    private List<String> extraCloseWells;

    /** 人工从计划关闭中剔除的井号（拒绝关该阀，按不可操作处理，会触发扩张或证明失败） */
    private List<String> excludedCloseWells;

    /** 人工调整说明 */
    private String adjustNote;

    @Data
    public static class ValveOverride {
        private String wellNo;
        /** 现场实际开关状态：全开 / 半开 / 关闭；为空只改不可操作标记 */
        private String openState;
        /** 现场是否不可操作（卡涩 / 占压 / 泄漏区） */
        private Boolean inoperable;
    }
}
