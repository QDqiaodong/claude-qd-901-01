package com.heat.supply.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heat.supply.common.BizException;
import com.heat.supply.common.Dict;
import com.heat.supply.domain.HeatingFee;
import com.heat.supply.domain.Resident;
import com.heat.supply.domain.ResidentMergeAudit;
import com.heat.supply.dto.ArrearsRankDTO;
import com.heat.supply.dto.FeeReviewResolveRequest;
import com.heat.supply.dto.FeeVO;
import com.heat.supply.dto.PayRequest;
import com.heat.supply.repository.HeatingFeeRepository;
import com.heat.supply.repository.ResidentMergeAuditRepository;

/**
 * 模块 5：采暖费收缴。
 * 欠费排行走手写 JPQL，并缓存在 Redis（缴费、归并、财务复核后失效）。
 * 归并作废行不进台账/欠费统计；待财务复核行在复核完成前冻结该年度缴费登记。
 */
@Service
public class FeeService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final HeatingFeeRepository feeRepository;
    private final ResidentMergeAuditRepository mergeAuditRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ResidentMergeService mergeService;

    public FeeService(HeatingFeeRepository feeRepository,
                      ResidentMergeAuditRepository mergeAuditRepository,
                      StringRedisTemplate redisTemplate,
                      ObjectMapper objectMapper,
                      ResidentMergeService mergeService) {
        this.feeRepository = feeRepository;
        this.mergeAuditRepository = mergeAuditRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.mergeService = mergeService;
    }

    /**
     * 收费台账：默认只展示未作废行（归并作废行金额冻结，仅在审计里可查）。
     * includeVoided=true 时一并带出，作废行置灰只读。
     */
    @Transactional(readOnly = true)
    public List<FeeVO> listFees(Integer feeYear, boolean includeVoided) {
        List<HeatingFee> fees = feeYear == null
                ? feeRepository.findAll()
                : feeRepository.findByFeeYearOrderByArrearsDesc(feeYear);
        List<FeeVO> result = new ArrayList<>();
        for (HeatingFee fee : fees) {
            if (fee.isVoided() && !includeVoided) {
                continue;
            }
            result.add(mergeService.toFeeVo(fee));
        }
        result.sort((a, b) -> {
            int cmpVoid = Boolean.compare(a.isVoided(), b.isVoided());
            if (cmpVoid != 0) {
                return cmpVoid;
            }
            int cmp = b.getArrears().compareTo(a.getArrears());
            return cmp != 0 ? cmp : a.getId().compareTo(b.getId());
        });
        return result;
    }

    @Transactional(readOnly = true)
    public BigDecimal sumArrears() {
        return nvl(feeRepository.sumArrears());
    }

    /**
     * 手写 JPQL 欠费金额排行，结果缓存在 Redis（缴费登记 / 归并 / 财务复核后清理缓存）。
     */
    @Transactional(readOnly = true)
    public List<ArrearsRankDTO> arrearsRank() {
        String cached = redisTemplate.opsForValue().get(Dict.CACHE_ARREARS_RANK);
        if (cached != null && !cached.isBlank()) {
            try {
                return objectMapper.readValue(cached, new TypeReference<List<ArrearsRankDTO>>() {
                });
            } catch (Exception ignored) {
                // 缓存解析失败就直接查库重建
            }
        }
        List<ArrearsRankDTO> rank = feeRepository.arrearsRank();
        try {
            redisTemplate.opsForValue().set(Dict.CACHE_ARREARS_RANK, objectMapper.writeValueAsString(rank), CACHE_TTL);
        } catch (Exception ignored) {
            // 缓存写失败不影响主流程
        }
        return rank;
    }

    @Transactional(readOnly = true)
    public List<Integer> listFeeYears() {
        List<Integer> years = new ArrayList<>();
        for (HeatingFee fee : feeRepository.findAll()) {
            if (fee.isVoided()) {
                continue;
            }
            if (fee.getFeeYear() != null && !years.contains(fee.getFeeYear())) {
                years.add(fee.getFeeYear());
            }
        }
        years.sort(null);
        return years;
    }

    /** 财务复核台账：同一年度两边均有实收的待复核行（未作废），按年度归组返回 */
    @Transactional(readOnly = true)
    public List<FeeVO> listReviewFees() {
        return mergeService.listReviewFees();
    }

    /**
     * 行内登记缴费：金额不得为负、不得超过欠费金额；
     * 归并只读档案、作废行、待财务复核行一律禁止登记。
     */
    @Transactional
    public FeeVO pay(Long feeId, PayRequest request) {
        HeatingFee fee = feeRepository.findById(feeId)
                .orElseThrow(() -> new BizException("收费记录不存在：id=" + feeId));
        if (fee.isVoided()) {
            throw new BizException("该台账记录已随归并作废（" + fee.getVoidReason()
                    + "），金额已冻结，不能登记缴费");
        }
        if (fee.isReviewFlag()) {
            throw new BizException(fee.getFeeYear() + " 年度存在归并后双份实收，已标为待财务复核；"
                    + "财务复核完成前禁止继续登记该年度缴费");
        }
        BigDecimal amount = request.getAmount();
        if (amount == null) {
            throw new BizException("请填写缴费金额");
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal arrears = nvl(fee.getArrears());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("缴费金额必须大于 0，当前录入 " + amount + " 元");
        }
        if (amount.compareTo(arrears) > 0) {
            throw new BizException("缴费金额 " + amount + " 元超过该户欠费 " + arrears + " 元，请核对后再登记");
        }
        Resident resident = fee.getResident();
        if (resident.isMerged()) {
            throw new BizException("该档案已归并（归并单 " + resident.getMergeNo()
                    + "），为只读档案，请到保留档案 " + resident.getMergedInto().getId() + " 办理缴费");
        }
        if ("已报停".equals(resident.getSupplyStatus())) {
            throw new BizException("该用户处于报停状态，请先恢复供热再办理缴费");
        }

        BigDecimal paid = nvl(fee.getPaid()).add(amount);
        fee.setPaid(paid.setScale(2, RoundingMode.HALF_UP));
        BigDecimal newArrears = nvl(fee.getReceivable()).subtract(fee.getPaid());
        if (newArrears.compareTo(BigDecimal.ZERO) < 0) {
            newArrears = BigDecimal.ZERO;
        }
        fee.setArrears(newArrears.setScale(2, RoundingMode.HALF_UP));
        fee.setPayStatus(Dict.derivePayStatus(fee.getReceivable(), fee.getPaid()));
        fee.setLastPayDate(request.getPayDate() == null ? LocalDate.now() : request.getPayDate());
        HeatingFee saved = feeRepository.save(fee);
        redisTemplate.delete(Dict.CACHE_ARREARS_RANK);
        return mergeService.toFeeVo(saved);
    }

    /**
     * 财务复核完成：同年度两条台账（同挂保留档案、均 review_flag）核对后收口为一条。
     * 实收按两笔原始实收之和据实入账（不重算、不丢弃），应收以财务确认值为准；
     * 另一条作废并冻结，保留原始金额供追溯；两条均解除待复核、开放后续缴费登记。
     */
    @Transactional
    public FeeVO resolveReview(FeeReviewResolveRequest request) {
        HeatingFee primary = feeRepository.findById(request.getFeeId())
                .orElseThrow(() -> new BizException("收费记录不存在：id=" + request.getFeeId()));
        if (primary.isVoided()) {
            throw new BizException("该台账记录已作废，不能完成复核");
        }
        if (!primary.isReviewFlag()) {
            throw new BizException("该台账记录不在待财务复核状态，无需复核");
        }
        Resident keeper = primary.getResident();
        if (keeper.isMerged()) {
            throw new BizException("台账挂在已归并只读档案下，数据异常，请联系管理员");
        }

        // 同一年度、同一保留档案下的全部待复核行（归并产生两条，id 小的通常是原保留档案台账）
        Map<Integer, List<HeatingFee>> sameYear = new TreeMap<>();
        for (HeatingFee f : feeRepository.findAll()) {
            if (f.getResident() == null || !keeper.getId().equals(f.getResident().getId())) {
                continue;
            }
            if (!f.isVoided() && f.isReviewFlag() && f.getFeeYear().equals(primary.getFeeYear())) {
                sameYear.computeIfAbsent(f.getFeeYear(), y -> new ArrayList<>()).add(f);
            }
        }
        List<HeatingFee> group = sameYear.getOrDefault(primary.getFeeYear(), List.of());
        if (group.size() < 2) {
            throw new BizException(primary.getFeeYear() + " 年度待复核记录不完整，无法完成复核，请先核对归并结果");
        }
        group.sort((a, b) -> a.getId().compareTo(b.getId()));
        HeatingFee keepRow = group.get(0);
        List<HeatingFee> dupRows = group.subList(1, group.size());

        BigDecimal confirmedReceivable = request.getConfirmedReceivable() != null
                ? request.getConfirmedReceivable().setScale(2, RoundingMode.HALF_UP)
                : keepRow.getReceivable();
        if (confirmedReceivable.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("确认应收金额不能为负");
        }

        BigDecimal totalPaid = BigDecimal.ZERO;
        for (HeatingFee f : group) {
            totalPaid = totalPaid.add(nvl(f.getPaid()));
        }
        totalPaid = totalPaid.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalArrears = confirmedReceivable.subtract(totalPaid).max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        // 找到本保留档案最近一次归并单，写入作废原因
        ResidentMergeAudit latest = findLatestAudit(keeper.getId());
        String mergeNo = latest == null ? null : latest.getMergeNo();

        // 保留行：财务确认应收 + 两笔实收据实合并
        keepRow.setReceivable(confirmedReceivable);
        keepRow.setBillingArea(keeper.getAreaM2());
        keepRow.setPaid(totalPaid);
        keepRow.setArrears(totalArrears);
        keepRow.setPayStatus(Dict.derivePayStatus(confirmedReceivable, totalPaid));
        keepRow.setReviewFlag(false);
        String keepNote = request.getReviewNote();
        keepRow.setRemark(appendNote(keepRow.getRemark(), "财务复核完成：实收 " + totalPaid
                + " 元为归并两笔实收之和，应收经财务确认为 " + confirmedReceivable + " 元"
                + (hasText(keepNote) ? "（" + keepNote.trim() + "）" : "")));
        HeatingFee saved = feeRepository.save(keepRow);

        // 重复行：解除待复核并作废，金额原样冻结
        for (HeatingFee dup : dupRows) {
            dup.setReviewFlag(false);
            dup.setVoided(true);
            dup.setVoidReason("归并单 " + mergeNo + "：" + dup.getFeeYear()
                    + " 年度财务复核完成，实收 " + nvl(dup.getPaid())
                    + " 元已并入保留台账 #" + keepRow.getId() + "，本行作废冻结（"
                    + (hasText(keepNote) ? keepNote.trim() : "金额保留备查") + "）");
            feeRepository.save(dup);
        }

        redisTemplate.delete(Dict.CACHE_ARREARS_RANK);
        return mergeService.toFeeVo(saved);
    }

    private ResidentMergeAudit findLatestAudit(Long keeperId) {
        return mergeAuditRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .filter(a -> a.getKeeperId().equals(keeperId))
                .findFirst().orElse(null);
    }

    private String appendNote(String origin, String addition) {
        String text = (origin == null ? "" : origin + "；") + addition;
        return text.length() > 128 ? text.substring(0, 128) : text;
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
