package com.heat.supply.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.heat.supply.domain.IsolationPlan;
import com.heat.supply.domain.IsolationStep;

public interface IsolationStepRepository extends JpaRepository<IsolationStep, Long> {

    List<IsolationStep> findByPlanAndStageOrderByStepNoAsc(IsolationPlan plan, String stage);

    List<IsolationStep> findByPlanOrderByStageAscStepNoAsc(IsolationPlan plan);

    @Modifying
    @Query("delete from IsolationStep s where s.plan = :plan")
    void deleteByPlan(@Param("plan") IsolationPlan plan);

    @Modifying
    @Query("delete from IsolationStep s where s.plan = :plan and s.stage = :stage")
    void deleteByPlanAndStage(@Param("plan") IsolationPlan plan, @Param("stage") String stage);
}
