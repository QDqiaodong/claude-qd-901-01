package com.heat.supply.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.heat.supply.domain.Resident;

public interface ResidentRepository extends JpaRepository<Resident, Long> {

    List<Resident> findByStationIdOrderByBuildingNoAscUnitNoAsc(Long stationId);

    List<Resident> findByCommunityOrderByBuildingNoAscUnitNoAscRoomNoAsc(String community);

    /** 隔离试算：按换热站统计在供（非报停）居民户数 */
    @Query("select r.station.id, count(r.id) from Resident r "
            + "where r.supplyStatus <> '已报停' group by r.station.id")
    List<Object[]> countActiveGroupByStation();

    List<Resident> findByBuildingNoAndUnitNoOrderByRoomNoAsc(String buildingNo, String unitNo);

    @Query("select distinct r.community from Resident r")
    List<String> findAllCommunities();

    long countBySupplyStatus(String supplyStatus);
}
