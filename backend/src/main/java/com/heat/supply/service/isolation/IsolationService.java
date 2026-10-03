package com.heat.supply.service.isolation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.heat.supply.common.BizException;
import com.heat.supply.common.Dict;
import com.heat.supply.domain.HeatSource;
import com.heat.supply.domain.HeatStation;
import com.heat.supply.domain.IsolationPlan;
import com.heat.supply.domain.IsolationStep;
import com.heat.supply.domain.PipeSegment;
import com.heat.supply.domain.ValveWell;
import com.heat.supply.dto.IsolationConfirmRequest;
import com.heat.supply.dto.IsolationFinishRequest;
import com.heat.supply.dto.IsolationImpactVO;
import com.heat.supply.dto.IsolationPlanVO;
import com.heat.supply.dto.IsolationStepVO;
import com.heat.supply.dto.IsolationTrialRequest;
import com.heat.supply.repository.HeatSourceRepository;
import com.heat.supply.repository.HeatStationRepository;
import com.heat.supply.repository.IsolationPlanRepository;
import com.heat.supply.repository.IsolationStepRepository;
import com.heat.supply.repository.PipeSegmentRepository;
import com.heat.supply.repository.ResidentRepository;
import com.heat.supply.repository.ValveWellRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 管网隔离方案闭环服务：
 * 拓扑试算（最小/扩大/拒绝三态）→ 人工调整重证 → 激活 → 现场逐步确认（偏差暂停）
 * → 已隔离 → 故障消除后按当前阀态重新生成复供顺序 → 仍不可达换热站留在异常清单。
 */
@Service
public class IsolationService {

    private static final DateTimeFormatter PLAN_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final IsolationPlanRepository planRepository;
    private final IsolationStepRepository stepRepository;
    private final PipeSegmentRepository segmentRepository;
    private final ValveWellRepository valveWellRepository;
    private final HeatSourceRepository sourceRepository;
    private final HeatStationRepository stationRepository;
    private final ResidentRepository residentRepository;
    private final IsolationSolver solver;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IsolationService(IsolationPlanRepository planRepository,
                            IsolationStepRepository stepRepository,
                            PipeSegmentRepository segmentRepository,
                            ValveWellRepository valveWellRepository,
                            HeatSourceRepository sourceRepository,
                            HeatStationRepository stationRepository,
                            ResidentRepository residentRepository,
                            IsolationSolver solver) {
        this.planRepository = planRepository;
        this.stepRepository = stepRepository;
        this.segmentRepository = segmentRepository;
        this.valveWellRepository = valveWellRepository;
        this.sourceRepository = sourceRepository;
        this.stationRepository = stationRepository;
        this.residentRepository = residentRepository;
        this.solver = solver;
    }

    // ==================================================================
    // 试算
    // ==================================================================

    @Transactional
    public IsolationPlanVO trial(IsolationTrialRequest request) {
        if (request.getSegmentId() == null && request.getPlanId() == null) {
            throw new BizException("请先选择故障管段再进行隔离试算");
        }
        IsolationPlan plan = request.getPlanId() == null ? null
                : planRepository.findById(request.getPlanId())
                .orElseThrow(() -> new BizException("隔离方案不存在：id=" + request.getPlanId()));

        Long segmentId = plan == null ? request.getSegmentId() : plan.getFaultSegment().getId();
        PipeSegment fault = segmentRepository.findById(segmentId)
                .orElseThrow(() -> new BizException("故障管段不存在：id=" + segmentId));
        if ("停用".equals(fault.getSegmentStatus())) {
            throw new BizException("管段 " + fault.getSegCode() + " 已停用，不在运行管网上，无需隔离试算");
        }
        if (plan != null && !Arrays.asList("试算中", "已暂停", "已失败").contains(plan.getPhase())) {
            throw new BizException("方案当前为「" + plan.getPhase() + "」状态，不能重新试算；请在偏差暂停或失败收口后再试算");
        }

        // 现场状态覆盖
        Map<String, String> stateOverrides = new LinkedHashMap<>();
        Set<String> forcedInoperable = new LinkedHashSet<>();
        List<String> overrideNotes = new ArrayList<>();
        if (request.getValveOverrides() != null) {
            for (IsolationTrialRequest.ValveOverride override : request.getValveOverrides()) {
                if (override.getWellNo() == null) {
                    continue;
                }
                ValveWell well = valveWellRepository.findByWellNo(override.getWellNo())
                        .orElseThrow(() -> new BizException("阀门井不存在：" + override.getWellNo()));
                if (override.getOpenState() != null) {
                    if (!Dict.isValidOpenState(override.getOpenState())) {
                        throw new BizException("阀门 " + override.getWellNo() + " 的现场状态只能是：全开 / 半开 / 关闭");
                    }
                    if (!Objects.equals(override.getOpenState(), well.getOpenState())) {
                        stateOverrides.put(override.getWellNo(), override.getOpenState());
                        overrideNotes.add(override.getWellNo() + " 现场状态按「" + override.getOpenState() + "」试算");
                    }
                }
                if (Boolean.TRUE.equals(override.getInoperable())) {
                    forcedInoperable.add(override.getWellNo());
                    overrideNotes.add(override.getWellNo() + " 标记为现场不可操作");
                }
            }
        }
        Set<String> extraClose = toWellSet(request.getExtraCloseWells());
        Set<String> excludedClose = toWellSet(request.getExcludedCloseWells());

        HeatGraph graph = loadGraph(stateOverrides);
        Map<Long, Integer> households = loadHouseholds();
        TrialResult result = solver.solve(graph, fault, forcedInoperable, extraClose, excludedClose, households);

        // 取舍：调度员必须在「扩大隔离（扩大停暖）」与「拒绝启用（等待替代路径）」之间显式选择。
        // 不指定策略时保留试算默认（最小隔离可激活；凡是被迫外扩的一律默认拒绝启用）。
        TrialResult baseResult = result;
        String strategy = request.getStrategy();
        if ("扩大隔离".equals(strategy)) {
            if (baseResult.expandOption == null) {
                throw new BizException("当前不存在可行的扩大隔离封口方案，无法通过扩大停暖实现安全隔离");
            }
            Set<String> moreClose = new LinkedHashSet<>(extraClose);
            moreClose.addAll(baseResult.expandOption.extraBlockedWells);
            TrialResult expanded = solver.solve(graph, fault, forcedInoperable, moreClose, excludedClose, households);
            if (!expanded.proven) {
                throw new BizException("扩大隔离后仍无法证明故障段隔开，请选择拒绝启用等待替代路径");
            }
            // 保留取舍后果说明，动作 / 边界 / 影响以扩大试算为准
            expanded.strategy = "扩大隔离";
            expanded.expandOption = baseResult.expandOption;
            expanded.rejectOption = baseResult.rejectOption;
            result = expanded;
        } else if ("拒绝启用".equals(strategy)) {
            result.strategy = "拒绝启用";
        } else {
            // 不指定取舍：沿用试算默认（被迫外扩时引擎已置为「拒绝启用」，必须人工显式确认扩大）
            result.strategy = baseResult.strategy;
        }

        // 落库（新建或更新试算方案）
        boolean isNew = plan == null;
        if (isNew) {
            plan = new IsolationPlan();
            plan.setPlanNo(generatePlanNo());
            plan.setFaultSegment(fault);
            plan.setCreatedAt(LocalDateTime.now());
        }
        plan.setFaultReason(request.getFaultReason());
        plan.setPhase("试算中");
        plan.setStrategy(result.strategy);
        plan.setIsolatedProven(result.proven);
        plan.setProofNote(result.proofNote);
        plan.setBlockedWellNos(join(result.blockedWells));
        plan.setEnclosedSegmentCodes(join(result.enclosedSegmentCodes));
        plan.setForcedOpenWellNos(join(result.mustOpenWells));
        plan.setAffectedStationCodes(join(impactCodes(result, "停暖")));
        plan.setAffectedStationCount(result.affectedCount);
        plan.setAffectedAreaWanM2(result.affectedArea);
        plan.setAffectedHouseholds(result.affectedHouseholds);
        plan.setMaintainableStationCodes(join(new LinkedHashSet<>(result.maintainableStationCodes)));
        plan.setOptionExpand(result.expandOption != null);
        plan.setOptionReject(true);
        plan.setBlockedChoiceNote(result.proven ? null
                : (result.rejectOption == null ? null : result.rejectOption.consequence));
        StringBuilder note = new StringBuilder();
        note.append("试算结论：").append(result.proofNote);
        if (!overrideNotes.isEmpty()) {
            note.append("；人工调整：").append(String.join("；", overrideNotes));
        }
        if (request.getAdjustNote() != null && !request.getAdjustNote().isBlank()) {
            note.append("；调整说明：").append(request.getAdjustNote());
        }
        plan.setTrialNote(truncate(note.toString(), 1000));
        plan.setNetworkOptionsJson(writeOptions(stateOverrides, forcedInoperable, extraClose, excludedClose,
                request.getAdjustNote()));
        plan = planRepository.save(plan);

        // 重写隔离步骤
        replaceSteps(plan, result);

        return toPlanVO(plan, result);
    }

    /** 激活方案：只有证明通过且非拒绝启用才能进入待执行 */
    @Transactional
    public IsolationPlanVO activate(Long planId) {
        IsolationPlan plan = requirePlan(planId);
        if (!"试算中".equals(plan.getPhase())) {
            throw new BizException("只有试算中的方案可以激活启用，当前状态：" + plan.getPhase());
        }
        if (!Boolean.TRUE.equals(plan.getIsolatedProven())) {
            throw new BizException("证明未通过：故障段尚未与运行管网隔开，方案不得启用；请扩大隔离或拒绝启用等待替代路径");
        }
        if ("拒绝启用".equals(plan.getStrategy())) {
            throw new BizException("该方案为「拒绝启用」，不允许下发执行");
        }
        // 激活前对照现场实况复验方案：不是重新求解（那总会成功），而是按方案「生成时的步骤记录」回放——
        // 计划动作阀的现场基线必须没变、「原本关闭」只核验阀必须确实仍关闭；
        // 再把各阀计划动作后状态叠到实况上做一次拓扑证明，任一不满足都必须先重新试算。
        String activationError = validatePlanAgainstLive(plan);
        if (activationError != null) {
            plan.setIsolatedProven(false);
            plan.setProofNote("激活前复验失败：" + activationError);
            plan.setTrialNote(truncate((plan.getTrialNote() == null ? "" : plan.getTrialNote())
                    + "；激活前复验未通过，需重新试算", 1000));
            planRepository.save(plan);
            throw new BizException("激活前复验失败：" + activationError + "，请重新试算后再激活");
        }
        plan.setPhase("待执行");
        plan.setActivatedAt(LocalDateTime.now());
        planRepository.save(plan);
        return getPlan(planId);
    }

    // ==================================================================
    // 现场执行
    // ==================================================================

    /** 开始执行：待执行 → 执行中 */
    @Transactional
    public IsolationPlanVO startExecution(Long planId) {
        IsolationPlan plan = requirePlan(planId);
        if ("已暂停".equals(plan.getPhase())) {
            plan.setPhase("执行中");
            planRepository.save(plan);
            return getPlan(planId);
        }
        if (!"待执行".equals(plan.getPhase())) {
            throw new BizException("只有待执行方案可以开始现场执行，当前状态：" + plan.getPhase());
        }
        plan.setPhase("执行中");
        planRepository.save(plan);
        return getPlan(planId);
    }

    /** 现场逐步确认 */
    @Transactional
    public IsolationPlanVO confirmStep(Long planId, Long stepId, IsolationConfirmRequest request) {
        IsolationPlan plan = requirePlan(planId);
        if (!Arrays.asList("执行中", "已暂停").contains(plan.getPhase())) {
            throw new BizException("方案当前为「" + plan.getPhase() + "」，不能确认阀门步骤");
        }
        IsolationStep step = stepRepository.findById(stepId)
                .orElseThrow(() -> new BizException("阀门步骤不存在：id=" + stepId));
        if (!step.getPlan().getId().equals(planId)) {
            throw new BizException("该步骤不属于当前隔离方案");
        }
        if (!Dict.STAGE_ISOLATE.equals(step.getStage())) {
            throw new BizException("隔离阶段只确认「隔离」步骤");
        }
        if ("已完成".equals(step.getStepStatus())) {
            throw new BizException("步骤 " + step.getStepNo() + "（" + step.getWellNo() + "）已完成，不能重复确认");
        }
        if (request.getActualState() == null || !Dict.isValidOpenState(request.getActualState())) {
            throw new BizException("请选择现场回报的实际阀态：全开 / 半开 / 关闭");
        }
        // 前序未完成校验
        List<IsolationStep> isolateSteps = stepRepository.findByPlanAndStageOrderByStepNoAsc(plan, Dict.STAGE_ISOLATE);
        for (IsolationStep other : isolateSteps) {
            if (other.getStepNo() < step.getStepNo()
                    && !Arrays.asList("已完成", "跳过").contains(other.getStepStatus())) {
                throw new BizException("前序步骤 " + other.getStepNo() + "（" + other.getWellNo()
                        + "）尚未完成，后续步骤暂停，不能先操作本阀");
            }
        }

        ValveWell live = valveWellRepository.findByWellNo(step.getWellNo())
                .orElseThrow(() -> new BizException("阀门井台账缺失：" + step.getWellNo()));
        boolean changedByOther = !Objects.equals(live.getOpenState(), step.getFromState());
        boolean matches = Boolean.TRUE.equals(request.getMatchPlan())
                && Objects.equals(request.getActualState(), step.getPlannedState())
                && !changedByOther;

        step.setActualState(request.getActualState());
        step.setConfirmedBy(request.getConfirmedBy());
        step.setConfirmedAt(LocalDateTime.now());

        if (matches) {
            // 同步阀门实况为计划态
            live.setOpenState(step.getPlannedState());
            valveWellRepository.save(live);
            // 「原本关闭」类步骤确认后记为跳过（不操作只核验），其余记已完成
            step.setStepStatus(Dict.KIND_ALREADY_CLOSED.equals(step.getActionKind()) ? "跳过" : "已完成");
            step.setDeviationReason(null);
            step.setNote((step.getNote() == null ? "" : step.getNote()));
        } else {
            List<String> reasons = new ArrayList<>();
            if (changedByOther) {
                reasons.add("同一阀门已被他人改动：台账实况「" + live.getOpenState() + "」≠ 方案基线「"
                        + step.getFromState() + "」");
            }
            if (!Objects.equals(request.getActualState(), step.getPlannedState())) {
                reasons.add("现场反馈与计划不符：回报「" + request.getActualState() + "」≠ 计划「"
                        + step.getPlannedState() + "」");
            }
            if (request.getDeviationReason() != null && !request.getDeviationReason().isBlank()) {
                reasons.add(request.getDeviationReason());
            }
            step.setStepStatus("偏差");
            step.setDeviationReason(truncate(String.join("；", reasons), 250));
            plan.setPhase("已暂停");
            appendFinishNote(plan, "步骤 " + step.getStepNo() + "（" + step.getWellNo() + "）暂停："
                    + step.getDeviationReason());
        }
        stepRepository.save(step);

        // 全部隔离步骤完成且证明仍通过 → 已隔离（按台账实况复证）
        if ("执行中".equals(plan.getPhase()) && allIsolateDone(plan)) {
            TrialResult proof = reEvaluate(plan, false);
            plan.setIsolatedProven(proof.proven);
            plan.setProofNote(proof.proofNote);
            refreshImpactSnapshot(plan, proof);
            if (proof.proven) {
                plan.setPhase("已隔离");
                plan.setIsolatedAt(LocalDateTime.now());
                appendFinishNote(plan, "关断序列全部确认完成，复证通过：故障段已与运行管网隔开");
            } else {
                plan.setPhase("已失败");
                appendFinishNote(plan, "关断完成但复证失败：" + proof.proofNote);
            }
        }
        planRepository.save(plan);
        return getPlan(planId);
    }

    /** 中途失败收口：扩大隔离 / 按安全顺序恢复已动阀门 / 保持现场等待处置 */
    @Transactional
    public IsolationPlanVO finishFailure(Long planId, IsolationFinishRequest request) {
        IsolationPlan plan = requirePlan(planId);
        if (!Arrays.asList("已暂停", "已失败").contains(plan.getPhase())) {
            throw new BizException("只有已暂停或已失败的方案需要失败收口，当前状态：" + plan.getPhase());
        }
        String disposition = request.getDisposition();
        if (Arrays.asList("EXPAND", "REVERT", "HOLD").contains(disposition) == false) {
            throw new BizException("处置方式只能是：EXPAND（扩大隔离）/ REVERT（恢复已动阀门）/ HOLD（保持现场等待处置）");
        }
        if ("EXPAND".equals(disposition)) {
            // 在原方案基础上带扩大策略重新试算：复用方案里保存的现场阀态覆盖/人工调整，避免丢失。
            IsolationTrialRequest retrial = request.getRetrial() != null ? request.getRetrial()
                    : new IsolationTrialRequest();
            retrial.setPlanId(planId);
            retrial.setStrategy("扩大隔离");
            if (retrial.getFaultReason() == null) {
                retrial.setFaultReason(plan.getFaultReason());
            }
            if (retrial.getValveOverrides() == null || retrial.getValveOverrides().isEmpty()) {
                hydrateFromStoredOptions(plan, retrial);
            }
            appendFinishNote(plan, "处置：扩大隔离，重新试算"
                    + (request.getNote() == null ? "" : "（" + request.getNote() + "）"));
            plan.setPhase("试算中");
            planRepository.save(plan);
            planRepository.flush();
            return trial(retrial);
        }
        if ("REVERT".equals(disposition)) {
            // 按安全顺序恢复已动阀门：只回「已完成」的计划关闭/必须开启，反向操作；偏差阀不动
            List<IsolationStep> done = stepRepository.findByPlanAndStageOrderByStepNoAsc(plan, Dict.STAGE_ISOLATE)
                    .stream()
                    .filter(s -> "已完成".equals(s.getStepStatus()))
                    .collect(Collectors.toList());
            Collections.reverse(done);
            List<String> reverted = new ArrayList<>();
            for (IsolationStep step : done) {
                ValveWell well = valveWellRepository.findByWellNo(step.getWellNo()).orElse(null);
                if (well == null || step.getFromState() == null) {
                    continue;
                }
                well.setOpenState(step.getFromState());
                valveWellRepository.save(well);
                step.setStepStatus("跳过");
                String priorNote = step.getNote() == null ? "" : step.getNote() + "；";
                step.setNote(truncate(priorNote
                        + "收口恢复：已按安全顺序恢复到操作前状态「" + step.getFromState() + "」", 250));
                stepRepository.save(step);
                reverted.add(step.getWellNo());
            }
            plan.setPhase("已废止");
            plan.setStrategy("拒绝启用");
            plan.setIsolatedProven(false);
            plan.setFinishedAt(LocalDateTime.now());
            appendFinishNote(plan, "处置：按安全顺序恢复已动阀门 " + String.join("、", reverted)
                    + "（偏差阀保持现场未动），方案废止"
                    + (request.getNote() == null ? "" : "；" + request.getNote()));
            planRepository.save(plan);
            return getPlan(planId);
        }
        // HOLD
        plan.setPhase("已失败");
        plan.setFinishedAt(plan.getFinishedAt() == null ? LocalDateTime.now() : plan.getFinishedAt());
        appendFinishNote(plan, "处置：保持现场状态等待抢修/调度处置，后续步骤继续暂停"
                + (request.getNote() == null ? "" : "；" + request.getNote()));
        planRepository.save(plan);
        return getPlan(planId);
    }

    // ==================================================================
    // 复供
    // ==================================================================

    /**
     * 故障结束后的复供：按当前阀门实况重新生成顺序。
     * 现场另行调整过的阀以台账实况为准，不被旧方案覆盖；恢复后仍不可达的换热站留在异常清单。
     */
    @Transactional
    public IsolationPlanVO planRestore(Long planId) {
        IsolationPlan plan = requirePlan(planId);
        if (!"已隔离".equals(plan.getPhase())) {
            throw new BizException("只有已隔离（故障段已安全隔开）的方案可以编排复供，当前状态：" + plan.getPhase());
        }
        // 清掉旧复供步骤，按当前实况重排（现场另行调整的阀不被旧方案覆盖）
        stepRepository.deleteByPlanAndStage(plan, Dict.STAGE_RESTORE);
        stepRepository.flush();
        List<IsolationStep> isolateSteps = stepRepository.findByPlanAndStageOrderByStepNoAsc(plan, Dict.STAGE_ISOLATE);

        // 当前实况 → 目标：把「本次方案实际关上的阀」按撤离安全规则反向打开，基线原本关闭的阀保持关闭
        List<IsolationStep> restoreSteps = new ArrayList<>();
        int no = 1;
        // 安全门最先开：先恢复撤离路径
        List<IsolationStep> completed = isolateSteps.stream()
                .filter(s -> "已完成".equals(s.getStepStatus()))
                .collect(Collectors.toList());
        completed.sort((a, b) -> {
            boolean ag = Boolean.TRUE.equals(a.getEgressGuard());
            boolean bg = Boolean.TRUE.equals(b.getEgressGuard());
            if (ag != bg) {
                return ag ? -1 : 1;
            }
            return b.getStepNo() - a.getStepNo();
        });
        for (IsolationStep origin : completed) {
            IsolationStep step = new IsolationStep();
            step.setPlan(plan);
            step.setStepNo(no++);
            step.setStage(Dict.STAGE_RESTORE);
            step.setValveWell(origin.getValveWell());
            step.setWellNo(origin.getWellNo());
            // 目标态：恢复到方案生成时的基线（fromState）；若现场已被他人另行调整，以实况为起，目标仍取基线
            step.setFromState(valveWellRepository.findByWellNo(origin.getWellNo())
                    .map(ValveWell::getOpenState).orElse(origin.getPlannedState()));
            step.setPlannedState(origin.getFromState() == null ? "全开" : origin.getFromState());
            step.setActionKind(Dict.KIND_PLAN_OPEN);
            step.setEgressGuard(Boolean.TRUE.equals(origin.getEgressGuard()));
            step.setStepStatus("待执行");
            if (Boolean.TRUE.equals(origin.getEgressGuard())) {
                step.setNote("人员撤离安全门：复供序列最先开启");
            } else if (Dict.KIND_MUST_OPEN.equals(origin.getActionKind())) {
                step.setNote("保供路径必须开启阀恢复到试算前状态");
            } else {
                step.setNote("按关断顺序逆向恢复");
            }
            restoreSteps.add(step);
        }
        // 现场另行调整的阀（实况与方案基线不一致、且不在已完成步骤里）不生成动作，只在备注中声明
        List<String> thirdParty = new ArrayList<>();
        Set<String> handled = restoreSteps.stream().map(IsolationStep::getWellNo).collect(Collectors.toSet());
        for (String wellNo : split(plan.getBlockedWellNos())) {
            if (handled.contains(wellNo)) {
                continue;
            }
            ValveWell well = valveWellRepository.findByWellNo(wellNo).orElse(null);
            if (well != null) {
                thirdParty.add(wellNo + "（实况 " + well.getOpenState() + "，保留现场调整不覆盖）");
            }
        }
        stepRepository.saveAll(restoreSteps);

        // 全部复供步骤完成前先做一次预演证明：故障段恢复在役后，仍不可达的换热站进异常清单
        RestoreProof proof = evaluateRestore(plan);
        plan.setRestoredProven(proof.unreachableStations.isEmpty());
        appendFinishNote(plan, "复供顺序已按当前阀门实况重新生成（" + restoreSteps.size()
                + " 步，安全门最先）；现场另行调整阀："
                + (thirdParty.isEmpty() ? "无" : String.join("、", thirdParty))
                + "；复供后仍不可达换热站："
                + (proof.unreachableStations.isEmpty() ? "无" : String.join("、", proof.unreachableStations)));
        planRepository.save(plan);
        return getPlan(planId);
    }

    /** 确认复供步骤 */
    @Transactional
    public IsolationPlanVO confirmRestoreStep(Long planId, Long stepId, IsolationConfirmRequest request) {
        IsolationPlan plan = requirePlan(planId);
        if (!Arrays.asList("已隔离", "复供中", "已暂停").contains(plan.getPhase())) {
            throw new BizException("复供只在已隔离方案上进行，当前状态：" + plan.getPhase());
        }
        IsolationStep step = stepRepository.findById(stepId)
                .orElseThrow(() -> new BizException("阀门步骤不存在：id=" + stepId));
        if (!Dict.STAGE_RESTORE.equals(step.getStage()) || !step.getPlan().getId().equals(planId)) {
            throw new BizException("该步骤不是当前方案的复供步骤");
        }
        if (request.getActualState() == null || !Dict.isValidOpenState(request.getActualState())) {
            throw new BizException("请选择现场回报的实际阀态：全开 / 半开 / 关闭");
        }
        List<IsolationStep> restoreSteps = stepRepository.findByPlanAndStageOrderByStepNoAsc(plan, Dict.STAGE_RESTORE);
        for (IsolationStep other : restoreSteps) {
            if (other.getStepNo() < step.getStepNo() && !"已完成".equals(other.getStepStatus())) {
                throw new BizException("前序复供步骤 " + other.getStepNo() + "（" + other.getWellNo()
                        + "）尚未完成，后续步骤暂停");
            }
        }
        ValveWell live = valveWellRepository.findByWellNo(step.getWellNo())
                .orElseThrow(() -> new BizException("阀门井台账缺失：" + step.getWellNo()));
        boolean changedByOther = !Objects.equals(live.getOpenState(), step.getFromState());
        boolean matches = Boolean.TRUE.equals(request.getMatchPlan())
                && Objects.equals(request.getActualState(), step.getPlannedState())
                && !changedByOther;
        step.setActualState(request.getActualState());
        step.setConfirmedBy(request.getConfirmedBy());
        step.setConfirmedAt(LocalDateTime.now());
        if (matches) {
            live.setOpenState(step.getPlannedState());
            valveWellRepository.save(live);
            step.setStepStatus("已完成");
            plan.setPhase("复供中");
        } else {
            List<String> reasons = new ArrayList<>();
            if (changedByOther) {
                reasons.add("同一阀门已被他人改动：台账实况「" + live.getOpenState() + "」≠ 复供基线「"
                        + step.getFromState() + "」");
            }
            if (!Objects.equals(request.getActualState(), step.getPlannedState())) {
                reasons.add("现场反馈与计划不符：回报「" + request.getActualState() + "」≠ 计划「"
                        + step.getPlannedState() + "」");
            }
            if (request.getDeviationReason() != null && !request.getDeviationReason().isBlank()) {
                reasons.add(request.getDeviationReason());
            }
            step.setStepStatus("偏差");
            step.setDeviationReason(truncate(String.join("；", reasons), 250));
            plan.setPhase("已暂停");
            appendFinishNote(plan, "复供步骤 " + step.getStepNo() + "（" + step.getWellNo() + "）暂停："
                    + step.getDeviationReason());
        }
        stepRepository.save(step);

        if ("复供中".equals(plan.getPhase()) && restoreSteps.stream().allMatch(s -> "已完成".equals(s.getStepStatus()))) {
            RestoreProof proof = evaluateRestore(plan);
            plan.setRestoredProven(proof.unreachableStations.isEmpty());
            plan.setPhase("已复供");
            plan.setFinishedAt(LocalDateTime.now());
            appendFinishNote(plan, "复供完成；仍不可达换热站："
                    + (proof.unreachableStations.isEmpty() ? "无，全部在役站恢复供热"
                    : String.join("、", proof.unreachableStations) + "，留在异常清单待处置"));
        }
        planRepository.save(plan);
        return getPlan(planId);
    }

    // ==================================================================
    // 查询
    // ==================================================================

    @Transactional(readOnly = true)
    public List<IsolationPlanVO> listPlans(String active) {
        List<Long> ids;
        if ("active".equals(active)) {
            ids = planRepository.findIdsByPhaseInOrderByIdDesc(
                    Arrays.asList("试算中", "待执行", "执行中", "已隔离", "已暂停", "已失败", "复供中"));
        } else {
            ids = planRepository.findIdsOrderByIdDesc();
        }
        List<IsolationPlanVO> result = new ArrayList<>();
        for (Long id : ids) {
            result.add(toPlanVO(requirePlan(id), null));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public IsolationPlanVO getPlan(Long planId) {
        return toPlanVO(requirePlan(planId), null);
    }

    /** 方案列表之外给选择故障管段页用的简要拓扑视图 */
    @Transactional(readOnly = true)
    public Map<String, Object> topologySnapshot() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("segments", segmentRepository.findAll().stream()
                .map(s -> Map.of("id", s.getId(), "segCode", s.getSegCode(),
                        "startWellNo", nz(s.getStartWellNo()), "endWellNo", nz(s.getEndWellNo()),
                        "segmentStatus", nz(s.getSegmentStatus())))
                .collect(Collectors.toList()));
        data.put("wells", valveWellRepository.findAll().stream()
                .map(w -> Map.of("id", w.getId(), "wellNo", w.getWellNo(),
                        "openState", nz(w.getOpenState()), "repairStatus", nz(w.getRepairStatus()),
                        "inoperableFlag", Boolean.TRUE.equals(w.getInoperableFlag()),
                        "egressGuard", Boolean.TRUE.equals(w.getEgressGuard())))
                .collect(Collectors.toList()));
        return data;
    }

    /** 把方案保存的现场阀态覆盖 / 人工追加关阀 / 拒绝关阀还原成试算请求 */
    private void hydrateFromStoredOptions(IsolationPlan plan, IsolationTrialRequest retrial) {
        Options options = readOptions(plan.getNetworkOptionsJson());
        List<IsolationTrialRequest.ValveOverride> overrides = new ArrayList<>();
        for (Map.Entry<String, String> entry : options.stateOverrides.entrySet()) {
            IsolationTrialRequest.ValveOverride override = new IsolationTrialRequest.ValveOverride();
            override.setWellNo(entry.getKey());
            override.setOpenState(entry.getValue());
            overrides.add(override);
        }
        for (String wellNo : options.inoperable) {
            IsolationTrialRequest.ValveOverride override = overrides.stream()
                    .filter(o -> wellNo.equals(o.getWellNo())).findFirst()
                    .orElseGet(() -> {
                        IsolationTrialRequest.ValveOverride created = new IsolationTrialRequest.ValveOverride();
                        created.setWellNo(wellNo);
                        overrides.add(created);
                        return created;
                    });
            override.setInoperable(true);
        }
        retrial.setValveOverrides(overrides);
        retrial.setExtraCloseWells(new ArrayList<>(options.extraClose));
        retrial.setExcludedCloseWells(new ArrayList<>(options.excludedClose));
    }

    // ==================================================================
    // 内部辅助
    // ==================================================================

    private IsolationPlan requirePlan(Long id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new BizException("隔离方案不存在：id=" + id));
    }

    private HeatGraph loadGraph(Map<String, String> stateOverrides) {
        return HeatGraph.build(segmentRepository.findAll(), valveWellRepository.findAll(),
                sourceRepository.findAll(), stationRepository.findAll(), stateOverrides, false);
    }

    private Map<Long, Integer> loadHouseholds() {
        Map<Long, Integer> map = new LinkedHashMap<>();
        for (Object[] row : residentRepository.countActiveGroupByStation()) {
            if (row[0] != null) {
                map.put(((Number) row[0]).longValue(), ((Number) row[1]).intValue());
            }
        }
        return map;
    }

    /**
     * 重新试算证明：
     * @param useOverrides true=沿用方案试算时的现场阀态覆盖（激活前复验，现场阀尚未被方案改动）；
     *                     false=完全按台账当前实况（执行中/关断完成后的复证，计划阀态已随确认写入台账）
     */
    private TrialResult reEvaluate(IsolationPlan plan, boolean useOverrides) {
        Options options = readOptions(plan.getNetworkOptionsJson());
        HeatGraph graph = loadGraph(useOverrides ? options.stateOverrides : Collections.emptyMap());
        Map<Long, Integer> households = loadHouseholds();
        return solver.solve(graph, plan.getFaultSegment(), options.inoperable,
                options.extraClose, options.excludedClose, households);
    }

    private void refreshImpactSnapshot(IsolationPlan plan, TrialResult result) {
        plan.setAffectedStationCodes(join(impactCodes(result, "停暖")));
        plan.setAffectedStationCount(result.affectedCount);
        plan.setAffectedAreaWanM2(result.affectedArea);
        plan.setAffectedHouseholds(result.affectedHouseholds);
        plan.setMaintainableStationCodes(join(new LinkedHashSet<>(result.maintainableStationCodes)));
    }

    private Set<String> impactCodes(TrialResult result, String wanted) {
        return result.impacts.stream()
                .filter(i -> wanted.equals(i.result))
                .map(i -> i.stationCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 复供证明：故障管段恢复在役 + 按当前阀态 BFS，列出仍不可达在役换热站 */
    private RestoreProof evaluateRestore(IsolationPlan plan) {
        RestoreProof proof = new RestoreProof();
        // 不复用 plan 覆盖，直接按台账当前实况建图（故障段也放回网内）
        HeatGraph graph = HeatGraph.build(segmentRepository.findAll(), valveWellRepository.findAll(),
                sourceRepository.findAll(), stationRepository.findAll(), Collections.emptyMap(), false);
        Set<String> reachable = graph.reachableFromSources(Collections.emptySet());
        for (HeatStation station : stationRepository.findAll()) {
            if (!"运行".equals(station.getStatus()) || station.getTapWellNo() == null) {
                continue;
            }
            if (!reachable.contains(station.getTapWellNo())) {
                proof.unreachableStations.add(station.getCode() + " " + station.getName()
                        + "（并网井 " + station.getTapWellNo() + "）");
            }
        }
        return proof;
    }

    private static class RestoreProof {
        final List<String> unreachableStations = new ArrayList<>();
    }

    private void replaceSteps(IsolationPlan plan, TrialResult result) {
        stepRepository.deleteByPlan(plan);
        stepRepository.flush();
        int no = 1;
        for (TrialResult.ValveAction action : result.actions) {
            ValveWell well = valveWellRepository.findByWellNo(action.wellNo).orElse(null);
            IsolationStep step = new IsolationStep();
            step.setPlan(plan);
            step.setStepNo(no++);
            step.setStage(Dict.STAGE_ISOLATE);
            step.setValveWell(well);
            step.setWellNo(action.wellNo);
            step.setActionKind(action.kind);
            step.setPlannedState(action.plannedState);
            step.setFromState(action.fromState);
            step.setEgressGuard(action.egressGuard);
            step.setStepStatus("待执行");
            step.setNote(action.note);
            stepRepository.save(step);
        }
    }

    private boolean allIsolateDone(IsolationPlan plan) {
        List<IsolationStep> steps = stepRepository.findByPlanAndStageOrderByStepNoAsc(plan, Dict.STAGE_ISOLATE);
        if (steps.isEmpty()) {
            return false;
        }
        return steps.stream().allMatch(s -> Arrays.asList("已完成", "跳过").contains(s.getStepStatus()));
    }

    private IsolationPlanVO toPlanVO(IsolationPlan plan, TrialResult reuse) {
        IsolationPlanVO vo = new IsolationPlanVO();
        vo.setId(plan.getId());
        vo.setPlanNo(plan.getPlanNo());
        PipeSegment fault = plan.getFaultSegment();
        vo.setFaultSegmentId(fault.getId());
        vo.setFaultSegmentCode(fault.getSegCode());
        vo.setFaultSegmentLabel(fault.getSegCode() + "（" + fault.getStartWellNo() + "→"
                + fault.getEndWellNo() + "）DN" + fault.getDiameterDn());
        vo.setFaultReason(plan.getFaultReason());
        vo.setPhase(plan.getPhase());
        vo.setStrategy(plan.getStrategy());
        vo.setIsolatedProven(plan.getIsolatedProven());
        vo.setProofNote(plan.getProofNote());
        vo.setBlockedWellNos(split(plan.getBlockedWellNos()));
        vo.setEnclosedSegmentCodes(split(plan.getEnclosedSegmentCodes()));
        vo.setForcedOpenWellNos(split(plan.getForcedOpenWellNos()));
        vo.setAffectedStationCodes(split(plan.getAffectedStationCodes()));
        vo.setAffectedStationCount(plan.getAffectedStationCount());
        vo.setAffectedAreaWanM2(plan.getAffectedAreaWanM2());
        vo.setAffectedHouseholds(plan.getAffectedHouseholds());
        vo.setMaintainableStationCodes(split(plan.getMaintainableStationCodes()));
        vo.setBlockedChoiceNote(plan.getBlockedChoiceNote());
        vo.setOptionExpand(plan.getOptionExpand());
        vo.setOptionReject(plan.getOptionReject());
        vo.setTrialNote(plan.getTrialNote());
        vo.setFinishNote(plan.getFinishNote());
        vo.setRestoredProven(plan.getRestoredProven());
        vo.setCreatedAt(plan.getCreatedAt());
        vo.setActivatedAt(plan.getActivatedAt());
        vo.setIsolatedAt(plan.getIsolatedAt());
        vo.setFinishedAt(plan.getFinishedAt());

        List<IsolationStep> steps = stepRepository.findByPlanOrderByStageAscStepNoAsc(plan);
        List<IsolationStepVO> stepVos = new ArrayList<>();
        Integer nextIsolate = null;
        Integer nextRestore = null;
        for (IsolationStep step : steps) {
            stepVos.add(toStepVO(step));
            if (Dict.STAGE_ISOLATE.equals(step.getStage()) && nextIsolate == null
                    && "待执行".equals(step.getStepStatus())) {
                nextIsolate = step.getStepNo();
            }
            if (Dict.STAGE_RESTORE.equals(step.getStage()) && nextRestore == null
                    && "待执行".equals(step.getStepStatus())) {
                nextRestore = step.getStepNo();
            }
        }
        vo.setSteps(stepVos);
        vo.setNextIsolateStepNo(nextIsolate);
        vo.setNextRestoreStepNo(nextRestore);

        // 影响清单：优先用本次试算结果，否则按方案快照从台账补全
        if (reuse != null) {
            vo.setImpacts(reuse.impacts.stream().map(this::toImpactVO).collect(Collectors.toList()));
            vo.setExpandOption(toChoiceVO(reuse.expandOption));
            vo.setRejectOption(toChoiceVO(reuse.rejectOption));
        } else {
            vo.setImpacts(loadImpactVos(plan));
        }

        // 相关阀门实况
        Set<String> related = new LinkedHashSet<>(vo.getBlockedWellNos());
        related.addAll(vo.getForcedOpenWellNos());
        for (IsolationStepVO step : stepVos) {
            related.add(step.getWellNo());
        }
        List<IsolationPlanVO.ValveLiveStateVO> live = new ArrayList<>();
        for (String wellNo : related) {
            valveWellRepository.findByWellNo(wellNo).ifPresent(w -> {
                IsolationPlanVO.ValveLiveStateVO l = new IsolationPlanVO.ValveLiveStateVO();
                l.setWellNo(w.getWellNo());
                l.setOpenState(w.getOpenState());
                l.setRepairStatus(w.getRepairStatus());
                l.setInoperableFlag(w.getInoperableFlag());
                l.setEgressGuard(w.getEgressGuard());
                live.add(l);
            });
        }
        vo.setLiveValves(live);
        return vo;
    }

    private IsolationStepVO toStepVO(IsolationStep step) {
        IsolationStepVO vo = new IsolationStepVO();
        vo.setId(step.getId());
        vo.setStepNo(step.getStepNo());
        vo.setStage(step.getStage());
        vo.setValveWellId(step.getValveWell() == null ? null : step.getValveWell().getId());
        vo.setWellNo(step.getWellNo());
        ValveWell well = step.getValveWell();
        if (well != null) {
            vo.setWellLocation(well.getLocation());
            vo.setValveCaliberDn(well.getValveCaliberDn());
            vo.setLiveState(well.getOpenState());
        }
        vo.setActionKind(step.getActionKind());
        vo.setPlannedState(step.getPlannedState());
        vo.setFromState(step.getFromState());
        vo.setEgressGuard(step.getEgressGuard());
        vo.setStepStatus(step.getStepStatus());
        vo.setActualState(step.getActualState());
        vo.setConfirmedBy(step.getConfirmedBy());
        vo.setConfirmedAt(step.getConfirmedAt());
        vo.setDeviationReason(step.getDeviationReason());
        vo.setNote(step.getNote());
        return vo;
    }

    private IsolationImpactVO toImpactVO(TrialResult.StationImpact impact) {
        IsolationImpactVO vo = new IsolationImpactVO();
        vo.setStationId(impact.stationId);
        vo.setStationCode(impact.stationCode);
        vo.setStationName(impact.stationName);
        vo.setTapWellNo(impact.tapWellNo);
        vo.setResult(impact.result);
        vo.setReason(impact.reason);
        vo.setAreaWanM2(impact.areaWanM2);
        vo.setHouseholds(impact.households);
        return vo;
    }

    /**
     * 按方案记录的隔离步骤对照阀门台账实况做激活前复验：
     * 1. 计划关闭 / 必须开启阀的「方案基线状态」必须与实况一致（他人改动即拦截）；
     * 2. 原本关闭（只核验）阀当前必须确实处于关闭；
     * 3. 把计划动作后状态叠到实况上，移除故障边后做 BFS 证明，故障段两端井必须都不可达热源。
     * @return null=复验通过；否则返回中文原因
     */
    private String validatePlanAgainstLive(IsolationPlan plan) {
        List<IsolationStep> steps = stepRepository.findByPlanAndStageOrderByStepNoAsc(plan, Dict.STAGE_ISOLATE);
        Map<String, String> simulated = new LinkedHashMap<>();
        for (IsolationStep step : steps) {
            ValveWell well = valveWellRepository.findByWellNo(step.getWellNo()).orElse(null);
            if (well == null) {
                return "方案步骤阀 " + step.getWellNo() + " 在台账中已缺失";
            }
            String live = well.getOpenState();
            if (Dict.KIND_ALREADY_CLOSED.equals(step.getActionKind())) {
                if (!Dict.isValveClosed(live)) {
                    return "只核验阀 " + step.getWellNo() + " 现场已被改为「" + live + "」，原「原本关闭」边界失效";
                }
            } else if (!Objects.equals(step.getFromState(), live)) {
                return step.getWellNo() + " 已被他人改动：现场实况「" + live
                        + "」≠ 方案基线「" + step.getFromState() + "」";
            }
            simulated.put(step.getWellNo(), step.getPlannedState());
        }
        HeatGraph proofGraph = HeatGraph.build(segmentRepository.findAll(), valveWellRepository.findAll(),
                sourceRepository.findAll(), stationRepository.findAll(), simulated, false)
                .rebuildWithoutFault(plan.getFaultSegment().getId());
        Set<String> closedAfterActions = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : simulated.entrySet()) {
            if (Dict.isValveClosed(entry.getValue())) {
                closedAfterActions.add(entry.getKey());
            }
        }
        Set<String> reach = proofGraph.reachableFromSources(closedAfterActions);
        PipeSegment fault = plan.getFaultSegment();
        if (reach.contains(fault.getStartWellNo()) || reach.contains(fault.getEndWellNo())) {
            return "按方案步骤模拟关断后，故障段端井仍可达运行热源，故障段未与运行管网隔开";
        }
        return null;
    }

    /** 已持久化方案重新计算在当前阀态下的影响（保证方案状态、阀门实况、影响清单一致） */
    private List<IsolationImpactVO> loadImpactVos(IsolationPlan plan) {
        Map<Long, Integer> households = loadHouseholds();
        // 已隔离/复供中按台账实况；试算/待执行按「执行计划后的模拟态」
        boolean planApplied = Arrays.asList("执行中", "已隔离", "复供中", "已复供", "已暂停", "已失败")
                .contains(plan.getPhase());
        Map<String, String> overrides = new LinkedHashMap<>();
        Set<String> closed = new LinkedHashSet<>();
        if (planApplied) {
            // 直接按实况建图。复供阶段所有当前不可达的在役换热站都列入异常（停暖）清单，
            // 不能用方案快照的「本次新增停暖」口径，否则会把他人现场改阀造成的停暖漏掉。
            HeatGraph graph = HeatGraph.build(segmentRepository.findAll(), valveWellRepository.findAll(),
                    sourceRepository.findAll(), stationRepository.findAll(), overrides, false);
            Set<String> reach = graph.reachableFromSources(closed);
            boolean restorePhase = Arrays.asList("复供中", "已复供").contains(plan.getPhase());
            return impactsFromReach(reach, households, plan, restorePhase);
        }
        Options options = readOptions(plan.getNetworkOptionsJson());
        HeatGraph graph = loadGraph(options.stateOverrides);
        TrialResult result = solver.solve(graph, plan.getFaultSegment(), options.inoperable,
                options.extraClose, options.excludedClose, households);
        return result.impacts.stream().map(this::toImpactVO).collect(Collectors.toList());
    }

    private List<IsolationImpactVO> impactsFromReach(Set<String> reach, Map<Long, Integer> households,
                                                     IsolationPlan plan, boolean restorePhase) {
        Set<String> affected = new LinkedHashSet<>(split(plan.getAffectedStationCodes()));
        List<IsolationImpactVO> list = new ArrayList<>();
        for (HeatStation station : stationRepository.findAll()) {
            if (!"运行".equals(station.getStatus())) {
                continue;
            }
            IsolationImpactVO vo = new IsolationImpactVO();
            vo.setStationId(station.getId());
            vo.setStationCode(station.getCode());
            vo.setStationName(station.getName());
            vo.setTapWellNo(station.getTapWellNo());
            vo.setAreaWanM2(station.getAreaWanM2() == null ? BigDecimal.ZERO : station.getAreaWanM2());
            vo.setHouseholds(households.getOrDefault(station.getId(), 0));
            boolean warm = station.getTapWellNo() != null && reach.contains(station.getTapWellNo());
            String result;
            if (warm) {
                result = "保供";
            } else if (restorePhase) {
                // 复供阶段：当前实况不可达的在役站一律留在异常（停暖）清单，
                // 即使不是本次方案原本新增的停暖（现场他人改阀导致的也不能漏）。
                result = "停暖";
            } else {
                // 隔离阶段：保留「停暖 / 原本停暖」区分，避免把试算前已停的站误算到本次故障后果上。
                result = affected.contains(station.getCode()) ? "停暖" : "原本停暖";
            }
            vo.setResult(result);
            vo.setReason(warm ? "当前并网井仍可达运行热源"
                    : "当前并网井 " + station.getTapWellNo() + " 不可达任一运行热源");
            list.add(vo);
        }
        list.sort((a, b) -> a.getStationCode().compareTo(b.getStationCode()));
        return list;
    }

    private IsolationPlanVO.IsolationChoiceVO toChoiceVO(TrialResult.ChoiceOption option) {
        if (option == null) {
            return null;
        }
        IsolationPlanVO.IsolationChoiceVO vo = new IsolationPlanVO.IsolationChoiceVO();
        vo.setKey(option.key);
        vo.setTitle(option.title);
        vo.setConsequence(option.consequence);
        vo.setExtraBlockedWells(option.extraBlockedWells);
        vo.setExtraAffectedStationCodes(option.extraAffectedStationCodes);
        vo.setMaintainableStationCodes(option.maintainableStationCodes);
        return vo;
    }

    private void appendFinishNote(IsolationPlan plan, String text) {
        String old = plan.getFinishNote() == null ? "" : plan.getFinishNote();
        String merged = old + (old.isEmpty() ? "" : "；") + LocalDateTime.now().toLocalDate() + " " + text;
        plan.setFinishNote(truncate(merged, 1000));
    }

    private String generatePlanNo() {
        String prefix = "ISO-" + LocalDate.now().format(PLAN_NO_FMT) + "-";
        long count = planRepository.count() + 1;
        return prefix + String.format("%03d", count);
    }

    private Set<String> toWellSet(List<String> wells) {
        Set<String> set = new LinkedHashSet<>();
        if (wells != null) {
            for (String well : wells) {
                if (well != null && !well.isBlank()) {
                    set.add(well.trim());
                }
            }
        }
        return set;
    }

    private String join(Set<String> values) {
        return values == null || values.isEmpty() ? null : String.join(",", values);
    }

    private List<String> split(String csv) {
        if (csv == null || csv.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(csv.split("\\s*,\\s*")));
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String nz(String value) {
        return value == null ? "" : value;
    }

    private String writeOptions(Map<String, String> stateOverrides, Set<String> inoperable,
                                Set<String> extraClose, Set<String> excludedClose, String note) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stateOverrides", stateOverrides);
        payload.put("inoperable", inoperable);
        payload.put("extraClose", extraClose);
        payload.put("excludedClose", excludedClose);
        payload.put("note", note);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new BizException("试算参数序列化失败：" + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Options readOptions(String json) {
        Options options = new Options();
        if (json == null || json.isBlank()) {
            return options;
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(json, Map.class);
            Object over = payload.get("stateOverrides");
            if (over instanceof Map) {
                ((Map<String, Object>) over).forEach((k, v) -> options.stateOverrides.put(k, String.valueOf(v)));
            }
            readList(payload.get("inoperable"), options.inoperable);
            readList(payload.get("extraClose"), options.extraClose);
            readList(payload.get("excludedClose"), options.excludedClose);
        } catch (JsonProcessingException e) {
            throw new BizException("试算参数解析失败：" + e.getMessage());
        }
        return options;
    }

    @SuppressWarnings("unchecked")
    private void readList(Object value, Set<String> target) {
        if (value instanceof List) {
            for (Object item : (List<Object>) value) {
                if (item != null) {
                    target.add(String.valueOf(item));
                }
            }
        }
    }

    private static class Options {
        final Map<String, String> stateOverrides = new LinkedHashMap<>();
        final Set<String> inoperable = new LinkedHashSet<>();
        final Set<String> extraClose = new LinkedHashSet<>();
        final Set<String> excludedClose = new LinkedHashSet<>();
    }
}
