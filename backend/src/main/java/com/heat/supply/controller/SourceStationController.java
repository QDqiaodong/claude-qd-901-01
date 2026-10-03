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
import com.heat.supply.dto.FetchProfileVO;
import com.heat.supply.dto.SourceLoadDTO;
import com.heat.supply.dto.SourceVO;
import com.heat.supply.dto.StationRuntimeRequest;
import com.heat.supply.dto.StationStatusRequest;
import com.heat.supply.dto.StationVO;
import com.heat.supply.service.SourceStationService;

/**
 * 模块 1：热源厂与换热站。
 * GET /api/sources 与 GET /api/sources/profile-compare 体现 @FetchProfile 启用前后的差异。
 */
@RestController
@RequestMapping("/api/sources")
public class SourceStationController {

    private final SourceStationService sourceStationService;

    public SourceStationController(SourceStationService sourceStationService) {
        this.sourceStationService = sourceStationService;
    }

    /**
     * 热源厂查询。带上 ?profile=with-stations 启用命名抓取档案，把换热站集合改成 JOIN 抓取。
     */
    @GetMapping
    public ApiResponse<FetchProfileVO> list(@RequestParam(required = false) String profile) {
        return ApiResponse.success(sourceStationService.loadSources(profile));
    }

    /** 一对对照接口：不启用档案 vs 启用 with-stations */
    @GetMapping("/profile-compare")
    public ApiResponse<List<FetchProfileVO>> compare() {
        return ApiResponse.success(sourceStationService.compareFetchProfiles());
    }

    @GetMapping("/list")
    public ApiResponse<List<SourceVO>> listAll() {
        return ApiResponse.success(sourceStationService.listSources());
    }

    @GetMapping("/{id}")
    public ApiResponse<SourceVO> detail(@PathVariable Long id) {
        return ApiResponse.success(sourceStationService.getSource(id));
    }

    /** 手写 JPQL：按热源汇总装机容量、换热站数、供热面积与一次侧平均温差 */
    @GetMapping("/load-summary")
    public ApiResponse<List<SourceLoadDTO>> loadSummary() {
        return ApiResponse.success(sourceStationService.sourceLoadSummary());
    }

    @GetMapping("/dict")
    public ApiResponse<Map<String, Object>> dict() {
        Map<String, Object> dict = new LinkedHashMap<>();
        dict.put("fuelTypes", Dict.FUEL_TYPES);
        dict.put("sourceStatus", Dict.SOURCE_STATUS);
        dict.put("stationStatus", Dict.STATION_STATUS);
        dict.put("minQualifiedTemp", Dict.MIN_QUALIFIED_TEMP);
        dict.put("maxComfortTemp", Dict.MAX_COMFORT_TEMP);
        dict.put("designDeltaT", Dict.DESIGN_DELTA_T);
        return ApiResponse.success(dict);
    }

    /** 换热站清单 */
    @GetMapping("/stations")
    public ApiResponse<List<StationVO>> stations(@RequestParam(required = false) Long sourceId) {
        return ApiResponse.success(sourceStationService.listStations(sourceId));
    }

    @GetMapping("/stations/{id}")
    public ApiResponse<StationVO> station(@PathVariable Long id) {
        return ApiResponse.success(sourceStationService.getStation(id));
    }

    /** 登记一次/二次侧温度与循环泵状态 */
    @PutMapping("/stations/{id}/runtime")
    public ApiResponse<StationVO> registerRuntime(@PathVariable Long id,
                                                  @RequestBody StationRuntimeRequest request) {
        return ApiResponse.success(sourceStationService.registerRuntime(id, request));
    }

    /** 换热站状态变更（停用前校验是否存在未闭环报修工单） */
    @PutMapping("/stations/{id}/status")
    public ApiResponse<StationVO> changeStatus(@PathVariable Long id,
                                               @RequestBody StationStatusRequest request) {
        return ApiResponse.success(sourceStationService.changeStatus(id, request));
    }
}
