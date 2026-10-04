package com.heat.supply.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heat.supply.common.BizException;
import com.heat.supply.common.Dict;
import com.heat.supply.domain.HeatingFee;
import com.heat.supply.domain.HeatStation;
import com.heat.supply.domain.RepairOrder;
import com.heat.supply.domain.Resident;
import com.heat.supply.domain.ResidentAlias;
import com.heat.supply.domain.ResidentMergeAudit;
import com.heat.supply.domain.RoomTempSample;
import com.heat.supply.dto.FeeVO;
import com.heat.supply.dto.MergeAuditVO;
import com.heat.supply.dto.MergeCandidateVO;
import com.heat.supply.dto.MergeConfirmRequest;
import com.heat.supply.dto.MergePreviewVO;
import com.heat.supply.dto.ResidentVO;
import com.heat.supply.dto.ResolveProfileVO;
import com.heat.supply.repository.HeatingFeeRepository;
import com.heat.supply.repository.RepairOrderRepository;
import com.heat.supply.repository.ResidentAliasRepository;
import com.heat.supply.repository.ResidentMergeAuditRepository;
import com.heat.supply.repository.ResidentRepository;
import com.heat.supply.repository.RoomTempSampleRepository;

import jakarta.persistence.EntityManager;

/**
 * 模块 6：重复热用户档案归并。
 * 预检一次看清地址 / 热表号 / 换热站 / 供热状态与抽测、报修、各年度采暖费；
 * 确认时在同一事务内完成引用迁移、收费处置、别名落档、审计写入，任一失败整笔回退。
 */
@Service
public class ResidentMergeService {

    /** 收费年度处置：仅一方有台账，原样改挂 */
    public static final String FEE_ACTION_KEEP_ONLY = "KEEP_ONLY";
    /** 两边均未缴费：按保留档案计费面积重算，重复行作废 */
    public static final String FEE_ACTION_RECOMPUTE = "RECOMPUTE";
    /** 任一方已有实收：保留原始金额，双方标待财务复核 */
    public static final String FEE_ACTION_REVIEW = "REVIEW";

    private final ResidentRepository residentRepository;
    private final HeatingFeeRepository feeRepository;
    private final RoomTempSampleRepository sampleRepository;
    private final RepairOrderRepository orderRepository;
    private final ResidentMergeAuditRepository auditRepository;
    private final ResidentAliasRepository aliasRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    public ResidentMergeService(ResidentRepository residentRepository,
                                HeatingFeeRepository feeRepository,
                                RoomTempSampleRepository sampleRepository,
                                RepairOrderRepository orderRepository,
                                ResidentMergeAuditRepository auditRepository,
                                ResidentAliasRepository aliasRepository,
                                StringRedisTemplate redisTemplate,
                                ObjectMapper objectMapper,
                                EntityManager entityManager) {
        this.residentRepository = residentRepository;
        this.feeRepository = feeRepository;
        this.sampleRepository = sampleRepository;
        this.orderRepository = orderRepository;
        this.auditRepository = auditRepository;
        this.aliasRepository = aliasRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
    }

    // ------------------------------------------------------------------
    // 疑似重复候选
    // ------------------------------------------------------------------

    /** 疑似重复档案列表：同一住房地址下存在 >=2 个在档档案 */
    @Transactional(readOnly = true)
    public List<MergeCandidateVO> listCandidates() {
        Map<String, MergeCandidateVO> grouped = new LinkedHashMap<>();
        for (Resident r : residentRepository.findAll()) {
            if (r.isMerged()) {
                continue;
            }
            String key = addressKey(r);
            MergeCandidateVO vo = grouped.computeIfAbsent(key, k -> {
                MergeCandidateVO c = new MergeCandidateVO();
                c.setCommunity(r.getCommunity());
                c.setBuildingNo(r.getBuildingNo());
                c.setUnitNo(r.getUnitNo());
                c.setRoomNo(r.getRoomNo());
                c.setFullAddress(fullAddress(r));
                c.setProfiles(new ArrayList<>());
                return c;
            });
            vo.getProfiles().add(toCandidateProfile(r));
        }
        List<MergeCandidateVO> result = new ArrayList<>();
        for (MergeCandidateVO vo : grouped.values()) {
            if (vo.getCount() >= 2) {
                vo.getProfiles().sort(Comparator.comparing(MergeCandidateVO.Profile::getResidentId));
                result.add(vo);
            }
        }
        result.sort(Comparator.comparing(MergeCandidateVO::getFullAddress));
        return result;
    }

    // ------------------------------------------------------------------
    // 归并预检
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public MergePreviewVO preview(Long keeperId, Long mergedId) {
        Resident keeper = loadActive(keeperId, "保留档案");
        Resident merged = loadActive(mergedId, "待归并档案");
        if (keeperId.equals(mergedId)) {
            throw new BizException("保留档案与待归并档案不能是同一档案");
        }
        return buildPreview(keeper, merged);
    }

    private MergePreviewVO buildPreview(Resident keeper, Resident merged) {
        MergePreviewVO vo = new MergePreviewVO();
        vo.setKeeper(toSide(keeper));
        vo.setMerged(toSide(merged));

        boolean sameAddress = addressKey(keeper).equals(addressKey(merged));
        vo.setSameAddress(sameAddress);

        boolean meterMatch = hasText(keeper.getMeterNo())
                && keeper.getMeterNo().trim().equals(nvl(merged.getMeterNo()).trim());
        vo.setMeterMatch(meterMatch);

        Long ks = keeper.getStation() == null ? null : keeper.getStation().getId();
        Long ms = merged.getStation() == null ? null : merged.getStation().getId();
        boolean stationMatch = ks != null && ks.equals(ms);
        vo.setStationMatch(stationMatch);

        boolean statusMatch = nvl(keeper.getSupplyStatus()).equals(nvl(merged.getSupplyStatus()));
        vo.setSupplyStatusMatch(statusMatch);

        List<String> blockers = new ArrayList<>();
        if (!sameAddress) {
            blockers.add("地址不一致：「" + fullAddress(keeper) + "」与「" + fullAddress(merged)
                    + "」不是同一住房，只有地址确实指向同一住房才能继续归并");
        }
        if (!meterMatch) {
            blockers.add("热表号不一致：保留档案 " + display(keeper.getMeterNo()) + "，待归并档案 "
                    + display(merged.getMeterNo()) + "，热表号不同必须停止归并");
        }
        if (!stationMatch) {
            blockers.add("所属换热站不一致：保留档案 " + stationCode(keeper) + "，待归并档案 "
                    + stationCode(merged) + "，换热站不同必须停止归并");
        }
        vo.setBlockers(blockers);

        if (!statusMatch) {
            vo.setSupplyStatusImpacts(List.of(
                    impact("正常供热",
                            "归并后按正常在供户管理，可继续安排室温抽测入户",
                            "报修工单正常受理与闭环，不受报停限制",
                            "各年度缴费可正常登记；历史欠费继续按台账催缴"),
                    impact("已报停",
                            "停止室温抽测入户，录入抽测将被拦截，直至恢复供热",
                            "报修可登记但报停期间不产生供热类缴费",
                            "禁止登记缴费（恢复供热后办理）；报停前须先结清历史欠费")));
        }

        // 室温抽测
        List<MergePreviewVO.SampleRef> samples = new ArrayList<>();
        for (RoomTempSample s : sampleRepository.findByResidentIdOrderBySampleDateDesc(keeper.getId())) {
            samples.add(toSampleRef(s, "keeper"));
        }
        for (RoomTempSample s : sampleRepository.findByResidentIdOrderBySampleDateDesc(merged.getId())) {
            samples.add(toSampleRef(s, "merged"));
        }
        samples.sort(Comparator.comparing(MergePreviewVO.SampleRef::getSampleDate,
                Comparator.nullsLast(Comparator.reverseOrder())));
        vo.setSamples(samples);

        // 报修工单
        List<MergePreviewVO.OrderRef> orders = new ArrayList<>();
        for (RepairOrder o : orderRepository.findAll()) {
            Long rid = o.getResident() == null ? null : o.getResident().getId();
            if (keeper.getId().equals(rid)) {
                orders.add(toOrderRef(o, "keeper"));
            } else if (merged.getId().equals(rid)) {
                orders.add(toOrderRef(o, "merged"));
            }
        }
        orders.sort(Comparator.comparing(MergePreviewVO.OrderRef::getReportedDate,
                Comparator.nullsLast(Comparator.reverseOrder())));
        vo.setOrders(orders);

        // 收费年度
        List<HeatingFee> bothFees = feeRepository.findByResidentIdInOrderByFeeYearAscIdAsc(
                List.of(keeper.getId(), merged.getId()));
        Map<Integer, List<HeatingFee>> byYear = new TreeMap<>();
        for (HeatingFee f : bothFees) {
            byYear.computeIfAbsent(f.getFeeYear(), y -> new ArrayList<>()).add(f);
        }
        List<MergePreviewVO.FeeYearPlan> plans = new ArrayList<>();
        int keeperFeeCount = 0;
        int mergedFeeCount = 0;
        for (Map.Entry<Integer, List<HeatingFee>> entry : byYear.entrySet()) {
            List<HeatingFee> rows = entry.getValue();
            HeatingFee kf = null;
            HeatingFee mf = null;
            for (HeatingFee f : rows) {
                if (f.getResident().getId().equals(keeper.getId())) {
                    kf = f;
                    keeperFeeCount++;
                } else {
                    mf = f;
                    mergedFeeCount++;
                }
            }
            plans.add(buildFeeYearPlan(entry.getKey(), keeper, kf, mf));
        }
        vo.setFeeYears(plans);

        vo.setKeeperSampleCount((int) samples.stream().filter(s -> "keeper".equals(s.getSide())).count());
        vo.setMergedSampleCount((int) samples.stream().filter(s -> "merged".equals(s.getSide())).count());
        vo.setKeeperOrderCount((int) orders.stream().filter(o -> "keeper".equals(o.getSide())).count());
        vo.setMergedOrderCount((int) orders.stream().filter(o -> "merged".equals(o.getSide())).count());
        vo.setKeeperFeeCount(keeperFeeCount);
        vo.setMergedFeeCount(mergedFeeCount);
        return vo;
    }

    private MergePreviewVO.FeeYearPlan buildFeeYearPlan(Integer year, Resident keeper,
                                                        HeatingFee kf, HeatingFee mf) {
        MergePreviewVO.FeeYearPlan plan = new MergePreviewVO.FeeYearPlan();
        plan.setFeeYear(year);
        plan.setKeeperRow(kf == null ? null : toFeeRow(kf));
        plan.setMergedRow(mf == null ? null : toFeeRow(mf));

        if (kf != null && mf != null) {
            boolean bothUnpaid = paid(kf).compareTo(BigDecimal.ZERO) <= 0
                    && paid(mf).compareTo(BigDecimal.ZERO) <= 0;
            if (bothUnpaid) {
                plan.setAction(FEE_ACTION_RECOMPUTE);
                plan.setActionLabel("按保留档案计费面积重算");
                BigDecimal unitPrice = kf.getUnitPrice() != null
                        ? kf.getUnitPrice()
                        : (mf.getUnitPrice() != null ? mf.getUnitPrice() : BigDecimal.ZERO);
                BigDecimal receivable = Dict.calcReceivable(keeper.getAreaM2(), unitPrice);
                plan.setRecomputedReceivable(receivable);
                plan.setActionNote("两边 " + year + " 年度均未发生缴费：保留档案按其计费面积 "
                        + keeper.getAreaM2() + " ㎡ × 单价 " + unitPrice + " 元/㎡ 重算应收 "
                        + receivable + " 元；待归并档案该年度台账行（应收 " + mf.getReceivable()
                        + " 元）归并时作废，不作废保留档案任何金额。");
                plan.setPayBlockedUntilReview(false);
            } else {
                plan.setAction(FEE_ACTION_REVIEW);
                plan.setActionLabel("保留原始金额 · 待财务复核");
                plan.setActionNote("两边 " + year + " 年度已有实收（保留档案实收 " + paid(kf)
                        + " 元、待归并档案实收 " + paid(mf) + " 元），金额不得简单相加或重算："
                        + "两条台账保留原始金额并标为待财务复核，复核完成前禁止继续登记该年度缴费。");
                plan.setPayBlockedUntilReview(true);
            }
        } else {
            plan.setAction(FEE_ACTION_KEEP_ONLY);
            plan.setActionLabel("原样改挂");
            HeatingFee only = kf != null ? kf : mf;
            String side = kf != null ? "保留档案" : "待归并档案";
            plan.setActionNote(year + " 年度仅" + side + "有台账（应收 " + only.getReceivable()
                    + " 元、实收 " + paid(only) + " 元），金额、编号、时间一律不变，归并后改挂保留档案。");
            plan.setPayBlockedUntilReview(only.isReviewFlag());
        }
        return plan;
    }

    // ------------------------------------------------------------------
    // 归并确认（单事务：引用迁移 + 收费处置 + 别名 + 审计，失败整体回退）
    // ------------------------------------------------------------------

    @Transactional
    public MergeAuditVO confirm(MergeConfirmRequest request) {
        Long keeperId = request.getKeeperId();
        Long mergedId = request.getMergedId();
        if (keeperId == null || mergedId == null) {
            throw new BizException("请先选择保留档案与待归并档案");
        }
        if (keeperId.equals(mergedId)) {
            throw new BizException("保留档案与待归并档案不能是同一档案");
        }

        // 行锁：两个档案同时只能有一笔归并在进行
        List<Resident> locked = residentRepository.findAllByIdForUpdate(List.of(keeperId, mergedId));
        if (locked.size() != 2) {
            throw new BizException("保留档案或待归并档案不存在，归并未执行，数据保持归并前状态");
        }
        locked.sort(Comparator.comparing(Resident::getId));
        Resident keeper = locked.get(0).getId().equals(keeperId) ? locked.get(0) : locked.get(1);
        Resident merged = locked.get(0).getId().equals(mergedId) ? locked.get(0) : locked.get(1);

        // 幂等：已成功的归并不再搬移数据 / 重算金额
        if (keeper.isMerged() || merged.isMerged()) {
            throw alreadyMerged(keeper, merged);
        }
        Optional<ResidentMergeAudit> existing = auditRepository.findByKeeperIdAndMergedId(keeperId, mergedId);
        if (existing.isPresent()) {
            throw new BizException("归并单 " + existing.get().getMergeNo()
                    + " 已成功完成，重复确认不会再次搬移数据或重算金额");
        }
        auditRepository.findByKeeperIdAndMergedId(mergedId, keeperId).ifPresent(a -> {
            throw new BizException("归并单 " + a.getMergeNo() + " 已按相反方向完成，不能反向再次归并");
        });

        // 事务内重跑预检硬规则，防止预检后数据被改动
        MergePreviewVO preview = buildPreview(keeper, merged);
        if (!preview.isMergeable()) {
            throw new BizException("归并预检未通过，已停止归并：" + String.join("；", preview.getBlockers()));
        }
        String chosenStatus;
        if (preview.isSupplyStatusMatch()) {
            chosenStatus = keeper.getSupplyStatus();
        } else {
            chosenStatus = request.getChosenSupplyStatus();
            if (!Dict.isValidSupplyStatus(chosenStatus)) {
                throw new BizException("双方供热状态不一致（保留档案：" + keeper.getSupplyStatus()
                        + "，待归并档案：" + merged.getSupplyStatus()
                        + "），请明确归并后采用「正常供热」还是「已报停」后再确认");
            }
        }

        String mergeNo = nextMergeNo();
        LocalDateTime now = LocalDateTime.now();

        // 迁移动作前先记账（审计口径），并据此核对实际影响行数
        int expectedSamples = (int) sampleRepository.findByResidentIdOrderBySampleDateDesc(mergedId).size();
        int expectedOrders = 0;
        for (RepairOrder o : orderRepository.findAll()) {
            if (o.getResident() != null && mergedId.equals(o.getResident().getId())) {
                expectedOrders++;
            }
        }
        List<HeatingFee> bothFees = feeRepository.findByResidentIdInOrderByFeeYearAscIdAsc(
                List.of(keeperId, mergedId));
        int expectedFees = bothFees.size();

        // 收费处置（先在原挂接关系上改字段）
        Map<Integer, List<HeatingFee>> byYear = new TreeMap<>();
        for (HeatingFee f : bothFees) {
            byYear.computeIfAbsent(f.getFeeYear(), y -> new ArrayList<>()).add(f);
        }
        List<String> recomputeYears = new ArrayList<>();
        List<String> reviewYears = new ArrayList<>();
        List<Map<String, Object>> detailEntries = new ArrayList<>();
        for (Map.Entry<Integer, List<HeatingFee>> entry : byYear.entrySet()) {
            List<HeatingFee> rows = entry.getValue();
            HeatingFee kf = null;
            HeatingFee mf = null;
            for (HeatingFee f : rows) {
                if (f.getResident().getId().equals(keeperId)) {
                    kf = f;
                } else {
                    mf = f;
                }
            }
            if (kf != null && mf != null) {
                boolean bothUnpaid = paid(kf).compareTo(BigDecimal.ZERO) <= 0
                        && paid(mf).compareTo(BigDecimal.ZERO) <= 0;
                if (bothUnpaid) {
                    BigDecimal unitPrice = kf.getUnitPrice() != null
                            ? kf.getUnitPrice()
                            : (mf.getUnitPrice() != null ? mf.getUnitPrice() : BigDecimal.ZERO);
                    BigDecimal receivable = Dict.calcReceivable(keeper.getAreaM2(), unitPrice);
                    kf.setBillingArea(keeper.getAreaM2());
                    kf.setUnitPrice(unitPrice);
                    kf.setReceivable(receivable);
                    kf.setPaid(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                    kf.setArrears(receivable);
                    kf.setPayStatus("欠费");
                    kf.setLastPayDate(null);
                    feeRepository.save(kf);
                    voidFee(mf, mergeNo, entry.getKey() + " 年度两边均未缴费，按保留档案计费面积重算，本重复台账行作废（金额从未实收）");
                    feeRepository.save(mf);
                    recomputeYears.add(String.valueOf(entry.getKey()));
                    detailEntries.add(detail("FEE_RECOMPUTE", entry.getKey(),
                            "保留档案台账 #" + kf.getId() + " 按 " + keeper.getAreaM2()
                                    + " ㎡重算应收 " + receivable + " 元；重复台账 #" + mf.getId() + " 作废"));
                } else {
                    kf.setReviewFlag(true);
                    mf.setReviewFlag(true);
                    feeRepository.save(kf);
                    feeRepository.save(mf);
                    reviewYears.add(String.valueOf(entry.getKey()));
                    detailEntries.add(detail("FEE_REVIEW", entry.getKey(),
                            "台账 #" + kf.getId() + "（实收 " + paid(kf) + " 元）与 #" + mf.getId()
                                    + "（实收 " + paid(mf) + " 元）保留原始金额，标待财务复核，复核前冻结该年度缴费"));
                }
            } else {
                HeatingFee only = kf != null ? kf : mf;
                detailEntries.add(detail("FEE_KEEP_ONLY", entry.getKey(),
                        "台账 #" + only.getId() + "（应收 " + only.getReceivable() + " 元、实收 "
                                + paid(only) + " 元）金额时间不变，随归并改挂保留档案"));
            }
        }

        // 先把收费字段变更（重算/待复核/作废）落库，再执行外键搬移；
        // 否则托管的 HeatingFee 脏实例会在批量更新后被按旧 resident_id 刷回去。
        // clear 后游离实体不能再触发懒加载，故先把审计快照字符串准备好。
        String keeperSnapshot = fullAddress(keeper) + " / " + display(keeper.getMeterNo())
                + " / " + stationCode(keeper);
        String mergedSnapshot = fullAddress(merged) + " / " + display(merged.getMeterNo())
                + " / " + stationCode(merged);
        String mergedMeterNo = hasText(merged.getMeterNo()) ? merged.getMeterNo().trim() : null;
        String mergedAddress = fullAddress(merged);
        entityManager.flush();

        // 引用迁移：抽测、报修、收费（含作废行）整体改挂保留档案，业务编号/时间/处理结果不变。
        // 三个批量更新共用同一对托管档案；全部执行完再 clear，避免中途 clear 使实体参数游离。
        int movedSamples = sampleRepository.reassignResident(keeper, merged);
        int movedOrders = orderRepository.reassignResident(keeper, merged);
        int movedFees = feeRepository.reassignResident(keeper, merged);
        if (movedSamples != expectedSamples || movedOrders != expectedOrders || movedFees + countKeeperRows(bothFees, keeperId) != expectedFees) {
            throw new BizException("引用迁移行数核对不符（抽测 " + movedSamples + "/" + expectedSamples
                    + "，报修 " + movedOrders + "/" + expectedOrders + "，收费 " + movedFees
                    + "），为避免遗留分散归属，整次归并回退");
        }
        // 清空持久化上下文：后续别名/档案状态一律按库里最新状态操作，杜绝旧关联回刷
        entityManager.clear();
        detailEntries.add(0, detail("SAMPLE_MOVE", null,
                movedSamples + " 条室温抽测改挂保留档案，抽测日期/室温/达标结论/抽测人保持不变"));
        detailEntries.add(1, detail("ORDER_MOVE", null,
                movedOrders + " 条报修工单改挂保留档案，工单号/报修时间/处理结果/状态保持不变"));

        // 旧标识别名：热表号、旧档案编号、地址三类查询统一落到保留档案
        Resident reloadedKeeper = residentRepository.findById(keeperId)
                .orElseThrow(() -> new BizException("归并异常：保留档案丢失，整次归并回退"));
        Resident reloadedMerged = residentRepository.findById(mergedId)
                .orElseThrow(() -> new BizException("归并异常：待归并档案丢失，整次归并回退"));
        registerAlias(ResidentAlias.TYPE_OLD_ID, String.valueOf(mergedId),
                reloadedKeeper, reloadedMerged, mergeNo, now);
        if (mergedMeterNo != null) {
            registerAlias(ResidentAlias.TYPE_METER_NO, mergedMeterNo,
                    reloadedKeeper, reloadedMerged, mergeNo, now);
        }
        registerAlias(ResidentAlias.TYPE_ADDRESS, normalizeAddress(mergedAddress),
                reloadedKeeper, reloadedMerged, mergeNo, now);

        // 保留档案采用工作人员确认的供热状态；重复档案转只读
        reloadedKeeper.setSupplyStatus(chosenStatus);
        reloadedMerged.setMerged(true);
        reloadedMerged.setMergedInto(reloadedKeeper);
        reloadedMerged.setMergedAt(now);
        reloadedMerged.setMergeNo(mergeNo);
        residentRepository.save(reloadedKeeper);
        residentRepository.save(reloadedMerged);

        // 审计写入失败（如单号冲突）同样整笔回退
        ResidentMergeAudit audit = new ResidentMergeAudit();
        audit.setMergeNo(mergeNo);
        audit.setKeeperId(keeperId);
        audit.setMergedId(mergedId);
        audit.setKeeperSnapshot(keeperSnapshot);
        audit.setMergedSnapshot(mergedSnapshot);
        audit.setChosenSupplyStatus(chosenStatus);
        audit.setSampleMovedCount(movedSamples);
        audit.setOrderMovedCount(movedOrders);
        audit.setFeeMovedCount(movedFees);
        audit.setFeeRecomputeYear(recomputeYears.isEmpty() ? null : String.join(",", recomputeYears));
        audit.setFeeReviewYear(reviewYears.isEmpty() ? null : String.join(",", reviewYears));
        audit.setOperator(hasText(request.getOperator()) ? request.getOperator().trim() : null);
        audit.setCreatedAt(now);
        try {
            audit.setDetailsJson(objectMapper.writeValueAsString(detailEntries));
        } catch (Exception e) {
            throw new BizException("归并审计明细序列化失败，整次归并回退：" + e.getMessage());
        }
        auditRepository.save(audit);

        redisTemplate.delete(Dict.CACHE_ARREARS_RANK);
        return toAuditVo(audit);
    }

    private void registerAlias(String type, String value, Resident keeper, Resident source,
                               String mergeNo, LocalDateTime now) {
        Optional<ResidentAlias> existed = aliasRepository.findByAliasTypeAndAliasValue(type, value);
        if (existed.isPresent()) {
            ResidentAlias alias = existed.get();
            if (!alias.getResident().getId().equals(keeper.getId())) {
                throw new BizException("旧标识 " + type + "=" + value
                        + " 已指向另一保留档案，别名落档冲突，整次归并回退");
            }
            return;
        }
        ResidentAlias alias = new ResidentAlias();
        alias.setAliasType(type);
        alias.setAliasValue(value);
        alias.setResident(keeper);
        alias.setSourceResident(source);
        alias.setMergeNo(mergeNo);
        alias.setCreatedAt(now);
        aliasRepository.save(alias);
    }

    // ------------------------------------------------------------------
    // 落档解析：地址 / 热表号 / 旧档案编号查询只能落到同一户
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ResolveProfileVO resolve(String keyword) {
        if (!hasText(keyword)) {
            throw new BizException("请输入地址、热表号或旧档案编号");
        }
        String key = keyword.trim();

        // 1) 直接命中档案主键
        Long idFromKey = parseLong(key);
        if (idFromKey != null) {
            Optional<Resident> direct = residentRepository.findById(idFromKey);
            if (direct.isPresent()) {
                Resident r = direct.get();
                if (r.isMerged()) {
                    return redirected(loadKeeper(r), "OLD_ID", key, r.getId(),
                            r.getMergeNo(), "旧档案编号 " + key + " 已归并，跳转到保留档案");
                }
                return direct(r);
            }
            Optional<ResidentAlias> alias = aliasRepository.findByAliasTypeAndAliasValue(
                    ResidentAlias.TYPE_OLD_ID, key);
            if (alias.isPresent()) {
                ResidentAlias a = alias.get();
                return redirected(a.getResident(), ResidentAlias.TYPE_OLD_ID, key,
                        a.getSourceResident().getId(), a.getMergeNo(),
                        "旧档案编号 " + key + " 已归并，跳转到保留档案");
            }
        }

        // 2) 别名表（热表号 / 归一化地址 / 旧编号）
        for (String type : List.of(ResidentAlias.TYPE_METER_NO, ResidentAlias.TYPE_ADDRESS,
                ResidentAlias.TYPE_OLD_ID)) {
            String lookupValue = ResidentAlias.TYPE_ADDRESS.equals(type) ? normalizeAddress(key) : key;
            Optional<ResidentAlias> alias = aliasRepository.findByAliasTypeAndAliasValue(type, lookupValue);
            if (alias.isPresent()) {
                ResidentAlias a = alias.get();
                String label = ResidentAlias.TYPE_ADDRESS.equals(type) ? "地址"
                        : ResidentAlias.TYPE_METER_NO.equals(type) ? "热表号" : "旧档案编号";
                return redirected(a.getResident(), type, lookupValue, a.getSourceResident().getId(),
                        a.getMergeNo(), "按" + label + " " + key + " 查询命中已归并档案，跳转到保留档案");
            }
        }

        // 3) 在档档案直查（热表号 / 归一化地址）
        String normalized = normalizeAddress(key);
        List<Resident> matches = new ArrayList<>();
        for (Resident r : residentRepository.findAll()) {
            if (r.isMerged()) {
                continue;
            }
            boolean meterHit = hasText(r.getMeterNo()) && r.getMeterNo().trim().equals(key);
            boolean addressHit = normalizeAddress(fullAddress(r)).equals(normalized)
                    || normalizeAddress(simpleAddress(r)).equals(normalized);
            if (meterHit || addressHit) {
                matches.add(r);
            }
        }
        if (matches.size() == 1) {
            return direct(matches.get(0));
        }
        if (matches.size() > 1) {
            throw new BizException("该标识仍对应 " + matches.size()
                    + " 个在档档案（疑似重复未归并），请先在归并页完成预检与归并");
        }
        throw new BizException("未查询到与「" + key + "」匹配的热用户档案（支持地址、热表号、旧档案编号）");
    }

    // ------------------------------------------------------------------
    // 审计
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<MergeAuditVO> listAudits() {
        List<MergeAuditVO> result = new ArrayList<>();
        for (ResidentMergeAudit a : auditRepository.findAllByOrderByCreatedAtDescIdDesc()) {
            result.add(toAuditVo(a));
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private Resident loadActive(Long id, String label) {
        Resident r = residentRepository.findById(id)
                .orElseThrow(() -> new BizException(label + "不存在：id=" + id));
        if (r.isMerged()) {
            throw new BizException("档案 " + id + " 已归并（归并单 " + r.getMergeNo()
                    + "），为只读档案，不能再参与归并，可跳转到保留档案 "
                    + (r.getMergedInto() == null ? null : r.getMergedInto().getId()));
        }
        return r;
    }

    private Resident loadKeeper(Resident merged) {
        if (merged.getMergedInto() == null) {
            throw new BizException("已归并档案缺少保留档案指向，数据异常，请联系管理员");
        }
        return merged.getMergedInto();
    }

    private BizException alreadyMerged(Resident keeper, Resident merged) {
        Resident readOnly = keeper.isMerged() ? keeper : merged;
        Long keeperId = readOnly.getMergedInto() == null ? null : readOnly.getMergedInto().getId();
        return new BizException("档案 " + readOnly.getId() + " 已是只读归并档案（归并单 "
                + readOnly.getMergeNo() + "），不能再次归并；其保留档案为 " + keeperId);
    }

    private String nextMergeNo() {
        LocalDate today = LocalDate.now();
        LocalDateTime from = today.atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay();
        long seq = auditRepository.countByCreatedAtBetween(from, to) + 1;
        return "GB" + today.toString().replace("-", "") + String.format("%03d", seq);
    }

    private int countKeeperRows(List<HeatingFee> rows, Long keeperId) {
        int n = 0;
        for (HeatingFee f : rows) {
            if (f.getResident().getId().equals(keeperId)) {
                n++;
            }
        }
        return n;
    }

    private void voidFee(HeatingFee fee, String mergeNo, String reason) {
        fee.setVoided(true);
        fee.setVoidReason("归并单 " + mergeNo + "：" + reason);
    }

    private Map<String, Object> detail(String type, Integer year, String text) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("feeYear", year);
        m.put("text", text);
        return m;
    }

    private MergePreviewVO.SupplyStatusImpact impact(String status, String temp, String order, String fee) {
        MergePreviewVO.SupplyStatusImpact i = new MergePreviewVO.SupplyStatusImpact();
        i.setSupplyStatus(status);
        i.setTempImpact(temp);
        i.setOrderImpact(order);
        i.setFeeImpact(fee);
        return i;
    }

    private MergePreviewVO.ProfileSide toSide(Resident r) {
        MergePreviewVO.ProfileSide side = new MergePreviewVO.ProfileSide();
        side.setResidentId(r.getId());
        side.setCommunity(r.getCommunity());
        side.setBuildingNo(r.getBuildingNo());
        side.setUnitNo(r.getUnitNo());
        side.setRoomNo(r.getRoomNo());
        side.setFullAddress(fullAddress(r));
        side.setAreaM2(r.getAreaM2());
        side.setMeterNo(r.getMeterNo());
        HeatStation station = r.getStation();
        side.setStationId(station == null ? null : station.getId());
        side.setStationCode(station == null ? null : station.getCode());
        side.setStationName(station == null ? null : station.getName());
        side.setSupplyStatus(r.getSupplyStatus());
        side.setContactPhone(r.getContactPhone());
        side.setMerged(r.isMerged());
        side.setMergedIntoId(r.getMergedInto() == null ? null : r.getMergedInto().getId());
        return side;
    }

    private MergeCandidateVO.Profile toCandidateProfile(Resident r) {
        MergeCandidateVO.Profile p = new MergeCandidateVO.Profile();
        p.setResidentId(r.getId());
        p.setMeterNo(r.getMeterNo());
        p.setAreaM2(r.getAreaM2());
        HeatStation station = r.getStation();
        p.setStationCode(station == null ? null : station.getCode());
        p.setStationName(station == null ? null : station.getName());
        p.setSupplyStatus(r.getSupplyStatus());
        p.setContactPhone(r.getContactPhone());
        p.setSampleCount((int) sampleRepository.findByResidentIdOrderBySampleDateDesc(r.getId()).size());
        int orderCount = 0;
        for (RepairOrder o : orderRepository.findAll()) {
            if (o.getResident() != null && r.getId().equals(o.getResident().getId())) {
                orderCount++;
            }
        }
        p.setOrderCount(orderCount);
        p.setFeeCount(feeRepository.findByResidentIdOrderByFeeYearDesc(r.getId()).size());
        return p;
    }

    private MergePreviewVO.SampleRef toSampleRef(RoomTempSample s, String side) {
        MergePreviewVO.SampleRef ref = new MergePreviewVO.SampleRef();
        ref.setId(s.getId());
        ref.setSide(side);
        ref.setSampleDate(s.getSampleDate());
        ref.setTemperature(s.getTemperature());
        ref.setQualified(s.getQualified());
        ref.setHouseholdName(s.getHouseholdName());
        ref.setSurveyor(s.getSurveyor());
        return ref;
    }

    private MergePreviewVO.OrderRef toOrderRef(RepairOrder o, String side) {
        MergePreviewVO.OrderRef ref = new MergePreviewVO.OrderRef();
        ref.setId(o.getId());
        ref.setSide(side);
        ref.setOrderNo(o.getOrderNo());
        ref.setReportedDate(o.getReportedDate());
        ref.setFaultType(o.getFaultType());
        ref.setOrderStatus(o.getOrderStatus());
        ref.setAssignee(o.getAssignee());
        ref.setHandleResult(o.getHandleResult());
        ref.setClosedDate(o.getClosedDate());
        return ref;
    }

    private MergePreviewVO.FeeRow toFeeRow(HeatingFee f) {
        MergePreviewVO.FeeRow row = new MergePreviewVO.FeeRow();
        row.setId(f.getId());
        row.setBillingArea(f.getBillingArea());
        row.setUnitPrice(f.getUnitPrice());
        row.setReceivable(f.getReceivable());
        row.setPaid(f.getPaid());
        row.setArrears(f.getArrears());
        row.setPayStatus(f.getPayStatus());
        row.setLastPayDate(f.getLastPayDate());
        row.setReviewFlag(f.isReviewFlag());
        row.setVoided(f.isVoided());
        return row;
    }

    private ResolveProfileVO direct(Resident r) {
        ResolveProfileVO vo = new ResolveProfileVO();
        vo.setResident(toResidentVo(r));
        vo.setRedirected(false);
        return vo;
    }

    private ResolveProfileVO redirected(Resident keeper, String aliasType, String aliasValue,
                                        Long sourceId, String mergeNo, String note) {
        ResolveProfileVO vo = new ResolveProfileVO();
        vo.setResident(toResidentVo(keeper));
        vo.setRedirected(true);
        vo.setMatchedAliasType(aliasType);
        vo.setMatchedAliasValue(aliasValue);
        vo.setSourceResidentId(sourceId);
        vo.setMergeNo(mergeNo);
        vo.setRedirectNote(note);
        return vo;
    }

    private ResidentVO toResidentVo(Resident r) {
        HeatStation station = r.getStation();
        return new ResidentVO(
                r.getId(), r.getCommunity(), r.getBuildingNo(), r.getUnitNo(), r.getRoomNo(),
                r.getAreaM2(), r.getMeterNo(), r.getTotalHeatGj(),
                station == null ? null : station.getId(),
                station == null ? null : station.getCode(),
                station == null ? null : station.getName(),
                r.getSupplyStatus(), r.getContactPhone(),
                feeRepository.sumArrearsByResident(r.getId()),
                r.isMerged(),
                r.getMergedInto() == null ? null : r.getMergedInto().getId(),
                r.getMergeNo());
    }

    private MergeAuditVO toAuditVo(ResidentMergeAudit a) {
        MergeAuditVO vo = new MergeAuditVO();
        vo.setId(a.getId());
        vo.setMergeNo(a.getMergeNo());
        vo.setKeeperId(a.getKeeperId());
        vo.setMergedId(a.getMergedId());
        vo.setKeeperSnapshot(a.getKeeperSnapshot());
        vo.setMergedSnapshot(a.getMergedSnapshot());
        vo.setChosenSupplyStatus(a.getChosenSupplyStatus());
        vo.setSampleMovedCount(a.getSampleMovedCount());
        vo.setOrderMovedCount(a.getOrderMovedCount());
        vo.setFeeMovedCount(a.getFeeMovedCount());
        vo.setFeeRecomputeYear(a.getFeeRecomputeYear());
        vo.setFeeReviewYear(a.getFeeReviewYear());
        vo.setDetailsJson(a.getDetailsJson());
        vo.setOperator(a.getOperator());
        vo.setCreatedAt(a.getCreatedAt());
        return vo;
    }

    /** 供收费台账/财务复核复用的转换 */
    public FeeVO toFeeVo(HeatingFee fee) {
        Resident resident = fee.getResident();
        HeatStation station = resident == null ? null : resident.getStation();
        return new FeeVO(
                fee.getId(), fee.getFeeYear(),
                resident == null ? null : resident.getId(),
                resident == null ? null : resident.getCommunity(),
                resident == null ? null : resident.getBuildingNo(),
                resident == null ? null : resident.getUnitNo(),
                resident == null ? null : resident.getRoomNo(),
                station == null ? null : station.getCode(),
                resident == null ? null : resident.getSupplyStatus(),
                fee.getBillingArea(), fee.getUnitPrice(), fee.getReceivable(),
                fee.getPaid(), fee.getArrears(), fee.getPayStatus(),
                fee.getLastPayDate(), fee.getRemark(),
                fee.isReviewFlag(), fee.isVoided(), fee.getVoidReason());
    }

    private String fullAddress(Resident r) {
        return nvl(r.getCommunity()) + nvl(r.getBuildingNo()) + "号楼"
                + nvl(r.getUnitNo()) + "单元" + nvl(r.getRoomNo());
    }

    private String simpleAddress(Resident r) {
        return nvl(r.getBuildingNo()) + "号楼" + nvl(r.getUnitNo()) + "单元" + nvl(r.getRoomNo());
    }

    private String addressKey(Resident r) {
        return normalize(fullAddress(r));
    }

    /** 归一化：去掉所有非字母/数字/汉字字符（空白、#、- 等） */
    public static String normalizeAddress(String text) {
        return text == null ? "" : normalize(text);
    }

    private static String normalize(String text) {
        return text.replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private String stationCode(Resident r) {
        return r.getStation() == null ? "未指定换热站" : r.getStation().getCode();
    }

    private String display(String value) {
        return hasText(value) ? value : "（空）";
    }

    private BigDecimal paid(HeatingFee f) {
        return f.getPaid() == null ? BigDecimal.ZERO : f.getPaid();
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    private Long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 供 FeeService 财务复核时查询待复核行使用 */
    @Transactional(readOnly = true)
    public List<FeeVO> listReviewFees() {
        List<FeeVO> result = new ArrayList<>();
        for (HeatingFee f : feeRepository.findByReviewFlagTrueAndVoidedFalseOrderByFeeYearAscIdAsc()) {
            result.add(toFeeVo(f));
        }
        return result;
    }
}
