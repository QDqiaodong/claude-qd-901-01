package com.heat.supply.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.heat.supply.domain.Resident;

import jakarta.persistence.LockModeType;

public interface ResidentRepository extends JpaRepository<Resident, Long> {

    List<Resident> findByStationIdOrderByBuildingNoAscUnitNoAsc(Long stationId);

    List<Resident> findByCommunityOrderByBuildingNoAscUnitNoAscRoomNoAsc(String community);

    /** 隔离试算：按换热站统计在供（非报停、非已归并）居民户数 */
    @Query("select r.station.id, count(r.id) from Resident r "
            + "where r.supplyStatus <> '已报停' and r.merged = false group by r.station.id")
    List<Object[]> countActiveGroupByStation();

    List<Resident> findByBuildingNoAndUnitNoOrderByRoomNoAsc(String buildingNo, String unitNo);

    @Query("select distinct r.community from Resident r where r.merged = false")
    List<String> findAllCommunities();

    long countBySupplyStatus(String supplyStatus);

    /** 归并确认时对两个档案加行锁（按 id 顺序返回，避免交叉死锁） */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Resident r where r.id in :ids order by r.id asc")
    List<Resident> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);
}
