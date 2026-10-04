package com.heat.supply.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.heat.supply.domain.RepairOrder;

public interface RepairOrderRepository extends JpaRepository<RepairOrder, Long> {

    List<RepairOrder> findByOrderStatusOrderByReportedDateDesc(String orderStatus);

    /** 某换热站是否存在未闭环工单（停用前校验） */
    boolean existsByStationIdAndOrderStatusNot(Long stationId, String orderStatus);

    long countByOrderStatusNot(String orderStatus);

    @Query("select count(o.id) from RepairOrder o where o.station.id = :stationId and o.orderStatus <> :closed")
    long countOpenByStation(@Param("stationId") Long stationId, @Param("closed") String closed);

    /** 同一用户同一故障类型是否已有未闭环工单（重复生成校验） */
    @Query("select count(o.id) from RepairOrder o where o.resident.id = :residentId "
            + "and o.faultType = :faultType and o.orderStatus <> :closed")
    long countDuplicate(@Param("residentId") Long residentId, @Param("faultType") String faultType,
                        @Param("closed") String closed);

    List<RepairOrder> findByReportedDateBetweenOrderByReportedDateDesc(LocalDate from, LocalDate to);

    long countByReportedDate(LocalDate reportedDate);

    /** 归并：工单改挂（工单号、报修日期、处理结果、状态一律不动），批量更新只改关联外键 */
    @Modifying
    @Query("update RepairOrder o set o.resident = :keeper where o.resident = :merged")
    int reassignResident(@Param("keeper") com.heat.supply.domain.Resident keeper,
                         @Param("merged") com.heat.supply.domain.Resident merged);
}
