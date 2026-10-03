package com.heat.supply.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heat.supply.common.BizException;
import com.heat.supply.common.Dict;
import com.heat.supply.domain.HeatingFee;
import com.heat.supply.domain.Resident;
import com.heat.supply.dto.ArrearsRankDTO;
import com.heat.supply.dto.FeeVO;
import com.heat.supply.dto.PayRequest;
import com.heat.supply.repository.HeatingFeeRepository;

/**
 * 模块 5：采暖费收缴。
 * 欠费排行走手写 JPQL，并缓存在 Redis（缴费后失效）。
 */
@Service
public class FeeService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final HeatingFeeRepository feeRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public FeeService(HeatingFeeRepository feeRepository,
                      StringRedisTemplate redisTemplate,
                      ObjectMapper objectMapper) {
        this.feeRepository = feeRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /** 收费台账，按欠费金额降序 */
    @Transactional(readOnly = true)
    public List<FeeVO> listFees(Integer feeYear) {
        List<HeatingFee> fees = feeYear == null
                ? feeRepository.findAll()
                : feeRepository.findByFeeYearOrderByArrearsDesc(feeYear);
        List<FeeVO> result = new ArrayList<>();
        for (HeatingFee fee : fees) {
            result.add(toVo(fee));
        }
        result.sort((a, b) -> {
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
     * 手写 JPQL 欠费金额排行，结果缓存在 Redis（缴费登记后清理缓存）。
     */
    @Transactional(readOnly = true)
    public List<ArrearsRankDTO> arrearsRank() {
        String cached = redisTemplate.opsForValue().get(Dict.CACHE_ARREARS_RANK);
        if (cached != null && !cached.isBlank()) {
            try {
                List<ArrearsRankDTO> dtoList = objectMapper.readValue(cached, new TypeReference<List<ArrearsRankDTO>>() {
                });
                return dtoList;
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
            if (fee.getFeeYear() != null && !years.contains(fee.getFeeYear())) {
                years.add(fee.getFeeYear());
            }
        }
        years.sort(null);
        return years;
    }

    /**
     * 行内登记缴费：金额不得为负、不得超过欠费金额。
     */
    @Transactional
    public FeeVO pay(Long feeId, PayRequest request) {
        HeatingFee fee = feeRepository.findById(feeId)
                .orElseThrow(() -> new BizException("收费记录不存在：id=" + feeId));
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
        if ("已报停".equals(fee.getResident().getSupplyStatus())) {
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
        return toVo(saved);
    }

    private FeeVO toVo(HeatingFee fee) {
        Resident resident = fee.getResident();
        return new FeeVO(
                fee.getId(),
                fee.getFeeYear(),
                resident == null ? null : resident.getId(),
                resident == null ? null : resident.getCommunity(),
                resident == null ? null : resident.getBuildingNo(),
                resident == null ? null : resident.getUnitNo(),
                resident == null ? null : resident.getRoomNo(),
                resident == null || resident.getStation() == null ? null : resident.getStation().getCode(),
                resident == null ? null : resident.getSupplyStatus(),
                fee.getBillingArea(),
                fee.getUnitPrice(),
                fee.getReceivable(),
                fee.getPaid(),
                fee.getArrears(),
                fee.getPayStatus(),
                fee.getLastPayDate(),
                fee.getRemark());
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
