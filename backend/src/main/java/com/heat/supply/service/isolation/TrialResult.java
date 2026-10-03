package com.heat.supply.service.isolation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.heat.supply.domain.HeatStation;
import com.heat.supply.domain.PipeSegment;
import com.heat.supply.domain.ValveWell;

/**
 * 隔离试算结果（不落库的中间产物）：
 * 阻断边界、围禁区、必须开启阀、动作序列（含人员撤离安全门排序）、
 * 证明结论与受影响 / 保供换热站、扩大隔离与拒绝启用两种取舍后果。
 */
public class TrialResult {

    /** 阀门动作（序列中的一项） */
    public static class ValveAction {
        public String wellNo;
        public String fromState;
        public String plannedState;
        /** 计划关闭 / 原本关闭 / 必须开启 / 计划开启 */
        public String kind;
        public boolean egressGuard;
        public String note;
    }

    /** 换热站供热后果 */
    public static class StationImpact {
        public Long stationId;
        public String stationCode;
        public String stationName;
        public String tapWellNo;
        /** 停暖 / 保供 / 原本停暖 */
        public String result;
        public String reason;
        public java.math.BigDecimal areaWanM2;
        public int households;
    }

    /** 取舍选项（扩大隔离 / 拒绝启用） */
    public static class ChoiceOption {
        public String key;
        public String title;
        public String consequence;
        public List<String> extraBlockedWells = new ArrayList<>();
        public List<String> extraAffectedStationCodes = new ArrayList<>();
        public List<String> maintainableStationCodes = new ArrayList<>();
    }

    public boolean proven;
    public String proofNote;
    public String strategy = "最小隔离";

    public Set<String> blockedWells = new LinkedHashSet<>();
    public Set<String> enclosedSegmentCodes = new LinkedHashSet<>();
    public Set<String> mustOpenWells = new LinkedHashSet<>();

    /** 已关闭的边界阀（含原本关闭，仅做现场核验） */
    public Set<String> alreadyClosedWells = new LinkedHashSet<>();
    /** 现场不可操作、导致边界外扩或无法隔离的阀 */
    public Set<String> inoperableWells = new LinkedHashSet<>();
    /** 人员撤离安全门（计划关闭里最后关的阀） */
    public String egressWellNo;

    public List<ValveAction> actions = new ArrayList<>();
    public List<StationImpact> impacts = new ArrayList<>();
    public ChoiceOption expandOption;
    public ChoiceOption rejectOption;

    public List<String> warnings = new ArrayList<>();
    public List<String> maintainableStationCodes = new ArrayList<>();

    public int affectedCount;
    public int affectedHouseholds;
    public java.math.BigDecimal affectedArea = java.math.BigDecimal.ZERO;

    /** 两种取舍都要给前端展示 */
    public boolean canExpand;
    public boolean canReject = true;

    /** 基线（方案试算前）即停暖的运行换热站，避免把既有停暖误算到本次故障头上 */
    public transient Set<String> baselineColdWells = new LinkedHashSet<>();
}
