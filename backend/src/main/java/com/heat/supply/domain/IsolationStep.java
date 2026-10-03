package com.heat.supply.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 隔离方案阀门步骤：区分计划关闭 / 原本关闭 / 必须开启 / 计划开启，
 * 现场逐步确认时记录实际状态、确认人与偏差原因。
 */
@Entity
@Table(name = "isolation_step", uniqueConstraints =
        @UniqueConstraint(name = "uk_plan_stage_no", columnNames = {"plan_id", "stage", "step_no"}))
@Getter
@Setter
public class IsolationStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private IsolationPlan plan;

    /** 执行顺序，从 1 开始 */
    @Column(name = "step_no", nullable = false)
    private Integer stepNo;

    /** 阶段：隔离 / 复供 */
    @Column(name = "stage", nullable = false, length = 16)
    private String stage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "valve_well_id", nullable = false)
    private ValveWell valveWell;

    @Column(name = "well_no", nullable = false, length = 32)
    private String wellNo;

    /** 动作类别：计划关闭 / 原本关闭 / 必须开启 / 计划开启 */
    @Column(name = "action_kind", nullable = false, length = 24)
    private String actionKind;

    /** 计划动作后状态：全开 / 半开 / 关闭 */
    @Column(name = "planned_state", nullable = false, length = 16)
    private String plannedState;

    /** 方案生成时的现场基线状态 */
    @Column(name = "from_state", length = 16)
    private String fromState;

    /** 人员撤离安全门步骤（最后关闭 / 最先开启） */
    @Column(name = "egress_guard")
    private Boolean egressGuard;

    /** 步骤状态：待执行/已完成/跳过/偏差/冲突/暂停 */
    @Column(name = "step_status", nullable = false, length = 16)
    private String stepStatus;

    /** 现场回报实际状态 */
    @Column(name = "actual_state", length = 16)
    private String actualState;

    @Column(name = "confirmed_by", length = 32)
    private String confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    /** 偏差原因：未关闭 / 他人改动 / 现场不可操作 */
    @Column(name = "deviation_reason", length = 255)
    private String deviationReason;

    @Column(name = "note", length = 255)
    private String note;
}
