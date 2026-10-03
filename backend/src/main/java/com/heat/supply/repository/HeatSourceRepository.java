package com.heat.supply.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.heat.supply.domain.HeatSource;
import com.heat.supply.dto.SourceLoadDTO;

public interface HeatSourceRepository extends JpaRepository<HeatSource, Long> {

    /** 只取主键，实体本体交给 Session#find 装载，便于 @FetchProfile 生效 */
    @Query("select s.id from HeatSource s order by s.id asc")
    List<Long> findIdsOrderById();

    /**
     * 手写 JPQL：按热源汇总装机容量、换热站数、供热面积与一次侧平均温差。
     */
    @Query("select new com.heat.supply.dto.SourceLoadDTO("
            + "s.id, s.name, s.fuelType, s.capacityMw, count(st.id), "
            + "coalesce(sum(st.areaWanM2), 0.00), coalesce(avg(st.primarySupplyTemp - st.primaryReturnTemp), 0.0)) "
            + "from HeatSource s left join s.stations st "
            + "group by s.id, s.name, s.fuelType, s.capacityMw "
            + "order by s.id asc")
    List<SourceLoadDTO> summaryBySource();

    boolean existsByName(String name);
}
