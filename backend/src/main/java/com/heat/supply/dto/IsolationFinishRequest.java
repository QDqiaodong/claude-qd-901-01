package com.heat.supply.dto;

import lombok.Data;

/** 中途失败后的三种收口处置 */
@Data
public class IsolationFinishRequest {

    /** EXPAND=扩大隔离（重新试算） / REVERT=按安全顺序恢复已动阀门 / HOLD=保持现场状态等待处置 */
    private String disposition;

    private String note;

    /** EXPAND 时复用试算请求体（可空，空则在原方案上直接带扩大策略重算） */
    private IsolationTrialRequest retrial;
}
