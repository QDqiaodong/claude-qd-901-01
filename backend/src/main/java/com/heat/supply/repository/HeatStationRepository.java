package com.heat.supply.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.heat.supply.domain.HeatStation;
import com.heat.supply.dto.StationTempStatDTO;

public interface HeatStationRepository extends JpaRepository<HeatStation, Long> {

    Optional<HeatStation> findByCode(String code);

    List<HeatStation> findByHeatSourceIdOrderByCodeAsc(Long heatSourceId);

    @Query("select st.id from HeatStation st order by st.code asc")
    List<Long> findIdsOrderByCode();

    long countByStatus(String status);

    long countByHeatSourceId(Long heatSourceId);

    /**
     * 手写 JPQL：按换热站统计室温抽测户数与不达标次数（不达标阈值来自 Dict）。
     */
    @Query("select new com.heat.supply.dto.StationTempStatDTO("
            + "st.id, st.code, st.name, count(rt.id), "
            + "sum(case when rt.id is null then 0 when rt.temperature < :threshold then 1 else 0 end), "
            + "count(distinct r.id)) "
            + "from HeatStation st left join st.residents r left join r.samples rt "
            + "group by st.id, st.code, st.name "
            + "order by st.code asc")
    List<StationTempStatDTO> tempStatGroupByStation(@Param("threshold") BigDecimal threshold);
}
