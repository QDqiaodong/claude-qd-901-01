package com.heat.supply.dto;

import lombok.Data;

/**
 * 按地址 / 热表号 / 旧档案编号查询热用户的落点结果：
 * 命中归档档案时自动跳到保留档案，同一户的任何查询方式只能落到同一户。
 */
@Data
public class ResolveTargetVO {

    private Long residentId;
    private String legacyCode;
    private String community;
    private String buildingNo;
    private String unitNo;
    private String roomNo;
    private String addressText;
    private String meterNo;
    private String stationCode;
    private String stationName;
    private String supplyStatus;
    private boolean archived;
    /** 本次命中的是不是归档档案 */
    private boolean redirected;
    /** 从哪个旧档案编号跳过来的 */
    private String redirectedFromCode;
}
