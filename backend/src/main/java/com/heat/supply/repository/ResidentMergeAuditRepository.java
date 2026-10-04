package com.heat.supply.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.heat.supply.domain.ResidentMergeAudit;

public interface ResidentMergeAuditRepository extends JpaRepository<ResidentMergeAudit, Long> {

    /** 幂等：同一对（保留档案, 重复档案）只能归并一次，重复确认直接拒绝、不搬数据 */
    Optional<ResidentMergeAudit> findByKeeperIdAndMergedId(Long keeperId, Long mergedId);

    long countByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    long countByMergeNo(String mergeNo);

    List<ResidentMergeAudit> findAllByOrderByCreatedAtDescIdDesc();
}
