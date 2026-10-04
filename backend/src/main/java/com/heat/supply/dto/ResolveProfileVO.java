package com.heat.supply.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 按地址 / 热表号 / 旧档案编号查询的落点结果。
 * 命中已归并档案时统一跳到保留档案（redirected=true），保证三种查询只落到同一户。
 */
@Data
@NoArgsConstructor
public class ResolveProfileVO {

    /** 实际落点档案（保留档案） */
    private ResidentVO resident;

    /** 是否经归并别名跳转 */
    private boolean redirected;

    /** 命中的旧标识类型：METER_NO / OLD_ID / ADDRESS */
    private String matchedAliasType;

    /** 命中的旧标识值 */
    private String matchedAliasValue;

    /** 原属的重复档案 id */
    private Long sourceResidentId;

    /** 归并单号 */
    private String mergeNo;

    private String redirectNote;
}
