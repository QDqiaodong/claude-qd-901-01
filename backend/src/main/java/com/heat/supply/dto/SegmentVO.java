package com.heat.supply.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

/** 管段视图对象 */
@Data
@NoArgsConstructor
public class SegmentVO {

    private Long id;
    private String segCode;
    private String startWellNo;
    private String endWellNo;
    private Integer diameterDn;
    private BigDecimal lengthM;
    private String layingType;
    private Integer commissionYear;
    private String insulationStatus;
    private String segmentStatus;
    private String mapPoints;
    private String remark;
    /** -1 表示所属阀门井未随本次查询预加载 */
    private Integer valveCount;
    private List<String> wellNos;
    private Boolean valvesEagerLoaded;

    public SegmentVO(Long id, String segCode, String startWellNo, String endWellNo, Integer diameterDn,
                     BigDecimal lengthM, String layingType, Integer commissionYear, String insulationStatus,
                     String segmentStatus, String mapPoints, String remark, Integer valveCount,
                     List<String> wellNos, Boolean valvesEagerLoaded) {
        this.id = id;
        this.segCode = segCode;
        this.startWellNo = startWellNo;
        this.endWellNo = endWellNo;
        this.diameterDn = diameterDn;
        this.lengthM = lengthM;
        this.layingType = layingType;
        this.commissionYear = commissionYear;
        this.insulationStatus = insulationStatus;
        this.segmentStatus = segmentStatus;
        this.mapPoints = mapPoints;
        this.remark = remark;
        this.valveCount = valveCount;
        this.wellNos = wellNos;
        this.valvesEagerLoaded = valvesEagerLoaded;
    }
}
