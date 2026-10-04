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
import lombok.Getter;
import lombok.Setter;

/**
 * 重复热用户档案归并审计：一次归并一条记录，与引用迁移、收费处置同事务写入，
 * 任一步骤失败整次回退（连审计也不留成功痕迹）；失败回退由独立事务补记「失败」记录。
 */
@Entity
@Table(name = "resident_merge_log")
@Getter
@Setter
public class ResidentMergeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 归并单号 MG-yyyyMMdd-序号 */
    @Column(name = "merge_no", nullable = false, unique = true, length = 32)
    private String mergeNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kept_resident_id")
    private Resident keptResident;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "away_resident_id")
    private Resident awayResident;

    /** 保留档案归并前快照：地址/热表号/换热站/供热状态 */
    @Column(name = "kept_snapshot", length = 255)
    private String keptSnapshot;

    /** 待归并档案归并前快照 */
    @Column(name = "away_snapshot", length = 255)
    private String awaySnapshot;

    /** 供热状态不一致时工作人员明确采用的状态 */
    @Column(name = "chosen_supply_status", length = 16)
    private String chosenSupplyStatus;

    /** 分年度处置方案 JSON：重算 / 保留待复核 / 原样迁移 */
    @Column(name = "fee_plan_json")
    private String feePlanJson;

    /** 改挂的室温抽测条数 */
    @Column(name = "sample_count", nullable = false)
    private int sampleCount;

    /** 改挂的报修记录条数 */
    @Column(name = "order_count", nullable = false)
    private int orderCount;

    /** 迁移 / 处置的收费记录条数 */
    @Column(name = "fee_count", nullable = false)
    private int feeCount;

    /** 已归并 / 失败回退 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 失败回退原因（独立事务写入） */
    @Column(name = "fail_reason", length = 255)
    private String failReason;

    /** 经办人 */
    @Column(name = "operator", length = 32)
    private String operator;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
