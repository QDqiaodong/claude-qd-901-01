package com.heat.supply.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.heat.supply.domain.Resident;

import jakarta.persistence.LockModeType;

public interface ResidentRepository extends JpaRepository<Resident, Long> {

    List<Resident> findByStationIdOrderByBuildingNoAscUnitNoAsc(Long stationId);

    List<Resident> findByCommunityOrderByBuildingNoAscUnitNoAscRoomNoAsc(String community);

    /** 隔离试算：按换热站统计在供（非报停、未归档）居民户数 */
    @Query("select r.station.id, count(r.id) from Resident r "
            + "where r.supplyStatus <> '已报停' and r.archived = false group by r.station.id")
    List<Object[]> countActiveGroupByStation();

    List<Resident> findByBuildingNoAndUnitNoOrderByRoomNoAsc(String buildingNo, String unitNo);

    @Query("select distinct r.community from Resident r where r.archived = false")
    List<String> findAllCommunities();

    long countBySupplyStatusAndArchivedFalse(String supplyStatus);

    /** 归并确认：行级上锁，避免两个归并并发处理同一档案（配合幂等判断） */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Resident r where r.id = :id")
    Optional<Resident> findByIdForUpdate(@Param("id") Long id);

    /** 候选档案：同地址四要素的未归档档案（不含自身） */
    @Query("select r from Resident r where r.archived = false and r.id <> :selfId "
            + "and r.community = :community and r.buildingNo = :buildingNo "
            + "and r.unitNo = :unitNo and r.roomNo = :roomNo order by r.id")
    List<Resident> findSameAddressActive(@Param("selfId") Long selfId,
                                         @Param("community") String community,
                                         @Param("buildingNo") String buildingNo,
                                         @Param("unitNo") String unitNo,
                                         @Param("roomNo") String roomNo);

    /** 按热表号查询：归档档案也要查到，由服务端统一跳到保留档案 */
    List<Resident> findByMeterNoOrderByArchivedAscIdAsc(String meterNo);

    /** 按旧档案编号查询 */
    Optional<Resident> findByLegacyCode(String legacyCode);

    /** 同地址四要素的所有档案（含归档，用于地址检索落点） */
    @Query("select r from Resident r where r.community = :community and r.buildingNo = :buildingNo "
            + "and r.unitNo = :unitNo and r.roomNo = :roomNo order by r.archived asc, r.id asc")
    List<Resident> findByAddressAll(@Param("community") String community,
                                    @Param("buildingNo") String buildingNo,
                                    @Param("unitNo") String unitNo,
                                    @Param("roomNo") String roomNo);
}
