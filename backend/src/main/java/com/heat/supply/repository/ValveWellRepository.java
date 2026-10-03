package com.heat.supply.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.heat.supply.domain.ValveWell;

public interface ValveWellRepository extends JpaRepository<ValveWell, Long> {

    Optional<ValveWell> findByWellNo(String wellNo);

    List<ValveWell> findBySegmentIdOrderByWellNoAsc(Long segmentId);

    @Query("select v.id from ValveWell v order by v.wellNo asc")
    List<Long> findIdsOrderByWellNo();

    @Query("select v.id from ValveWell v where v.segment.id = :segmentId order by v.wellNo asc")
    List<Long> findIdsBySegmentIdOrderByWellNoAsc(@Param("segmentId") Long segmentId);
}
