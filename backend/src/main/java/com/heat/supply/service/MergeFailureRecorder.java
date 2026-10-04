package com.heat.supply.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.heat.supply.common.Dict;
import com.heat.supply.domain.ResidentMergeLog;
import com.heat.supply.repository.ResidentMergeLogRepository;
import com.heat.supply.repository.ResidentRepository;

/**
 * 归并失败记录器：用 REQUIRES_NEW 独立事务写「失败回退」审计。
 * 主事务回滚后（引用迁移/冲突处理/审计任一失败），失败原因仍有据可查，
 * 且绝不影响主事务的回滚结果——刷新后数据保持归并前状态。
 */
@Component
public class MergeFailureRecorder {

    private final ResidentMergeLogRepository mergeLogRepository;
    private final ResidentRepository residentRepository;

    public MergeFailureRecorder(ResidentMergeLogRepository mergeLogRepository,
                                ResidentRepository residentRepository) {
        this.mergeLogRepository = mergeLogRepository;
        this.residentRepository = residentRepository;
    }

    /**
     * @param mergeNo  归并单号
     * @param keptId   保留档案 id（可能为 null）
     * @param awayId   待归并档案 id（可能为 null）
     * @param reason   失败原因
     * @param operator 经办人
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String mergeNo, Long keptId, Long awayId, String reason, String operator) {
        try {
            ResidentMergeLog log = new ResidentMergeLog();
            log.setMergeNo(mergeNo);
            // 用 getReference 只取代理（不查库），即使主事务已回滚也能落 FK
            if (keptId != null) {
                log.setKeptResident(residentRepository.getReferenceById(keptId));
            }
            if (awayId != null) {
                log.setAwayResident(residentRepository.getReferenceById(awayId));
            }
            log.setStatus(Dict.MERGE_STATUS_FAILED);
            String safeReason = reason == null ? "未知原因" : reason;
            log.setFailReason(safeReason.length() > 255 ? safeReason.substring(0, 255) : safeReason);
            log.setOperator(operator);
            log.setCreatedAt(LocalDateTime.now());
            mergeLogRepository.save(log);
        } catch (Exception ignored) {
            // 失败审计写不进去也不能掩盖原始异常，主事务照常回滚
        }
    }
}
