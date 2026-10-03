package com.heat.supply.dto;

import lombok.Data;

/** 现场逐步确认阀门步骤 */
@Data
public class IsolationConfirmRequest {

    /** 现场回报的实际阀态：全开 / 半开 / 关闭 */
    private String actualState;

    /** 确认人 */
    private String confirmedBy;

    /** true=计划相符完成；false=反馈与计划不符，记偏差并暂停后续步骤 */
    private Boolean matchPlan;

    /** 偏差原因（现场不可操作 / 他人已改动 / 阀门卡涩 等） */
    private String deviationReason;
}
