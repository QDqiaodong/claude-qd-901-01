package com.heat.supply.dto;

import java.util.List;

import lombok.Data;

/**
 * 归并预检结果：一次看清地址、热表号、换热站、供热状态与双方关联业务，
 * 并给出硬性阻断项、状态冲突项、各年度采暖费处置预案和供热状态选择的影响。
 */
@Data
public class MergePrecheckVO {

    private MergeSideVO kept;
    private MergeSideVO away;

    /** 地址四项（小区/楼栋/单元/房号）是否完全一致 */
    private boolean addressSame;

    /** 热表号是否一致（不一致必须停止归并） */
    private boolean meterSame;
    /** 换热站是否一致（不一致必须停止归并） */
    private boolean stationSame;

    /** 供热状态是否冲突（冲突时须由工作人员明确采用哪种状态） */
    private boolean supplyConflict;

    /** 硬性阻断项（热表号/换热站不一致、档案已归档等），非空时不允许归并 */
    private List<String> blockers;

    /** 预检提示（地址确认、状态冲突、欠费等），不阻断但需工作人员知晓 */
    private List<String> notices;

    private List<MergeFeeYearPlan> feePlans;

    private int keptSampleCount;
    private int awaySampleCount;
    private int keptOrderCount;
    private int awayOrderCount;

    /** 选择「正常供热」对后续抽测与缴费的影响 */
    private String impactActive;
    /** 选择「已报停」对后续抽测与缴费的影响 */
    private String impactSuspended;
}
