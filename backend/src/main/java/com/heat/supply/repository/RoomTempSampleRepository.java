package com.heat.supply.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.heat.supply.domain.RoomTempSample;
import com.heat.supply.dto.LatestTempDTO;

public interface RoomTempSampleRepository extends JpaRepository<RoomTempSample, Long> {

    List<RoomTempSample> findByResidentIdOrderBySampleDateDesc(Long residentId);

    @Query("select v.id from RoomTempSample v order by v.sampleDate desc, v.id desc")
    List<Long> findIdsOrderBySampleDateDesc();

    /**
     * 手写 JPQL：每户最近一次抽测结果（热力格子染色用）。
     */
    @Query("select new com.heat.supply.dto.LatestTempDTO("
            + "rt.id, rt.resident.id, rt.sampleDate, rt.temperature, rt.qualified, rt.householdName) "
            + "from RoomTempSample rt "
            + "where rt.sampleDate = (select max(x.sampleDate) from RoomTempSample x where x.resident.id = rt.resident.id)")
    List<LatestTempDTO> findLatestPerResident();
}
