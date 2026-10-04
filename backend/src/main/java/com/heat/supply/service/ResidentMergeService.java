package com.heat.supply.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heat.supply.common.BizException;
import com.heat.supply.common.Dict;
import com.heat.supply.domain.HeatStation;
import com.heat.supply.domain.HeatingFee;
import com.heat.supply.domain.RepairOrder;
import com.heat.supply.domain.Resident;
import com.heat.supply.domain.ResidentMergeLog;
import com.heat.supply.domain.RoomTempSample;
import com.heat.supply.dto.MergeFeeYearPlan;
import com.heat.supply.dto.MergeLogVO;
import com.heat.supply.dto.MergePrecheckVO;
import com.heat.supply.dto.MergeResultVO;
import com.heat.supply.dto.MergeSideVO;
import com.heat.supply.dto.ResidentMergeRequest;
import com.heat.supply.dto.ResidentVO;
import com.heat.supply.dto.ResolveTargetVO;
import com.heat.supply.repository.HeatingFeeRepository;
import com.heat.supply.repository.RepairOrderRepository;
import com.heat.supply.repository.ResidentMergeLogRepository;
import com.heat.supply.repository.ResidentRepository;
import com.heat.supply.repository.RoomTempSampleRepository;

/**
 * 重复热用户档案归并。
 *
 * 预检硬规则：
 * - 地址四要素（小区/楼栋/单元/房号）必须完全一致，且由工作人员显式确认指向同一住房；
 * - 热表号不同必须停止归并；所属换热站不同必须停止归并；
 * - 供热状态不一致时，必须由工作人员明确采用「正常供热」或「已报停」，并展示对抽测/缴费的影响。
 *
 * 同收费年度两边都有台账时不得简单相加：
 * - 两边都未发生缴费：按保留档案计费面积重算保留侧一条，删除待归并侧重算前旧台账（金额快照进审计）；
 * - 任一方已有实收：两边原始金额一律不动，全部改挂保留档案并标「待财务复核」，复核前禁止登记该年度缴费。
 *
 * 确认归并：室温抽测 / 报修记录 / 收费记录全部改挂保留档案，业务编号、时间、处理结果不变；
 * 待归并档案置只读并指向保留档案。引用迁移、冲突处理、审计任一失败整次回退。
 * 已成功归并不允许重复搬移数据或重算金额（幂等返回原结果）。
 */
@Service
public class ResidentMergeService {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ResidentRepository residentRepository;
    private final RoomTempSampleRepository sampleRepository;
    private final RepairOrderRepository orderRepository;
    private final HeatingFeeRepository feeRepository;
    private final ResidentMergeLogRepository mergeLogRepository;
    private final MergeFailureRecorder failureRecorder;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ResidentMergeService(ResidentRepository residentRepository,
                                RoomTempSampleRepository sampleRepository,
                                RepairOrderRepository orderRepository,
                                HeatingFeeRepository feeRepository,
                                ResidentMergeLogRepository mergeLogRepository,
                                MergeFailureRecorder failureRecorder,
                                StringRedisTemplate redisTemplate,
                                ObjectMapper objectMapper) {
        this.residentRepository = residentRepository;
        this.sampleRepository = sampleRepository;
        this.orderRepository = orderRepository;
        this.feeRepository = feeRepository;
        this.mergeLogRepository = mergeLogRepository;
        this.failureRecorder = failureRecorder;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // ================================================================
    // 预检
    // ================================================================

    @Transactional(readOnly = true)
    public MergePrecheckVO precheck(Long keptId, Long awayId) {
        Resident kept = loadActive(keptId, "保留档案");
        Resident away = loadActive(awayId, "待归并档案");
        if (keptId.equals(awayId)) {
            throw new BizException("保留档案与待归并档案不能是同一档案");
        }

        MergePrecheckVO vo = new MergePrecheckVO();
        vo.setKept(buildSide(kept));
        vo.setAway(buildSide(away));

        boolean addressSame = eq(kept.getCommunity(), away.getCommunity())
                && eq(kept.getBuildingNo(), away.getBuildingNo())
                && eq(kept.getUnitNo(), away.getUnitNo())
                && eq(kept.getRoomNo(), away.getRoomNo());
        boolean meterSame = eq(kept.getMeterNo(), away.getMeterNo())
                && kept.getMeterNo() != null && !kept.getMeterNo().isBlank();
        boolean stationSame = kept.getStation() != null && away.getStation() != null
                && kept.getStation().getId().equals(away.getStation().getId());
        boolean supplyConflict = !eq(kept.getSupplyStatus(), away.getSupplyStatus());
        vo.setAddressSame(addressSame);
        vo.setMeterSame(meterSame);
        vo.setStationSame(stationSame);
        vo.setSupplyConflict(supplyConflict);
        vo.setKeptSampleCount(vo.getKept().getSamples().size());
        vo.setAwaySampleCount(vo.getAway().getSamples().size());
        vo.setKeptOrderCount(vo.getKept().getOrders().size());
        vo.setAwayOrderCount(vo.getAway().getOrders().size());

        List<String> blockers = new ArrayList<>();
        if (!addressSame) {
            blockers.add("两边地址不一致（" + addressText(kept) + " ≠ " + addressText(away) + "），不是同一住房，禁止归并");
        }
        if (!meterSame) {
            blockers.add("热表号不一致（保留档案 " + dash(kept.getMeterNo()) + " / 待归并档案 "
                    + dash(away.getMeterNo()) + "），必须停止归并，请先核实热表归属");
        }
        if (!stationSame) {
            blockers.add("所属换热站不一致（保留档案 " + stationCode(kept) + " / 待归并档案 "
                    + stationCode(away) + "），必须停止归并");
        }
        vo.setBlockers(blockers);

        List<String> notices = new ArrayList<>();
        notices.add("请确认两边地址确实指向同一住房后，勾选地址确认再执行归并");
        if (supplyConflict) {
            notices.add("供热状态不一致：保留档案「" + dash(kept.getSupplyStatus())
                    + "」，待归并档案「" + dash(away.getSupplyStatus()) + "」，归并时必须明确采用哪一种");
        }
        BigDecimal keptArrears = sumArrears(keptId);
        BigDecimal awayArrears = sumArrears(awayId);
        if (keptArrears.add(awayArrears).compareTo(Dict.ARREARS_EPSILON) >= 0) {
            notices.add("两户合计欠费 " + keptArrears.add(awayArrears).setScale(2, RoundingMode.HALF_UP)
                    + " 元；若选择「已报停」，按业务规则须先缴清欠费");
        }
        vo.setNotices(notices);

        vo.setFeePlans(buildFeePlans(kept, away));

        vo.setImpactActive("采用「正常供热」：归并后该户可继续录入室温抽测、按各年度台账登记缴费，"
                + "两边历史抽测与报修统一在保留档案下展示。");
        vo.setImpactSuspended("采用「已报停」：归并后禁止录入室温抽测、禁止登记缴费，须先恢复供热；"
                + "若保留档案尚有欠费，按业务规则不能采用该状态。");
        return vo;
    }

    // ================================================================
    // 确认归并（幂等 + 整事务回退）
    // ================================================================

    @Transactional
    public MergeResultVO confirmMerge(ResidentMergeRequest request) {
        Long keptId = request.getKeptResidentId();
        Long awayId = request.getAwayResidentId();
        String operator = request.getOperator();

        // 幂等：同一对档案已成功归并 → 直接回传原结果，不再搬移、不重算
        Optional<ResidentMergeLog> existed =
                mergeLogRepository.findFirstByKeptResidentIdAndAwayResidentIdAndStatus(
                        keptId, awayId, Dict.MERGE_STATUS_DONE);
        if (existed.isPresent()) {
            return buildIdempotentResult(existed.get());
        }

        String mergeNo = null;
        try {
            // 行锁锁定两份档案，挡住并发重复归并
            Resident kept = residentRepository.findByIdForUpdate(keptId)
                    .orElseThrow(() -> new BizException("保留档案不存在：id=" + keptId));
            Resident away = residentRepository.findByIdForUpdate(awayId)
                    .orElseThrow(() -> new BizException("待归并档案不存在：id=" + awayId));

            // 锁内二次幂等判断（并发请求可能在前一个事务提交后才拿到锁）
            Optional<ResidentMergeLog> again =
                    mergeLogRepository.findFirstByKeptResidentIdAndAwayResidentIdAndStatus(
                            keptId, awayId, Dict.MERGE_STATUS_DONE);
            if (again.isPresent()) {
                return buildIdempotentResult(again.get());
            }

            validate(kept, away, request);

            String chosenStatus = request.getChosenSupplyStatus();
            if (chosenStatus == null || chosenStatus.isBlank()) {
                chosenStatus = kept.getSupplyStatus();
            }

            ResidentMergeLog log = new ResidentMergeLog();
            // 用当天最大 id + 1 拼单号：失败审计也会占号，保证成功/失败记录单号全局唯一
            mergeNo = nextMergeNo();
            log.setMergeNo(mergeNo);
            log.setKeptResident(kept);
            log.setAwayResident(away);
            log.setKeptSnapshot(snapshot(kept));
            log.setAwaySnapshot(snapshot(away));
            log.setChosenSupplyStatus(chosenStatus);
            log.setOperator(operator);
            log.setStatus(Dict.MERGE_STATUS_DONE);
            log.setCreatedAt(LocalDateTime.now());
            // 先落审计主记录拿到单号/主键，收费处置行要回填 merge_log_id；最后再 flush 一次，审计失败同样整次回退
            mergeLogRepository.saveAndFlush(log);

            // 1) 收费记录按年度处置（先算清金额，再动引用）
            List<MergeResultVO.FeeYearResult> feeResults =
                    handleFees(kept, away, log);
            // 2) 室温抽测整体改挂：编号 / 时间 / 抽测结果一律不动
            int sampleCount = migrateSamples(kept, away);
            // 3) 报修记录整体改挂：工单号 / 报修时间 / 派工人 / 处理结果 / 状态不动
            int orderCount = migrateOrders(kept, away);
            // 4) 保留档案采用明确选择的供热状态
            kept.setSupplyStatus(chosenStatus);
            // 5) 待归并档案转只读并指向保留档案；热表号原样保留（数据库不再有唯一约束），
            //    按地址 / 热表号 / 旧档案编号查到归档档案时由落点接口跳到保留档案
            away.setArchived(true);
            away.setMergedInto(kept);
            away.setMergedAt(LocalDateTime.now());
            if (away.getLegacyCode() == null || away.getLegacyCode().isBlank()) {
                away.setLegacyCode("R" + away.getId());
            }
            residentRepository.save(kept);
            residentRepository.save(away);

            log.setSampleCount(sampleCount);
            log.setOrderCount(orderCount);
            log.setFeeCount(feeResults.stream().mapToInt(r -> r.getTouchedRows()).sum());
            log.setFeePlanJson(writePlanJson(feeResults));
            // 审计更新与引用迁移同一事务：写失败整次回退
            mergeLogRepository.saveAndFlush(log);

            MergeResultVO result = new MergeResultVO();
            result.setMergeNo(mergeNo);
            result.setIdempotent(false);
            result.setKeptResidentId(keptId);
            result.setAwayResidentId(awayId);
            result.setChosenSupplyStatus(chosenStatus);
            result.setSampleMigrated(sampleCount);
            result.setOrderMigrated(orderCount);
            result.setFeeHandled(log.getFeeCount());
            result.setFeeResults(feeResults);
            result.setCreatedAt(log.getCreatedAt().format(TIME_FMT));

            // 提交后清欠费缓存，避免旧归属/旧金额残留
            redisTemplate.delete(Dict.CACHE_ARREARS_RANK);
            redisTemplate.delete(Dict.CACHE_OVERVIEW);
            return result;
        } catch (BizException e) {
            // 业务校验失败本身就该回滚；同样留一条失败审计
            failureRecorder.recordFailure(mergeNo != null ? mergeNo : nextFallbackNo(), keptId, awayId,
                    e.getMessage(), operator);
            throw e;
        } catch (RuntimeException e) {
            // 引用迁移 / 冲突处理 / 审计写库失败：记录失败后整次回退，刷新后仍是归并前状态
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            failureRecorder.recordFailure(mergeNo != null ? mergeNo : nextFallbackNo(), keptId, awayId,
                    reason, operator);
            throw new BizException("归并失败，已整次回退，数据保持归并前状态：" + reason);
        }
    }

    // ================================================================
    // 查询落点：地址 / 热表号 / 旧档案编号 → 同一户
    // ================================================================

    @Transactional(readOnly = true)
    public List<ResidentVO> listCandidates(Long residentId) {
        Resident base = residentRepository.findById(residentId)
                .orElseThrow(() -> new BizException("热用户不存在：id=" + residentId));
        List<Resident> same = residentRepository.findSameAddressActive(residentId,
                nz(base.getCommunity()), nz(base.getBuildingNo()), nz(base.getUnitNo()), nz(base.getRoomNo()));
        List<ResidentVO> result = new ArrayList<>();
        for (Resident r : same) {
            result.add(toSimpleVo(r));
        }
        return result;
    }

    /** 按旧档案编号查询（R+id，兼容去前导零） */
    @Transactional(readOnly = true)
    public ResolveTargetVO resolveByLegacyCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BizException("请输入旧档案编号");
        }
        String key = code.trim();
        Resident target = residentRepository.findByLegacyCode(key).orElse(null);
        if (target == null && key.toUpperCase().matches("R\\d+")) {
            // 兼容 R001 / r17 等写法：去前导零后按数值 id 定位
            String numeric = key.substring(1);
            String normalized = "R" + Long.parseLong(numeric);
            target = residentRepository.findByLegacyCode(normalized).orElse(null);
            if (target == null) {
                target = residentRepository.findById(Long.parseLong(numeric)).orElse(null);
                if (target != null && target.getLegacyCode() != null
                        && !target.getLegacyCode().equals(normalized)) {
                    target = null;
                }
            }
        }
        if (target == null) {
            throw new BizException("未找到旧档案编号：" + key);
        }
        return toResolve(canonical(target), target.isArchived() ? key : null);
    }

    /** 按热表号查询：同号归档档案也命中，但一律跳到保留档案 */
    @Transactional(readOnly = true)
    public ResolveTargetVO resolveByMeterNo(String meterNo) {
        if (meterNo == null || meterNo.isBlank()) {
            throw new BizException("请输入热表号");
        }
        List<Resident> list = residentRepository.findByMeterNoOrderByArchivedAscIdAsc(meterNo.trim());
        if (list.isEmpty()) {
            throw new BizException("未找到热表号：" + meterNo.trim());
        }
        Resident target = canonical(list.get(0));
        return toResolve(target, target.getId().equals(list.get(0).getId()) ? null : meterNo.trim());
    }

    /** 按地址四要素查询：只能落到同一户（优先未归档保留档案） */
    @Transactional(readOnly = true)
    public ResolveTargetVO resolveByAddress(String community, String buildingNo, String unitNo, String roomNo) {
        if (isBlank(community) || isBlank(buildingNo) || isBlank(unitNo) || isBlank(roomNo)) {
            throw new BizException("请填写完整地址：小区 / 楼栋 / 单元 / 房号");
        }
        List<Resident> list = residentRepository.findByAddressAll(
                community.trim(), buildingNo.trim(), unitNo.trim(), roomNo.trim());
        if (list.isEmpty()) {
            throw new BizException("未找到该地址的热用户档案");
        }
        Resident first = list.get(0);
        Resident target = canonical(first);
        return toResolve(target, target.getId().equals(first.getId()) ? null
                : (first.getLegacyCode() == null ? "R" + first.getId() : first.getLegacyCode()));
    }

    // ================================================================
    // 归并审计
    // ================================================================

    @Transactional(readOnly = true)
    public List<MergeLogVO> listLogs() {
        List<MergeLogVO> result = new ArrayList<>();
        for (ResidentMergeLog log : mergeLogRepository.findAllByOrderByCreatedAtDescIdDesc()) {
            MergeLogVO vo = new MergeLogVO();
            vo.setId(log.getId());
            vo.setMergeNo(log.getMergeNo());
            vo.setKeptResidentId(log.getKeptResident() == null ? null : log.getKeptResident().getId());
            vo.setAwayResidentId(log.getAwayResident() == null ? null : log.getAwayResident().getId());
            vo.setKeptAddress(log.getKeptSnapshot());
            vo.setAwayAddress(log.getAwaySnapshot());
            vo.setChosenSupplyStatus(log.getChosenSupplyStatus());
            vo.setSampleCount(log.getSampleCount());
            vo.setOrderCount(log.getOrderCount());
            vo.setFeeCount(log.getFeeCount());
            vo.setStatus(log.getStatus());
            vo.setFailReason(log.getFailReason());
            vo.setOperator(log.getOperator());
            vo.setCreatedAt(log.getCreatedAt());
            result.add(vo);
        }
        return result;
    }

    // ================================================================
    // 内部：预检组装
    // ================================================================

    private MergeSideVO buildSide(Resident r) {
        MergeSideVO side = new MergeSideVO();
        side.setResidentId(r.getId());
        side.setLegacyCode(r.getLegacyCode() == null ? "R" + r.getId() : r.getLegacyCode());
        side.setCommunity(r.getCommunity());
        side.setBuildingNo(r.getBuildingNo());
        side.setUnitNo(r.getUnitNo());
        side.setRoomNo(r.getRoomNo());
        side.setAddressText(addressText(r));
        side.setAreaM2(r.getAreaM2());
        side.setMeterNo(r.getMeterNo());
        HeatStation st = r.getStation();
        side.setStationId(st == null ? null : st.getId());
        side.setStationCode(st == null ? null : st.getCode());
        side.setStationName(st == null ? null : st.getName());
        side.setSupplyStatus(r.getSupplyStatus());
        side.setContactPhone(r.getContactPhone());
        side.setTotalArrears(sumArrears(r.getId()));
        side.setArchived(r.isArchived());
        side.setMergedIntoId(r.getMergedInto() == null ? null : r.getMergedInto().getId());

        List<MergeSideVO.MergeSampleRow> samples = new ArrayList<>();
        for (RoomTempSample s : sampleRepository.findByResidentIdOrderByIdAsc(r.getId())) {
            MergeSideVO.MergeSampleRow row = new MergeSideVO.MergeSampleRow();
            row.setId(s.getId());
            row.setSampleDate(s.getSampleDate() == null ? null : s.getSampleDate().toString());
            row.setTemperature(s.getTemperature());
            row.setQualified(s.getQualified());
            row.setHouseholdName(s.getHouseholdName());
            row.setSurveyor(s.getSurveyor());
            samples.add(row);
        }
        side.setSamples(samples);

        List<MergeSideVO.MergeOrderRow> orders = new ArrayList<>();
        for (RepairOrder o : orderRepository.findByResidentIdOrderByIdAsc(r.getId())) {
            MergeSideVO.MergeOrderRow row = new MergeSideVO.MergeOrderRow();
            row.setId(o.getId());
            row.setOrderNo(o.getOrderNo());
            row.setReportedDate(o.getReportedDate() == null ? null : o.getReportedDate().toString());
            row.setFaultType(o.getFaultType());
            row.setAssignee(o.getAssignee());
            row.setHandleResult(o.getHandleResult());
            row.setOrderStatus(o.getOrderStatus());
            orders.add(row);
        }
        side.setOrders(orders);

        List<MergeSideVO.MergeFeeRow> fees = new ArrayList<>();
        for (HeatingFee f : feeRepository.findByResidentIdOrderByFeeYearDesc(r.getId())) {
            MergeSideVO.MergeFeeRow row = new MergeSideVO.MergeFeeRow();
            row.setId(f.getId());
            row.setFeeYear(f.getFeeYear());
            row.setBillingArea(f.getBillingArea());
            row.setUnitPrice(f.getUnitPrice());
            row.setReceivable(f.getReceivable());
            row.setPaid(f.getPaid());
            row.setArrears(f.getArrears());
            row.setPayStatus(f.getPayStatus());
            row.setLastPayDate(f.getLastPayDate() == null ? null : f.getLastPayDate().toString());
            row.setRemark(f.getRemark());
            row.setReviewRequired(f.isReviewRequired());
            row.setReviewed(f.isReviewed());
            fees.add(row);
        }
        side.setFees(fees);
        return side;
    }

    private List<MergeFeeYearPlan> buildFeePlans(Resident kept, Resident away) {
        Map<Integer, List<HeatingFee>> keptByYear = groupByYear(
                feeRepository.findByResidentIdOrderByFeeYearDesc(kept.getId()));
        Map<Integer, List<HeatingFee>> awayByYear = groupByYear(
                feeRepository.findByResidentIdOrderByFeeYearDesc(away.getId()));
        Map<Integer, MergeFeeYearPlan> plans = new LinkedHashMap<>();

        for (Integer year : awayByYear.keySet()) {
            plans.put(year, planFor(year, keptByYear.get(year), awayByYear.get(year), kept));
        }
        for (Integer year : keptByYear.keySet()) {
            plans.computeIfAbsent(year,
                    y -> planFor(y, keptByYear.get(y), awayByYear.get(y), kept));
        }
        return new ArrayList<>(plans.values());
    }

    private MergeFeeYearPlan planFor(Integer year, List<HeatingFee> keptRows,
                                     List<HeatingFee> awayRows, Resident kept) {
        MergeFeeYearPlan plan = new MergeFeeYearPlan();
        plan.setFeeYear(year);
        List<HeatingFee> k = keptRows == null ? List.of() : keptRows;
        List<HeatingFee> a = awayRows == null ? List.of() : awayRows;
        if (!k.isEmpty()) {
            plan.setKeptFeeId(k.get(0).getId());
            plan.setKeptReceivable(k.get(0).getReceivable());
            plan.setKeptPaid(k.get(0).getPaid());
        }
        if (!a.isEmpty()) {
            plan.setAwayFeeId(a.get(0).getId());
            plan.setAwayReceivable(a.get(0).getReceivable());
            plan.setAwayPaid(a.get(0).getPaid());
        }

        if (k.isEmpty()) {
            // 仅待归并档案有该年度记录：原样改挂，金额不动
            plan.setAction(Dict.FEE_ACTION_MIGRATE);
            plan.setActionText("原样迁移");
            plan.setNote("保留档案无 " + year + " 年度台账，原记录金额、缴费状态不变，直接改挂到保留档案");
            return plan;
        }
        if (a.isEmpty()) {
            // 仅保留档案有：无需处置
            plan.setAction(Dict.FEE_ACTION_MIGRATE);
            plan.setActionText("保留档案独有");
            plan.setNote(year + " 年度仅保留档案有台账，归并不改动该记录");
            return plan;
        }

        boolean anyPaid = hasAnyPaid(k) || hasAnyPaid(a);
        boolean strictSingle = k.size() == 1 && a.size() == 1;
        if (!anyPaid && strictSingle) {
            HeatingFee kf = k.get(0);
            BigDecimal price = kf.getUnitPrice() != null ? kf.getUnitPrice()
                    : (a.get(0).getUnitPrice() != null ? a.get(0).getUnitPrice() : BigDecimal.valueOf(25.40));
            BigDecimal area = kept.getAreaM2() == null ? kf.getBillingArea() : kept.getAreaM2();
            BigDecimal receivable = Dict.calcReceivable(area, price);
            plan.setAction(Dict.FEE_ACTION_RECALC);
            plan.setActionText("按保留档案面积重算");
            plan.setRecalcBillingArea(area);
            plan.setRecalcReceivable(receivable);
            plan.setRecalcArrears(receivable);
            plan.setNote("两边 " + year + " 年度都未发生缴费，不相加；按保留档案计费面积 "
                    + area.stripTrailingZeros().toPlainString() + " ㎡ × " + price.stripTrailingZeros().toPlainString()
                    + " 元/㎡ 重算应收 " + receivable + " 元，待归并侧旧台账删除（金额快照进审计）");
            return plan;
        }

        // 任一方已有实收（或同年度台账异常有多条）：金额一律不动，保留待财务复核
        plan.setAction(Dict.FEE_ACTION_KEEP_REVIEW);
        plan.setActionText("保留原始金额·待财务复核");
        plan.setNote(anyPaid
                ? year + " 年度两边都有记录且已有实收，金额一律不相加、不重算；两边原始台账都保留并挂到保留档案，"
                        + "标记待财务复核，复核完成前禁止继续登记该年度缴费"
                : year + " 年度同年度台账存在多条异常记录，为避免错算一律保留原始金额，标待财务复核");
        return plan;
    }

    // ================================================================
    // 内部：确认期校验与执行
    // ================================================================

    private void validate(Resident kept, Resident away, ResidentMergeRequest request) {
        if (kept.getId().equals(away.getId())) {
            throw new BizException("保留档案与待归并档案不能是同一档案");
        }
        if (kept.isArchived() || away.isArchived()) {
            throw new BizException("归档档案为只读，不能再次参与归并");
        }
        boolean addressSame = eq(kept.getCommunity(), away.getCommunity())
                && eq(kept.getBuildingNo(), away.getBuildingNo())
                && eq(kept.getUnitNo(), away.getUnitNo())
                && eq(kept.getRoomNo(), away.getRoomNo());
        if (!addressSame) {
            throw new BizException("两边地址不指向同一住房，禁止归并");
        }
        if (!Boolean.TRUE.equals(request.getAddressConfirmed())) {
            throw new BizException("请先勾选确认「两边地址确实指向同一住房」");
        }
        if (isBlank(kept.getMeterNo()) || isBlank(away.getMeterNo())
                || !eq(kept.getMeterNo(), away.getMeterNo())) {
            throw new BizException("热表号不一致（" + dash(kept.getMeterNo()) + " / "
                    + dash(away.getMeterNo()) + "），必须停止归并");
        }
        if (kept.getStation() == null || away.getStation() == null
                || !kept.getStation().getId().equals(away.getStation().getId())) {
            throw new BizException("所属换热站不一致，必须停止归并");
        }
        boolean conflict = !eq(kept.getSupplyStatus(), away.getSupplyStatus());
        String chosen = request.getChosenSupplyStatus();
        if (conflict) {
            if (!Dict.isValidSupplyStatus(chosen)) {
                throw new BizException("供热状态不一致，必须明确选择采用「正常供热」还是「已报停」");
            }
            if (!chosen.equals(kept.getSupplyStatus()) && !chosen.equals(away.getSupplyStatus())) {
                throw new BizException("只能在两边现有供热状态（" + kept.getSupplyStatus()
                        + " / " + away.getSupplyStatus() + "）中选择");
            }
        } else if (chosen != null && !chosen.isBlank() && !chosen.equals(kept.getSupplyStatus())) {
            throw new BizException("两边供热状态一致，无需改选状态");
        }
        if (Dict.SUPPLY_SUSPENDED.equals(chosen)) {
            BigDecimal arrears = sumArrears(kept.getId()).add(sumArrears(away.getId()));
            if (arrears.compareTo(Dict.ARREARS_EPSILON) >= 0) {
                throw new BizException("选择「已报停」但两户合计欠费 "
                        + arrears.setScale(2, RoundingMode.HALF_UP) + " 元，按业务规则须先缴清欠费");
            }
        }
    }

    private List<MergeResultVO.FeeYearResult> handleFees(Resident kept, Resident away, ResidentMergeLog log) {
        Map<Integer, List<HeatingFee>> keptByYear = groupByYear(
                feeRepository.findByResidentIdOrderByFeeYearDesc(kept.getId()));
        Map<Integer, List<HeatingFee>> awayByYear = groupByYear(
                feeRepository.findByResidentIdOrderByFeeYearDesc(away.getId()));
        Map<Integer, MergeResultVO.FeeYearResult> results = new LinkedHashMap<>();

        // 待归并档案独有的年度：原样迁移
        for (Map.Entry<Integer, List<HeatingFee>> entry : awayByYear.entrySet()) {
            Integer year = entry.getKey();
            if (keptByYear.containsKey(year)) {
                continue;
            }
            for (HeatingFee f : entry.getValue()) {
                f.setResident(kept);
                f.setMergeLogId(log.getId());
                feeRepository.save(f);
            }
            MergeResultVO.FeeYearResult migrate = feeResult(year, Dict.FEE_ACTION_MIGRATE,
                    "原样迁移", null, false, "原记录金额/状态不变，已改挂保留档案");
            migrate.setTouchedRows(entry.getValue().size());
            results.put(year, migrate);
        }

        // 两边都有的年度
        for (Map.Entry<Integer, List<HeatingFee>> entry : keptByYear.entrySet()) {
            Integer year = entry.getKey();
            List<HeatingFee> k = entry.getValue();
            List<HeatingFee> a = awayByYear.get(year);
            if (a == null) {
                results.computeIfAbsent(year, y -> {
                    MergeResultVO.FeeYearResult own = feeResult(y, Dict.FEE_ACTION_MIGRATE,
                            "保留档案独有", null, false,
                            y + " 年度仅保留档案有台账，未改动");
                    own.setTouchedRows(0);
                    return own;
                });
                continue;
            }
            boolean anyPaid = hasAnyPaid(k) || hasAnyPaid(a);
            boolean strictSingle = k.size() == 1 && a.size() == 1;
            if (!anyPaid && strictSingle) {
                // 都未缴费：删除待归并侧旧台账（金额快照在备注/审计），再按保留档案计费面积重算保留侧
                HeatingFee kf = k.get(0);
                HeatingFee af = a.get(0);
                BigDecimal awayReceivableSnapshot = nvl(af.getReceivable());
                BigDecimal awayPriceSnapshot = af.getUnitPrice();
                BigDecimal price = kf.getUnitPrice() != null ? kf.getUnitPrice()
                        : (awayPriceSnapshot != null ? awayPriceSnapshot : BigDecimal.valueOf(25.40));
                BigDecimal area = kept.getAreaM2() == null ? kf.getBillingArea() : kept.getAreaM2();
                feeRepository.delete(af);
                BigDecimal receivable = Dict.calcReceivable(area, price);
                kf.setBillingArea(area);
                kf.setUnitPrice(price);
                kf.setReceivable(receivable);
                kf.setPaid(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                kf.setArrears(receivable);
                kf.setPayStatus("欠费");
                kf.setLastPayDate(null);
                kf.setMergeLogId(log.getId());
                kf.setRemark(trimRemark(Dict.RECALC_REMARK_PREFIX + year + " 年度两边均未缴费，按保留档案 "
                        + area.stripTrailingZeros().toPlainString() + " ㎡ 重算；原待归并档案应收 "
                        + awayReceivableSnapshot + " 元已并入审计，归并单号 " + log.getMergeNo()));
                feeRepository.save(kf);
                MergeResultVO.FeeYearResult recalc = feeResult(year, Dict.FEE_ACTION_RECALC,
                        "按保留档案面积重算", receivable, false,
                        "重算应收 " + receivable + " 元；待归并侧旧台账已删除，金额快照进审计");
                recalc.setPaid(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                recalc.setArrears(receivable);
                recalc.setTouchedRows(1);
                results.put(year, recalc);
            } else {
                // 任一方已有实收 / 台账异常：原始金额一律不动，全部并挂保留档案并待财务复核
                for (HeatingFee f : k) {
                    f.setReviewRequired(true);
                    f.setReviewed(false);
                    f.setMergeLogId(log.getId());
                    f.setRemark(appendRemark(f.getRemark(),
                            Dict.REVIEW_REMARK_PREFIX + year + " 年度同号档案有实收，金额保留待复核"));
                    feeRepository.save(f);
                }
                for (HeatingFee f : a) {
                    f.setResident(kept);
                    f.setReviewRequired(true);
                    f.setReviewed(false);
                    f.setMergeYearDup(true);
                    f.setMergeLogId(log.getId());
                    f.setRemark(appendRemark(f.getRemark(),
                            Dict.REVIEW_REMARK_PREFIX + "原待归并档案台账，实收金额原样保留"));
                    feeRepository.save(f);
                }
                MergeResultVO.FeeYearResult review = feeResult(year, Dict.FEE_ACTION_KEEP_REVIEW,
                        "保留原始金额·待财务复核", null, true,
                        "两边原始金额均未改动并挂保留档案，财务复核前禁止登记 " + year + " 年度缴费");
                review.setTouchedRows(k.size() + a.size());
                results.put(year, review);
            }
        }
        return new ArrayList<>(results.values());
    }

    private int migrateSamples(Resident kept, Resident away) {
        List<RoomTempSample> samples = sampleRepository.findByResidentIdOrderByIdAsc(away.getId());
        for (RoomTempSample sample : samples) {
            sample.setResident(kept);
            sampleRepository.save(sample);
        }
        return samples.size();
    }

    private int migrateOrders(Resident kept, Resident away) {
        List<RepairOrder> orders = orderRepository.findByResidentIdOrderByIdAsc(away.getId());
        int count = 0;
        for (RepairOrder order : orders) {
            order.setResident(kept);
            // 换热站两边一致（预检已拦截），station_id、工单号、时间、处理结果原样保留
            orderRepository.save(order);
            count++;
        }
        return count;
    }

    // ================================================================
    // 内部：工具
    // ================================================================

    private Resident loadActive(Long id, String label) {
        Resident r = residentRepository.findById(id)
                .orElseThrow(() -> new BizException(label + "不存在：id=" + id));
        if (r.isArchived()) {
            Long redirect = r.getMergedInto() == null ? null : r.getMergedInto().getId();
            throw new BizException(label + "已归并归档（只读）"
                    + (redirect != null ? "，请改用保留档案 id=" + redirect : "")
                    + "，不能再次参与归并");
        }
        return r;
    }

    private Resident canonical(Resident r) {
        Resident cur = r;
        int guard = 0;
        while (cur != null && cur.isArchived() && cur.getMergedInto() != null && guard++ < 8) {
            cur = cur.getMergedInto();
        }
        return cur == null ? r : cur;
    }

    private Map<Integer, List<HeatingFee>> groupByYear(List<HeatingFee> fees) {
        Map<Integer, List<HeatingFee>> map = new LinkedHashMap<>();
        for (HeatingFee f : fees) {
            map.computeIfAbsent(f.getFeeYear(), y -> new ArrayList<>()).add(f);
        }
        return map;
    }

    private boolean hasAnyPaid(List<HeatingFee> fees) {
        for (HeatingFee f : fees) {
            if (nvl(f.getPaid()).compareTo(BigDecimal.ZERO) > 0) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal sumArrears(Long residentId) {
        BigDecimal v = feeRepository.sumArrearsByResident(residentId);
        return v == null ? BigDecimal.ZERO : v;
    }

    private MergeResultVO.FeeYearResult feeResult(Integer year, String action, String actionText,
                                                  BigDecimal receivable, boolean review, String note) {
        MergeResultVO.FeeYearResult r = new MergeResultVO.FeeYearResult();
        r.setFeeYear(year);
        r.setAction(action);
        r.setActionText(actionText);
        r.setReceivable(receivable);
        r.setReviewRequired(review);
        r.setNote(note);
        return r;
    }

    private String writePlanJson(List<MergeResultVO.FeeYearResult> results) {
        try {
            return objectMapper.writeValueAsString(results);
        } catch (Exception e) {
            return "[]";
        }
    }

    private MergeResultVO buildIdempotentResult(ResidentMergeLog log) {
        MergeResultVO result = new MergeResultVO();
        result.setMergeNo(log.getMergeNo());
        result.setIdempotent(true);
        result.setKeptResidentId(log.getKeptResident().getId());
        result.setAwayResidentId(log.getAwayResident().getId());
        result.setChosenSupplyStatus(log.getChosenSupplyStatus());
        result.setSampleMigrated(log.getSampleCount());
        result.setOrderMigrated(log.getOrderCount());
        result.setFeeHandled(log.getFeeCount());
        result.setCreatedAt(log.getCreatedAt() == null ? null : log.getCreatedAt().format(TIME_FMT));
        List<MergeResultVO.FeeYearResult> feeResults = new ArrayList<>();
        if (log.getFeePlanJson() != null) {
            try {
                feeResults = objectMapper.readValue(log.getFeePlanJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<MergeResultVO.FeeYearResult>>() {
                        });
            } catch (Exception ignored) {
                feeResults = new ArrayList<>();
            }
        }
        result.setFeeResults(feeResults);
        return result;
    }

    private String nextMergeNo() {
        // 当天已有的最大 id +1 作为序号：失败记录也占号，避免 REQUIRES_NEW 先插入失败记录后撞唯一键
        java.sql.Timestamp dayStart = java.sql.Timestamp.valueOf(LocalDate.now().atStartOfDay());
        long seq = mergeLogRepository.maxIdToday(dayStart) + 1;
        return "MG" + LocalDate.now().format(NO_FMT) + String.format("%03d", seq);
    }

    /** 极端情况下（单号尚未生成就抛错）给失败记录兜底一个不撞号的单号 */
    private String nextFallbackNo() {
        try {
            return nextMergeNo();
        } catch (Exception e) {
            return "MG" + LocalDate.now().format(NO_FMT) + "X" + System.currentTimeMillis() % 100000;
        }
    }

    private ResidentVO toSimpleVo(Resident r) {
        HeatStation st = r.getStation();
        ResidentVO vo = new ResidentVO(
                r.getId(), r.getCommunity(), r.getBuildingNo(), r.getUnitNo(), r.getRoomNo(),
                r.getAreaM2(), r.getMeterNo(), r.getTotalHeatGj(),
                st == null ? null : st.getId(),
                st == null ? null : st.getCode(),
                st == null ? null : st.getName(),
                r.getSupplyStatus(), r.getContactPhone(), sumArrears(r.getId()));
        vo.setArchived(r.isArchived());
        vo.setMergedIntoId(r.getMergedInto() == null ? null : r.getMergedInto().getId());
        vo.setLegacyCode(r.getLegacyCode());
        return vo;
    }

    private ResolveTargetVO toResolve(Resident r, String redirectedFrom) {
        HeatStation st = r.getStation();
        ResolveTargetVO vo = new ResolveTargetVO();
        vo.setResidentId(r.getId());
        vo.setLegacyCode(r.getLegacyCode() == null ? "R" + r.getId() : r.getLegacyCode());
        vo.setCommunity(r.getCommunity());
        vo.setBuildingNo(r.getBuildingNo());
        vo.setUnitNo(r.getUnitNo());
        vo.setRoomNo(r.getRoomNo());
        vo.setAddressText(addressText(r));
        vo.setMeterNo(r.getMeterNo());
        vo.setStationCode(st == null ? null : st.getCode());
        vo.setStationName(st == null ? null : st.getName());
        vo.setSupplyStatus(r.getSupplyStatus());
        vo.setArchived(r.isArchived());
        vo.setRedirected(redirectedFrom != null);
        vo.setRedirectedFromCode(redirectedFrom);
        return vo;
    }

    private String snapshot(Resident r) {
        return addressText(r) + "｜热表 " + dash(r.getMeterNo())
                + "｜" + stationCode(r) + "｜" + dash(r.getSupplyStatus())
                + "｜面积 " + (r.getAreaM2() == null ? "-" : r.getAreaM2().stripTrailingZeros().toPlainString());
    }

    private String addressText(Resident r) {
        return nz(r.getCommunity()) + nz(r.getBuildingNo()) + "号楼" + nz(r.getUnitNo())
                + "单元" + nz(r.getRoomNo());
    }

    private String stationCode(Resident r) {
        return r.getStation() == null ? "-" : dash(r.getStation().getCode());
    }

    private String appendRemark(String origin, String suffix) {
        String base = origin == null ? "" : origin;
        String merged = base.isBlank() ? suffix : base + "；" + suffix;
        return trimRemark(merged);
    }

    private String trimRemark(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > 128 ? text.substring(0, 128) : text;
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String nz(String v) {
        return v == null ? "" : v;
    }

    private String dash(String v) {
        return v == null || v.isBlank() ? "-" : v;
    }

    private boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private boolean isBlank(String v) {
        return v == null || v.isBlank();
    }
}
