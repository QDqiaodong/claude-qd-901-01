package com.heat.supply.dto;

import java.time.LocalDate;

import lombok.Data;
import lombok.NoArgsConstructor;

/** 阀门井视图对象 */
@Data
@NoArgsConstructor
public class ValveWellVO {

    private Long id;
    private String wellNo;
    private String location;
    private Integer valveCaliberDn;
    private String openState;
    private String repairStatus;
    private Long segmentId;
    private String segCode;
    private String segmentStatus;
    private LocalDate lastCheckDate;
    private String remark;
    private Integer mapX;
    private Integer mapY;
    /** 现场不可操作（卡涩 / 占压 / 泄漏区），隔离试算不得列入计划关断 */
    private Boolean inoperableFlag;
    /** 人员撤离安全门：关断序列最后关闭、复供最先开启 */
    private Boolean egressGuard;
    private Boolean segmentEagerLoaded;

    public ValveWellVO(Long id, String wellNo, String location, Integer valveCaliberDn, String openState,
                       String repairStatus, Long segmentId, String segCode, String segmentStatus,
                       LocalDate lastCheckDate, String remark, Integer mapX, Integer mapY,
                       Boolean inoperableFlag, Boolean egressGuard, Boolean segmentEagerLoaded) {
        this.id = id;
        this.wellNo = wellNo;
        this.location = location;
        this.valveCaliberDn = valveCaliberDn;
        this.openState = openState;
        this.repairStatus = repairStatus;
        this.segmentId = segmentId;
        this.segCode = segCode;
        this.segmentStatus = segmentStatus;
        this.lastCheckDate = lastCheckDate;
        this.remark = remark;
        this.mapX = mapX;
        this.mapY = mapY;
        this.inoperableFlag = inoperableFlag;
        this.egressGuard = egressGuard;
        this.segmentEagerLoaded = segmentEagerLoaded;
    }
}
