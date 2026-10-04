package com.heat.supply.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.heat.supply.domain.ResidentMergeLog;

public interface ResidentMergeLogRepository extends JpaRepository<ResidentMergeLog, Long> {

    /** 幂等：同一对（保留 + 待归并）只允许一次成功归并 */
    Optional<ResidentMergeLog> findFirstByKeptResidentIdAndAwayResidentIdAndStatus(
            Long keptResidentId, Long awayResidentId, String status);

    List<ResidentMergeLog> findAllByOrderByCreatedAtDescIdDesc();

    /** 当天本事务之前最大的自增主键（用 id 拼归并单号，避免失败记录占用/撞号） */
    @org.springframework.data.jpa.repository.Query(
            value = "select coalesce(max(id), 0) from resident_merge_log where created_at >= :dayStart",
            nativeQuery = true)
    long maxIdToday(@org.springframework.data.repository.query.Param("dayStart") java.sql.Timestamp dayStart);
}
