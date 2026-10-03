package com.heat.supply.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.heat.supply.domain.HeatingFee;
import com.heat.supply.dto.ArrearsRankDTO;

public interface HeatingFeeRepository extends JpaRepository<HeatingFee, Long> {

    List<HeatingFee> findByFeeYearOrderByArrearsDesc(Integer feeYear);

    List<HeatingFee> findByResidentIdOrderByFeeYearDesc(Long residentId);

    Optional<HeatingFee> findByResidentIdAndFeeYear(Long residentId, Integer feeYear);

    /**
     * 手写 JPQL：欠费金额排行（只取仍有欠费的记录）。
     */
    @Query("select new com.heat.supply.dto.ArrearsRankDTO("
            + "f.id, r.id, f.feeYear, r.community, r.buildingNo, r.unitNo, r.roomNo, st.code, "
            + "f.receivable, f.paid, f.arrears, f.payStatus) "
            + "from HeatingFee f join f.resident r left join r.station st "
            + "where f.arrears > 0 "
            + "order by f.arrears desc, f.id asc")
    List<ArrearsRankDTO> arrearsRank();

    @Query("select coalesce(sum(f.arrears), 0.00) from HeatingFee f")
    BigDecimal sumArrears();

    @Query("select coalesce(sum(f.arrears), 0.00) from HeatingFee f where f.resident.id = :residentId")
    BigDecimal sumArrearsByResident(@Param("residentId") Long residentId);
}
