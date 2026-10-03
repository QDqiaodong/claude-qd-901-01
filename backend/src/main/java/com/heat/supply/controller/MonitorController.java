package com.heat.supply.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.heat.supply.common.ApiResponse;
import com.heat.supply.common.Dict;
import com.heat.supply.dto.RepairOrderVO;
import com.heat.supply.dto.ResidentVO;
import com.heat.supply.dto.RoomTempRequest;
import com.heat.supply.dto.RoomTempVO;
import com.heat.supply.dto.StationTempStatDTO;
import com.heat.supply.dto.SupplyChangeRequest;
import com.heat.supply.dto.TempGridVO;
import com.heat.supply.service.MonitorService;

/**
 * 模块 3：热用户与室温抽测。
 * GET /api/monitor/room-temps?profile=with-resident 走 @FetchProfile JOIN 抓取关联热用户。
 */
@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    private final MonitorService monitorService;

    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    /** 室温抽测记录 */
    @GetMapping("/room-temps")
    public ApiResponse<List<RoomTempVO>> samples(@RequestParam(required = false) String profile) {
        return ApiResponse.success(monitorService.listSamples(profile));
    }

    /** 楼栋 × 单元 热力格子矩阵 */
    @GetMapping("/room-temps/grid")
    public ApiResponse<TempGridVO> grid(@RequestParam(required = false) String community) {
        return ApiResponse.success(monitorService.buildGrid(community));
    }

    /** 手写 JPQL：按换热站统计不达标室温户数 */
    @GetMapping("/room-temps/station-stat")
    public ApiResponse<List<StationTempStatDTO>> stationStat() {
        return ApiResponse.success(monitorService.stationTempStat());
    }

    /** 录入室温抽测 */
    @PostMapping("/room-temps")
    public ApiResponse<RoomTempVO> record(@RequestBody RoomTempRequest request) {
        return ApiResponse.success(monitorService.recordSample(request));
    }

    /** 不达标记录一键生成报修工单（重复生成会拦截） */
    @PostMapping("/room-temps/{id}/create-order")
    public ApiResponse<RepairOrderVO> createOrder(@PathVariable Long id) {
        return ApiResponse.success(monitorService.createOrderFromSample(id));
    }

    @GetMapping("/residents")
    public ApiResponse<List<ResidentVO>> residents(@RequestParam(required = false) Long stationId,
                                                   @RequestParam(required = false) String community) {
        return ApiResponse.success(monitorService.listResidents(stationId, community));
    }

    @GetMapping("/residents/communities")
    public ApiResponse<List<String>> communities() {
        return ApiResponse.success(monitorService.listCommunities());
    }

    /** 报停 / 恢复供热（欠费用户会被拦截） */
    @PutMapping("/residents/{id}/supply-status")
    public ApiResponse<ResidentVO> changeSupplyStatus(@PathVariable Long id,
                                                      @RequestBody SupplyChangeRequest request) {
        return ApiResponse.success(monitorService.changeSupplyStatus(id, request));
    }

    @GetMapping("/dict")
    public ApiResponse<Map<String, Object>> dict() {
        Map<String, Object> dict = new LinkedHashMap<>();
        dict.put("supplyStatus", Dict.SUPPLY_STATUS);
        dict.put("minQualifiedTemp", Dict.MIN_QUALIFIED_TEMP);
        dict.put("maxComfortTemp", Dict.MAX_COMFORT_TEMP);
        return ApiResponse.success(dict);
    }
}
