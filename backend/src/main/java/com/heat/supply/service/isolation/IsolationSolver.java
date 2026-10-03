package com.heat.supply.service.isolation;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.heat.supply.common.Dict;
import com.heat.supply.domain.HeatStation;
import com.heat.supply.domain.PipeSegment;
import com.heat.supply.domain.ValveWell;

/**
 * 隔离试算引擎（无状态组件）：
 * 1. 以「关阀 = 井位阻断、管段 = 井间无向边」建图，故障边先从图中拿掉；
 * 2. 从故障段两端井向外做边界扩张：开着且可操作的阀纳入计划关闭，原本关闭的直接计入边界，
 *    遇到现场不可操作的开阀则越过它继续向相邻管段扩张（扩大停暖）；
 * 3. 用扩张后的关阀集合做一次热源 BFS 重新证明：故障段两端井都不可达任一运行热源，才算隔离成功；
 * 4. 计算必须开启阀（原本关闭、打开后能恢复更多在役换热站且不破坏隔离）与保供/停暖换热站；
 * 5. 动作序列满足人员撤离安全门最后关闭，并给出「扩大隔离 / 拒绝启用」两种取舍后果。
 */
@Component
public class IsolationSolver {

    /**
     * 执行一次试算。
     *
     * @param graph               已叠加现场状态覆盖的管网图（故障边未移除）
     * @param faultSegment        故障管段
     * @param forcedInoperable    人工标记的现场不可操作井
     * @param extraCloseWells     人工追加关闭的井（现场调整）
     * @param excludedCloseWells  人工从计划关闭中剔除的井（现场调整，按拒绝关阀处理 → 触发扩张/证明失败）
     * @param householdsByStation 换热站 → 在供居民户数
     */
    public TrialResult solve(HeatGraph graph,
                             PipeSegment faultSegment,
                             Set<String> forcedInoperable,
                             Set<String> extraCloseWells,
                             Set<String> excludedCloseWells,
                             Map<Long, Integer> householdsByStation) {
        TrialResult result = new TrialResult();
        result.inoperableWells.addAll(forcedInoperable);

        String fa = faultSegment.getStartWellNo();
        String fb = faultSegment.getEndWellNo();

        // 基线（当前现场实况，故障边仍在网）下的热源可达集，用来区分「原本停暖」与「本次停暖」
        Set<String> baselineReachable = graph.reachableFromSources(Collections.emptySet());
        for (HeatStation station : graph.stations()) {
            if (isRunning(station) && station.getTapWellNo() != null
                    && !baselineReachable.contains(station.getTapWellNo())) {
                result.baselineColdWells.add(station.getTapWellNo());
            }
        }

        // ---------- 1. 边界扩张（关阀围禁） ----------
        Set<String> closed = new LinkedHashSet<>();          // 全部阻断阀（计划关闭 + 原本关闭 + 人工追加）
        Set<String> plannedClose = new LinkedHashSet<>();    // 计划关闭（开着、可操作）
        Set<String> alreadyClosed = new LinkedHashSet<>();   // 原本关闭
        Set<String> enclosed = new LinkedHashSet<>();        // 围进隔离区的管段
        Set<String> visitedWells = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();

        enclosed.add(faultSegment.getSegCode());
        // 从故障段两端井开始扩张
        stack.push(fa);
        stack.push(fb);

        Set<String> blockedByInoperable = new LinkedHashSet<>();
        while (!stack.isEmpty()) {
            String wellNo = stack.pop();
            if (!visitedWells.add(wellNo)) {
                continue;
            }
            String state = graph.stateOf(wellNo);
            boolean closedNow = Dict.isValveClosed(state);
            boolean inoperable = graph.isInoperable(wellNo, forcedInoperable)
                    || excludedCloseWells.contains(wellNo);

            if (closedNow) {
                // 原本关闭：天然边界，不列入操作序列，只做现场核验
                closed.add(wellNo);
                alreadyClosed.add(wellNo);
                continue;
            }
            if (!inoperable) {
                // 开着且可操作：计划关闭，形成边界
                closed.add(wellNo);
                plannedClose.add(wellNo);
                continue;
            }
            // 开着但现场不可操作（或人工拒绝关阀）：越过该阀，向相邻在役管段的对侧井继续扩张封口
            blockedByInoperable.add(wellNo);
            List<HeatGraph.Edge> incident = graph.incidentEdges(wellNo);
            if (incident.isEmpty()) {
                result.warnings.add("阀门井 " + wellNo + " 现场不可操作且为悬空节点，无法从该侧封口");
                continue;
            }
            for (HeatGraph.Edge edge : incident) {
                if (edge.segment.getId().equals(faultSegment.getId())) {
                    continue;
                }
                if ("停用".equals(edge.segment.getSegmentStatus())) {
                    continue;
                }
                enclosed.add(edge.segment.getSegCode());
                stack.push(edge.other(wellNo));
            }
        }
        // 人工追加关闭
        for (String wellNo : extraCloseWells) {
            if (graph.well(wellNo) == null) {
                continue;
            }
            if (!closed.contains(wellNo)) {
                if (Dict.isValveClosed(graph.stateOf(wellNo))) {
                    alreadyClosed.add(wellNo);
                } else {
                    plannedClose.add(wellNo);
                }
                closed.add(wellNo);
            }
        }

        result.blockedWells.addAll(closed);
        result.alreadyClosedWells.addAll(alreadyClosed);
        result.enclosedSegmentCodes.addAll(enclosed);

        // ---------- 2. 重新证明：故障段两端井均不可达运行热源 ----------
        // 证明图：故障边移除 + closed 关阀
        HeatGraph proofGraph = graph.rebuildWithoutFault(faultSegment.getId());
        Set<String> reachable = proofGraph.reachableFromSources(closed);
        boolean aCold = !reachable.contains(fa);
        boolean bCold = !reachable.contains(fb);
        result.proven = aCold && bCold;
        if (result.proven) {
            result.proofNote = "证明通过：故障段 " + faultSegment.getSegCode() + "（" + fa + "→" + fb
                    + "）两端井在关断边界下均不可达任一运行热源，故障段已与运行管网隔开";
        } else {
            List<String> hotEnds = new ArrayList<>();
            if (!aCold) {
                hotEnds.add(fa);
            }
            if (!bCold) {
                hotEnds.add(fb);
            }
            result.proofNote = "证明失败：故障段端井 " + String.join("、", hotEnds)
                    + " 仍可达运行热源（边界存在现场不可操作阀），故障段未与运行管网隔开";
        }

        // ---------- 3. 必须开启阀 ----------
        // 枚举所有「当前关闭、不在关断边界上」的在役阀：若把它打开后故障段两端井仍然不可达热源
        // （不破坏隔离）、且能让某个在关断边界下停暖的在役换热站重新可达热源，则列入「必须开启」。
        // 必须开启阀与关断边界共同决定最终可达性，因此保供判定以最终集合为准，不能把已恢复的站
        // 当成「开前已保供」而漏掉后续候选。
        Set<String> mustOpen = new LinkedHashSet<>();
        List<ValveWell> closedCandidates = new ArrayList<>();
        for (ValveWell well : graph.allWells()) {
            String no = well.getWellNo();
            // 关断边界上的阀（计划关闭或原本关闭）不能再作为「必须开启」候选：
            // 它们承担围禁职责，打开必然破坏隔离或推翻已核验边界。
            if (closed.contains(no)) {
                continue;
            }
            if (Dict.isValveClosed(graph.stateOf(no))) {
                closedCandidates.add(well);
            }
        }
        for (ValveWell candidate : closedCandidates) {
            Set<String> trialClosed = new LinkedHashSet<>(closed);
            Map<String, String> openOverride = new LinkedHashMap<>(graph.overrides());
            openOverride.put(candidate.getWellNo(), "全开");
            HeatGraph trialGraph = graph.rebuildProof(faultSegment.getId(), openOverride);
            Set<String> trialReach = trialGraph.reachableFromSources(trialClosed);
            boolean stillIsolated = !trialReach.contains(fa) && !trialReach.contains(fb);
            if (!stillIsolated) {
                continue;
            }
            boolean helpsStation = false;
            for (HeatStation station : graph.stations()) {
                if (!isRunning(station) || station.getTapWellNo() == null) {
                    continue;
                }
                // 对照基线（无任何必须开启阀、仅有关断边界）的可达集
                boolean coldBefore = !reachable.contains(station.getTapWellNo());
                boolean warmAfter = trialReach.contains(station.getTapWellNo());
                if (coldBefore && warmAfter) {
                    helpsStation = true;
                    break;
                }
            }
            if (helpsStation) {
                mustOpen.add(candidate.getWellNo());
            }
        }
        result.mustOpenWells.addAll(mustOpen);

        // 应用必须开启后的最终可达集
        Map<String, String> finalOverride = new LinkedHashMap<>(graph.overrides());
        Set<String> finalClosed = new LinkedHashSet<>(closed);
        for (String no : mustOpen) {
            finalOverride.put(no, "全开");
            finalClosed.remove(no);
        }
        HeatGraph finalGraph = graph.rebuildProof(faultSegment.getId(), finalOverride);
        Set<String> finalReach = finalGraph.reachableFromSources(finalClosed);

        // ---------- 4. 人员撤离安全门：关断序列最后关闭 ----------
        chooseEgressGuard(graph, plannedClose, faultSegment, closed, result);

        // ---------- 5. 换热站后果 ----------
        buildImpacts(graph, finalReach, baselineReachable, householdsByStation, result);

        // ---------- 6. 动作序列 ----------
        buildActions(graph, plannedClose, alreadyClosed, mustOpen, result);

        // ---------- 7. 扩大隔离 / 拒绝启用 取舍 ----------
        buildChoices(graph, proofGraph, faultSegment, closed, alreadyClosed, blockedByInoperable, finalReach, result);

        result.canExpand = result.expandOption != null;
        result.canReject = true;
        return result;
    }

    /**
     * 选撤离安全门：优先库内标记 egress_guard 的计划关闭阀；
     * 没有标记时，取「关断后仍让故障段端井通过未封管段连通到最多外部井」的那道阀作为最后关闭阀。
     */
    private void chooseEgressGuard(HeatGraph graph, Set<String> plannedClose, PipeSegment fault,
                                   Set<String> closed, TrialResult result) {
        for (String no : plannedClose) {
            ValveWell well = graph.well(no);
            if (well != null && Boolean.TRUE.equals(well.getEgressGuard())) {
                result.egressWellNo = no;
                return;
            }
        }
        // 兜底：与故障段直接相邻、且关闭前仍连着至少一条非故障在役边的阀，视为撤离路径上的门
        String fallback = null;
        int best = -1;
        for (String no : plannedClose) {
            if (no.equals(fault.getStartWellNo()) || no.equals(fault.getEndWellNo())) {
                int openNeighbors = 0;
                for (HeatGraph.Edge edge : graph.incidentEdges(no)) {
                    if (edge.segment.getId().equals(fault.getId())) {
                        continue;
                    }
                    String other = edge.other(no);
                    if (!closed.contains(other)) {
                        openNeighbors++;
                    }
                }
                if (openNeighbors > best) {
                    best = openNeighbors;
                    fallback = no;
                }
            }
        }
        result.egressWellNo = fallback;
    }

    private void buildImpacts(HeatGraph graph, Set<String> finalReach, Set<String> baselineReach,
                              Map<Long, Integer> householdsByStation, TrialResult result) {
        for (HeatStation station : graph.stations()) {
            if (!isRunning(station)) {
                continue;
            }
            TrialResult.StationImpact impact = new TrialResult.StationImpact();
            impact.stationId = station.getId();
            impact.stationCode = station.getCode();
            impact.stationName = station.getName();
            impact.tapWellNo = station.getTapWellNo();
            impact.areaWanM2 = station.getAreaWanM2() == null ? BigDecimal.ZERO : station.getAreaWanM2();
            impact.households = householdsByStation.getOrDefault(station.getId(), 0);
            if (station.getTapWellNo() == null) {
                impact.result = "保供";
                impact.reason = "未挂接一次网并网井";
            } else if (finalReach.contains(station.getTapWellNo())) {
                impact.result = "保供";
                impact.reason = "关断后并网井 " + station.getTapWellNo() + " 仍有热源路径（环网多路径保供）";
                result.maintainableStationCodes.add(station.getCode());
            } else if (result.baselineColdWells.contains(station.getTapWellNo())) {
                impact.result = "原本停暖";
                impact.reason = "试算前并网井 " + station.getTapWellNo() + " 已不可达热源，非本次故障新增";
            } else {
                impact.result = "停暖";
                impact.reason = "关断后并网井 " + station.getTapWellNo() + " 与运行热源间无导通路径";
                result.affectedCount++;
                result.affectedHouseholds += impact.households;
                result.affectedArea = result.affectedArea.add(impact.areaWanM2);
            }
            result.impacts.add(impact);
        }
        result.impacts.sort((a, b) -> a.stationCode.compareTo(b.stationCode));
    }

    /**
     * 动作序列：必须开启在前（先打通保供路径），计划关闭在后；撤离安全门在计划关闭中排最后。
     * 「原本关闭」只核验不操作，排在关闭动作之后作为现场确认项。
     */
    private void buildActions(HeatGraph graph, Set<String> plannedClose, Set<String> alreadyClosed,
                              Set<String> mustOpen, TrialResult result) {
        List<String> openOrder = new ArrayList<>(mustOpen);
        Collections.sort(openOrder);
        List<String> closeOrder = new ArrayList<>(plannedClose);
        if (result.egressWellNo != null) {
            closeOrder.remove(result.egressWellNo);
            Collections.sort(closeOrder);
            closeOrder.add(result.egressWellNo);
        } else {
            Collections.sort(closeOrder);
        }
        List<String> verifyOrder = new ArrayList<>(alreadyClosed);
        Collections.sort(verifyOrder);

        // 安全顺序：① 先开必须开启阀打通保供路径；
        //          ② 现场核验所有「原本关闭」阀确实处于关闭态（不操作，是后续关断的前提）；
        //          ③ 再按序关闭计划阀，撤离安全门排在计划关闭最后。
        for (String no : openOrder) {
            ValveWell well = graph.well(no);
            TrialResult.ValveAction action = new TrialResult.ValveAction();
            action.wellNo = no;
            action.fromState = graph.stateOf(no);
            action.plannedState = "全开";
            action.kind = Dict.KIND_MUST_OPEN;
            action.note = "必须开启：打通环网保供路径，开启后不破坏故障隔离";
            result.actions.add(action);
            if (well == null) {
                result.warnings.add("必须开启阀 " + no + " 在阀门井台账中缺失");
            }
        }
        for (String no : verifyOrder) {
            TrialResult.ValveAction action = new TrialResult.ValveAction();
            action.wellNo = no;
            action.fromState = graph.stateOf(no);
            action.plannedState = "关闭";
            action.kind = Dict.KIND_ALREADY_CLOSED;
            action.note = "原本关闭：不操作，关断前现场挂牌核验其确实处于关闭态";
            result.actions.add(action);
        }
        for (String no : closeOrder) {
            TrialResult.ValveAction action = new TrialResult.ValveAction();
            action.wellNo = no;
            action.fromState = graph.stateOf(no);
            action.plannedState = "关闭";
            action.kind = Dict.KIND_PLAN_CLOSE;
            action.egressGuard = no.equals(result.egressWellNo);
            if (action.egressGuard) {
                action.note = "人员撤离安全门：确认井室/管段人员全部撤离后方可关闭（关断序列最后一步）";
            }
            result.actions.add(action);
        }
    }

    /**
     * 取舍构造（两种现场情形都要让调度员显式选）：
     * A. 不可操作阀迫使边界外扩、扩大后的关断可以证明隔离 —— 默认拒绝启用，
     *    另给「扩大隔离」选项，列出相对故障段理论最小边界多关的阀、多停的换热站；
     * B. 外扩后仍证明失败 —— 只能拒绝启用等待替代路径（若还存在更远的可操作封口阀，
     *    第二轮 BFS 会在本方法里再尝试一次扩大）。
     */
    private void buildChoices(HeatGraph graph, HeatGraph proofGraph, PipeSegment fault,
                              Set<String> closed, Set<String> alreadyClosed,
                              Set<String> blockedByInoperable,
                              Set<String> currentReach, TrialResult result) {
        boolean forcedExpansion = !blockedByInoperable.isEmpty()
                && result.enclosedSegmentCodes.size() > 1;
        if (result.proven && !forcedExpansion) {
            result.strategy = "最小隔离";
            return;
        }

        if (result.proven) {
            // 情形 A：扩大后的边界能隔离，但多停了暖，必须由调度员在「扩大停暖」与「拒绝启用」间选择。
            // 扩大代价阀 = 当前计划关闭动作里、不属于故障段两端井的阀（原本关闭阀不动作，不计入）。
            Set<String> extraValves = new LinkedHashSet<>();
            for (TrialResult.ValveAction action : result.actions) {
                String no = action.wellNo;
                if (Dict.KIND_PLAN_CLOSE.equals(action.kind)
                        && !no.equals(fault.getStartWellNo()) && !no.equals(fault.getEndWellNo())) {
                    extraValves.add(no);
                }
            }

            // 理论最小可达集：假设故障段两端阀都能关，其余边界维持现状（已经关闭的阀照旧）。
            // 注意现场本来就关闭的联络阀（如环臂 J112）不能为了「对比」临时打开，否则会把
            // 现场本就停暖的站算成扩大隔离的新代价。
            Set<String> idealClosed = new LinkedHashSet<>(alreadyClosed);
            idealClosed.add(fault.getStartWellNo());
            idealClosed.add(fault.getEndWellNo());
            Set<String> idealReach = proofGraph.reachableFromSources(idealClosed);

            TrialResult.ChoiceOption option = new TrialResult.ChoiceOption();
            option.key = "EXPAND";
            option.title = "扩大隔离（扩大停暖范围确保安全）";
            option.extraBlockedWells.addAll(extraValves);
            List<String> coldNames = new ArrayList<>();
            Set<String> countedCodes = new HashSet<>();
            for (HeatStation station : graph.stations()) {
                if (!isRunning(station) || station.getTapWellNo() == null
                        || !countedCodes.add(station.getCode())) {
                    continue;
                }
                boolean warmUnderIdeal = idealReach.contains(station.getTapWellNo());
                boolean coldAfterExpand = !currentReach.contains(station.getTapWellNo());
                if (warmUnderIdeal && coldAfterExpand
                        && !result.baselineColdWells.contains(station.getTapWellNo())) {
                    option.extraAffectedStationCodes.add(station.getCode());
                    coldNames.add(station.getCode() + " " + station.getName());
                }
                if (!coldAfterExpand) {
                    option.maintainableStationCodes.add(station.getCode());
                }
            }
            option.consequence = "故障段端井存在现场不可操作阀（" + String.join("、", blockedByInoperable)
                    + "），已自动越界扩张；接受扩大隔离将按上述边界关断（追加操作 "
                    + String.join("、", extraValves)
                    + "），代价是扩大停暖，新增停暖换热站："
                    + (coldNames.isEmpty() ? "无" : String.join("、", coldNames))
                    + "。现场须按同一关断序列执行并保留撤离安全门。";
            result.expandOption = option;
            result.canExpand = true;
            result.strategy = "拒绝启用";
            buildRejectOption(graph, fault, currentReach, result);
            return;
        }

        // 情形 B：当前边界证明失败，尝试更远一轮外扩
        Set<String> expandClose = new LinkedHashSet<>(closed);
        Set<String> extra = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>(blockedByInoperable);
        Set<String> expandInoperable = new HashSet<>(blockedByInoperable);
        Set<String> seen = new HashSet<>(blockedByInoperable);
        while (!queue.isEmpty()) {
            String bad = queue.poll();
            for (HeatGraph.Edge edge : graph.incidentEdges(bad)) {
                if (edge.segment.getId().equals(fault.getId())) {
                    continue;
                }
                if ("停用".equals(edge.segment.getSegmentStatus())) {
                    continue;
                }
                String out = edge.other(bad);
                if (expandClose.contains(out) || !seen.add(out)) {
                    continue;
                }
                boolean inoperable = graph.isInoperable(out, expandInoperable);
                if (Dict.isValveClosed(graph.stateOf(out))) {
                    expandClose.add(out);
                    continue;
                }
                if (!inoperable) {
                    expandClose.add(out);
                    extra.add(out);
                } else {
                    expandInoperable.add(out);
                    queue.add(out);
                }
            }
        }
        Set<String> expandReach = proofGraph.reachableFromSources(expandClose);
        boolean expandProven = !expandReach.contains(fault.getStartWellNo())
                && !expandReach.contains(fault.getEndWellNo());

        if (expandProven && !extra.isEmpty()) {
            TrialResult.ChoiceOption option = new TrialResult.ChoiceOption();
            option.key = "EXPAND";
            option.title = "扩大隔离（扩大停暖范围确保安全）";
            option.extraBlockedWells.addAll(extra);
            List<String> coldNames = new ArrayList<>();
            for (HeatStation station : graph.stations()) {
                if (!isRunning(station) || station.getTapWellNo() == null) {
                    continue;
                }
                boolean warmNow = currentReach.contains(station.getTapWellNo());
                boolean coldAfter = !expandReach.contains(station.getTapWellNo());
                if (warmNow && coldAfter && !result.baselineColdWells.contains(station.getTapWellNo())) {
                    option.extraAffectedStationCodes.add(station.getCode());
                    coldNames.add(station.getCode() + " " + station.getName());
                }
                if (expandReach.contains(station.getTapWellNo())) {
                    option.maintainableStationCodes.add(station.getCode());
                }
            }
            option.consequence = "追加关闭 " + String.join("、", extra)
                    + " 后故障段可彻底隔离；代价是扩大停暖，新增停暖换热站："
                    + (coldNames.isEmpty() ? "无" : String.join("、", coldNames))
                    + "。现场须按同一关断序列执行并保留撤离安全门。";
            result.expandOption = option;
            result.canExpand = true;
        }
        result.strategy = "拒绝启用";
        buildRejectOption(graph, fault, currentReach, result);
    }

    private void buildRejectOption(HeatGraph graph, PipeSegment fault, Set<String> currentReach,
                                   TrialResult result) {
        TrialResult.ChoiceOption reject = new TrialResult.ChoiceOption();
        reject.key = "REJECT";
        reject.title = "拒绝启用方案（等待替代路径）";
        reject.consequence = "不扩大关阀范围，故障段 " + fault.getSegCode()
                + " 暂不满足隔离条件、禁止动火检修；保持现场值守并等待替代路径投运或不可操作阀恢复条件，"
                + "方案不予启用，当前在网换热站维持现状供热。";
        for (HeatStation station : graph.stations()) {
            if (isRunning(station) && station.getTapWellNo() != null
                    && currentReach.contains(station.getTapWellNo())) {
                reject.maintainableStationCodes.add(station.getCode());
            }
        }
        result.rejectOption = reject;
    }

    private boolean isRunning(HeatStation station) {
        return "运行".equals(station.getStatus());
    }
}
