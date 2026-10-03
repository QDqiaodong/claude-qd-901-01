package com.heat.supply.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.heat.supply.common.ApiResponse;
import com.heat.supply.common.Dict;
import com.heat.supply.dto.SegmentVO;
import com.heat.supply.dto.ValveWellUpdateRequest;
import com.heat.supply.dto.ValveWellVO;
import com.heat.supply.service.PipeNetworkService;

/**
 * 模块 2：管网段与阀门井。
 * /api/pipe/segments?profile=with-valves 与 /api/pipe/valve-wells?profile=with-segment 走 @FetchProfile。
 */
@RestController
@RequestMapping("/api/pipe")
public class PipeNetworkController {

    private final PipeNetworkService pipeNetworkService;

    public PipeNetworkController(PipeNetworkService pipeNetworkService) {
        this.pipeNetworkService = pipeNetworkService;
    }

    /** 管段清单，可按管径筛选；profile=with-valves 时 JOIN 抓取阀门井集合 */
    @GetMapping("/segments")
    public ApiResponse<List<SegmentVO>> segments(@RequestParam(required = false) Integer dn,
                                                 @RequestParam(required = false) String profile) {
        return ApiResponse.success(pipeNetworkService.listSegments(dn, profile));
    }

    /** 顶部管径筛选条选项 */
    @GetMapping("/segments/diameters")
    public ApiResponse<List<Integer>> diameters() {
        return ApiResponse.success(pipeNetworkService.listDiameters());
    }

    /** 管段状态变更 */
    @PutMapping("/segments/{id}/status")
    public ApiResponse<SegmentVO> changeSegmentStatus(@PathVariable Long id,
                                                      @RequestBody Map<String, String> body) {
        return ApiResponse.success(pipeNetworkService.changeSegmentStatus(id, body.get("status")));
    }

    /** 阀门井清单；profile=with-segment 时 JOIN 抓取所属管段 */
    @GetMapping("/valve-wells")
    public ApiResponse<List<ValveWellVO>> valveWells(@RequestParam(required = false) Long segmentId,
                                                     @RequestParam(required = false) String profile) {
        return ApiResponse.success(pipeNetworkService.listValveWells(segmentId, profile));
    }

    /** 修改阀门开关 / 检修状态（管段检修时禁止改为全开） */
    @PutMapping("/valve-wells/{id}")
    public ApiResponse<ValveWellVO> updateValveWell(@PathVariable Long id,
                                                    @RequestBody ValveWellUpdateRequest request) {
        return ApiResponse.success(pipeNetworkService.updateValveWell(id, request));
    }

    @GetMapping("/dict")
    public ApiResponse<Map<String, Object>> dict() {
        Map<String, Object> dict = new LinkedHashMap<>();
        dict.put("layingTypes", Dict.LAYING_TYPES);
        dict.put("insulationStatus", Dict.INSULATION_STATUS);
        dict.put("segmentStatus", Dict.SEGMENT_STATUS);
        dict.put("valveOpenState", Dict.VALVE_OPEN_STATE);
        dict.put("valveRepairStatus", Dict.VALVE_REPAIR_STATUS);
        return ApiResponse.success(dict);
    }
}
