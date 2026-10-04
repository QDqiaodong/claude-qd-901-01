package com.heat.supply.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.heat.supply.domain.HeatingFee;
import com.heat.supply.dto.ArrearsRankDTO;

public interface HeatingFeeRepository extends JpaRepository<HeatingFee, Long> {

    List<HeatingFee> findByFeeYearOrderByArrearsDesc(Integer feeYear);

    List<HeatingFee> findByResidentIdOrderByFeeYearDesc(Long residentId);

    Optional<HeatingFee> findByResidentIdAndFeeYear(Long residentId, Integer feeYear);

    /**
     * 手写 JPQL：欠费金额排行（只取仍有欠费、未作废、未待复核的记录）。
     * 待财务复核行由财务复核台账单独处理，不进常规排行。
     */
    @Query("select new com.heat.supply.dto.ArrearsRankDTO("
            + "f.id, r.id, f.feeYear, r.community, r.buildingNo, r.unitNo, r.roomNo, st.code, "
            + "f.receivable, f.paid, f.arrears, f.payStatus) "
            + "from HeatingFee f join f.resident r left join r.station st "
            + "where f.arrears > 0 and f.voided = false and f.reviewFlag = false "
            + "order by f.arrears desc, f.id asc")
    List<ArrearsRankDTO> arrearsRank();

    @Query("select coalesce(sum(f.arrears), 0.00) from HeatingFee f "
            + "where f.voided = false and f.reviewFlag = false")
    BigDecimal sumArrears();

    @Query("select coalesce(sum(f.arrears), 0.00) from HeatingFee f where f.resident.id = :residentId "
            + "and f.voided = false and f.reviewFlag = false")
    BigDecimal sumArrearsByResident(@Param("residentId") Long residentId);

    /**
     * 归并：把重复档案名下的收费记录全部改挂到保留档案（不改业务编号/金额/时间）。
     * 调用方在批量更新前已手动 flush 字段变更，并在全部更新后 clear 重载实体。
     */
    @Modifying
    @Query("update HeatingFee f set f.resident = :keeper where f.resident = :merged")
    int reassignResident(@Param("keeper") com.heat.supply.domain.Resident keeper,
                         @Param("merged") com.heat.supply.domain.Resident merged);

    List<HeatingFee> findByResidentIdInOrderByFeeYearAscIdAsc(List<Long> residentIds);

    /** 财务复核台账：待复核行（未作废），按年度、id 排序 */
    List<HeatingFee> findByReviewFlagTrueAndVoidedFalseOrderByFeeYearAscIdAsc();
}
