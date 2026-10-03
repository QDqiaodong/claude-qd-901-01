package com.heat.supply.dto;

import lombok.Data;

/** 换热站状态变更（含停用前置校验） */
@Data
public class StationStatusRequest {

    /** 运行 / 检修 / 停用 */
    private String status;
}
