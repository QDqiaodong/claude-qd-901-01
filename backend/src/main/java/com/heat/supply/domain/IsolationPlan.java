package com.heat.supply.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 管网隔离方案：选定故障管段后经拓扑试算生成，贯穿
 * 试算中 → 待执行 → 执行中 → 已隔离 → 已复供 的闭环，
 * 中途出现偏差可转「已暂停」，收口失败转「已失败」，放弃方案转「已废止」。
 */
@Entity
@Table(name = "isolation_plan")
@Getter
@Setter
public class IsolationPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 方案编号 ISO-yyyyMMdd-序号 */
    @Column(name = "plan_no", nullable = false, unique = true, length = 32)
    private String planNo;

    /** 故障管段 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fault_segment_id", nullable = false)
    private PipeSegment faultSegment;

    /** 故障现象 / 填报原因 */
    @Column(name = "fault_reason", length = 255)
    private String faultReason;

    /** 方案阶段：试算中/待执行/执行中/已隔离/已暂停/已失败/已复供/已废止 */
    @Column(name = "phase", nullable = false, length = 16)
    private String phase;

    /** 取舍策略：最小隔离 / 扩大隔离 / 拒绝启用 */
    @Column(name = "strategy", nullable = false, length = 16)
    private String strategy;

    /** 最近一次证明结果：故障段是否已与运行热源侧隔开 */
    @Column(name = "isolated_proven")
    private Boolean isolatedProven;

    /** 证明说明：阻断边界 / 失败原因 */
    @Column(name = "proof_note", length = 255)
    private String proofNote;

    /** 试算阻断边界阀门井号（逗号分隔） */
    @Column(name = "blocked_well_nos", length = 512)
    private String blockedWellNos;

    /** 被围进隔离区的管段编号（逗号分隔） */
    @Column(name = "enclosed_segment_codes", length = 512)
    private String enclosedSegmentCodes;

    /** 必须开启的阀门井号（保供路径，逗号分隔） */
    @Column(name = "forced_open_well_nos", length = 512)
    private String forcedOpenWellNos;

    /** 受影响换热站编号快照（逗号分隔） */
    @Column(name = "affected_station_codes", length = 512)
    private String affectedStationCodes;

    @Column(name = "affected_station_count")
    private Integer affectedStationCount;

    /** 受影响供热面积（万㎡）快照 */
    @Column(name = "affected_area_wan_m2", precision = 10, scale = 2)
    private BigDecimal affectedAreaWanM2;

    /** 受影响居民户数快照 */
    @Column(name = "affected_households")
    private Integer affectedHouseholds;

    /** 仍有保供路径的换热站编号（逗号分隔） */
    @Column(name = "maintainable_station_codes", length = 512)
    private String maintainableStationCodes;

    /** 扩大停暖 vs 拒绝启用 的取舍说明 */
    @Column(name = "blocked_choice_note", length = 512)
    private String blockedChoiceNote;

    /** 是否存在「扩大隔离」可选结果 */
    @Column(name = "option_expand")
    private Boolean optionExpand;

    /** 是否存在「拒绝启用等待替代路径」可选结果 */
    @Column(name = "option_reject")
    private Boolean optionReject;

    /** 最近一次拓扑试算结论与人工调整记录 */
    @Column(name = "trial_note", length = 1024)
    private String trialNote;

    /** 本次试算采用的现场阀门状态覆盖（人工调整 / 现场不可操作），JSON */
    @Column(name = "network_options_json")
    private String networkOptionsJson;

    /** 收口记录：完成情况、偏差原因、处置结果 */
    @Column(name = "finish_note", length = 1024)
    private String finishNote;

    /** 复供后证明：仍不可达清单是否已列出 */
    @Column(name = "restored_proven")
    private Boolean restoredProven;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "isolated_at")
    private LocalDateTime isolatedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @OneToMany(mappedBy = "plan", fetch = FetchType.LAZY)
    @OrderBy("stage asc, stepNo asc")
    private List<IsolationStep> steps = new ArrayList<>();
}
