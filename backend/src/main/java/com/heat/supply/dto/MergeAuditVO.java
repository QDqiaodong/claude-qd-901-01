package com.heat.supply.dto;

import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;

/** 归并审计记录视图 */
@Data
@NoArgsConstructor
public class MergeAuditVO {

    private Long id;
    private String mergeNo;
    private Long keeperId;
    private Long mergedId;
    private String keeperSnapshot;
    private String mergedSnapshot;
    private String chosenSupplyStatus;
    private Integer sampleMovedCount;
    private Integer orderMovedCount;
    private Integer feeMovedCount;
    private String feeRecomputeYear;
    private String feeReviewYear;
    private String detailsJson;
    private String operator;
    private LocalDateTime createdAt;
}
