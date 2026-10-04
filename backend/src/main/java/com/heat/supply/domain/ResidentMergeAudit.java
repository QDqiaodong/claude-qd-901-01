package com.heat.supply.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 热用户归并审计：一次归并一行。
 * 审计写入与引用迁移在同一事务内，任一失败整笔回退，刷新后仍是归并前状态。
 */
@Entity
@Table(name = "resident_merge_audit")
@Getter
@Setter
public class ResidentMergeAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 归并单号 GB-yyyyMMdd-序号 */
    @Column(name = "merge_no", nullable = false, unique = true, length = 32)
    private String mergeNo;

    /** 保留档案 id */
    @Column(name = "keeper_id", nullable = false)
    private Long keeperId;

    /** 待归并（重复）档案 id */
    @Column(name = "merged_id", nullable = false)
    private Long mergedId;

    /** 保留档案地址 + 热表号快照 */
    @Column(name = "keeper_snapshot", length = 255)
    private String keeperSnapshot;

    /** 重复档案地址 + 热表号快照 */
    @Column(name = "merged_snapshot", length = 255)
    private String mergedSnapshot;

    /** 归并后采用的供热状态：正常供热 / 已报停 */
    @Column(name = "chosen_supply_status", length = 16)
    private String chosenSupplyStatus;

    /** 改挂的室温抽测条数 */
    @Column(name = "sample_moved_count")
    private Integer sampleMovedCount;

    /** 改挂的报修工单条数 */
    @Column(name = "order_moved_count")
    private Integer orderMovedCount;

    /** 改挂（含作废 / 标复核）的收费记录条数 */
    @Column(name = "fee_moved_count")
    private Integer feeMovedCount;

    /** 两边均未缴费、按保留档案计费面积重算的年度清单 */
    @Column(name = "fee_recompute_year", length = 64)
    private String feeRecomputeYear;

    /** 任一方已有实收、标待财务复核的年度清单 */
    @Column(name = "fee_review_year", length = 64)
    private String feeReviewYear;

    /** 逐类引用迁移 / 收费处置明细 JSON */
    @Column(name = "details_json", columnDefinition = "TEXT")
    private String detailsJson;

    @Column(name = "operator", length = 32)
    private String operator;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
