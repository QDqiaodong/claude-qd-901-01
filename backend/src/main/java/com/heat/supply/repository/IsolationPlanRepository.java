package com.heat.supply.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.heat.supply.domain.IsolationPlan;

public interface IsolationPlanRepository extends JpaRepository<IsolationPlan, Long> {

    Optional<IsolationPlan> findByPlanNo(String planNo);

    @Query("select p.id from IsolationPlan p order by p.id desc")
    List<Long> findIdsOrderByIdDesc();

    @Query("select p.id from IsolationPlan p where p.phase in :phases order by p.id desc")
    List<Long> findIdsByPhaseInOrderByIdDesc(List<String> phases);

    long countByFaultSegmentIdAndPhaseIn(Long faultSegmentId, List<String> phases);
}
