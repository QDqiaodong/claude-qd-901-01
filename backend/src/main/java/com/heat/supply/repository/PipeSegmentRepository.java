package com.heat.supply.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.heat.supply.domain.PipeSegment;

public interface PipeSegmentRepository extends JpaRepository<PipeSegment, Long> {

    @Query("select p.id from PipeSegment p order by p.diameterDn desc, p.segCode asc")
    List<Long> findIdsOrderByDn();

    @Query("select p.id from PipeSegment p where p.diameterDn = :diameterDn order by p.segCode asc")
    List<Long> findIdsByDiameterDnOrderBySegCodeAsc(@Param("diameterDn") Integer diameterDn);

    List<PipeSegment> findByDiameterDnOrderBySegCodeAsc(Integer diameterDn);

    List<PipeSegment> findBySegmentStatusOrderBySegCodeAsc(String segmentStatus);
}
