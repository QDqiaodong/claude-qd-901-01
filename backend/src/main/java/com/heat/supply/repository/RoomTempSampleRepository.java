package com.heat.supply.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.heat.supply.domain.RoomTempSample;
import com.heat.supply.dto.LatestTempDTO;

public interface RoomTempSampleRepository extends JpaRepository<RoomTempSample, Long> {

    List<RoomTempSample> findByResidentIdOrderBySampleDateDesc(Long residentId);

    @Query("select v.id from RoomTempSample v order by v.sampleDate desc, v.id desc")
    List<Long> findIdsOrderBySampleDateDesc();

    /**
     * 手写 JPQL：每户最近一次抽测结果（热力格子染色用，已归并只读档案不再单独成色）。
     */
    @Query("select new com.heat.supply.dto.LatestTempDTO("
            + "rt.id, rt.resident.id, rt.sampleDate, rt.temperature, rt.qualified, rt.householdName) "
            + "from RoomTempSample rt join rt.resident r "
            + "where r.merged = false and rt.sampleDate = ("
            + "select max(x.sampleDate) from RoomTempSample x where x.resident.id = rt.resident.id)")
    List<LatestTempDTO> findLatestPerResident();

    /** 归并：抽测记录改挂（抽测日期、室温、结论等一律不动），批量更新只改关联外键 */
    @Modifying
    @Query("update RoomTempSample s set s.resident = :keeper where s.resident = :merged")
    int reassignResident(@Param("keeper") com.heat.supply.domain.Resident keeper,
                         @Param("merged") com.heat.supply.domain.Resident merged);

    long countByResidentId(Long residentId);
}
