package com.heat.supply.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.heat.supply.common.ApiResponse;
import com.heat.supply.domain.RoomTempSample;
import com.heat.supply.dto.ArrearsRankDTO;
import com.heat.supply.dto.OverviewVO;
import com.heat.supply.dto.RepairOrderVO;
import com.heat.supply.dto.ResidentVO;
import com.heat.supply.dto.RoomTempVO;
import com.heat.supply.dto.SourceLoadDTO;
import com.heat.supply.dto.StationTempStatDTO;
import com.heat.supply.dto.StationVO;
import com.heat.supply.service.FeeService;
import com.heat.supply.service.MonitorService;
import com.heat.supply.service.RepairOrderService;
import com.heat.supply.service.SourceStationService;

/**
 * 运行总览：把五个模块的关键指标汇总成一个面板数据，全部由手写 JPQL 或抓取档案查询产出。
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final SourceStationService sourceStationService;
    private final MonitorService monitorService;
    private final RepairOrderService repairOrderService;
    private final FeeService feeService;

    public StatsController(SourceStationService sourceStationService,
                           MonitorService monitorService,
                           RepairOrderService repairOrderService,
                           FeeService feeService) {
        this.sourceStationService = sourceStationService;
        this.monitorService = monitorService;
        this.repairOrderService = repairOrderService;
        this.feeService = feeService;
    }

    @GetMapping("/overview")
    public ApiResponse<OverviewVO> overview() {
        // 手写 JPQL：按热源汇总供热面积与负荷
        List<SourceLoadDTO> loads = sourceStationService.sourceLoadSummary();
        BigDecimal totalArea = BigDecimal.ZERO;
        BigDecimal totalCapacity = BigDecimal.ZERO;
        long stationCount = 0L;
        for (SourceLoadDTO dto : loads) {
            totalArea = totalArea.add(nvl(dto.getTotalAreaWanM2()));
            totalCapacity = totalCapacity.add(nvl(dto.getCapacityMw()));
            stationCount += dto.getStationCount() == null ? 0L : dto.getStationCount();
        }

        List<StationVO> stations = sourceStationService.listStations(null);
        long runningStations = stations.stream().filter(s -> "运行".equals(s.getStatus())).count();

        List<ResidentVO> residents = monitorService.listResidents(null, null);
        List<RoomTempVO> samples = monitorService.listSamples(RoomTempSample.FETCH_WITH_RESIDENT);
        long qualified = samples.stream().filter(v -> Boolean.TRUE.equals(v.getQualified())).count();
        BigDecimal unqualifiedRate = samples.isEmpty()
                ? BigDecimal.ZERO
                : BigDecimal.valueOf((samples.size() - qualified) * 100L)
                        .divide(BigDecimal.valueOf(samples.size()), 2, RoundingMode.HALF_UP);

        List<RepairOrderVO> orders = repairOrderService.listOrders(null);
        long unclosed = orders.stream().filter(o -> !"已闭环".equals(o.getOrderStatus())).count();

        OverviewVO overview = new OverviewVO(
                (long) loads.size(),
                stationCount,
                runningStations,
                (long) residents.size(),
                unclosed,
                totalArea.setScale(2, RoundingMode.HALF_UP),
                totalCapacity.setScale(2, RoundingMode.HALF_UP),
                unqualifiedRate,
                feeService.sumArrears(),
                loads);
        return ApiResponse.success(overview);
    }

    /** 手写 JPQL：不达标室温户数按换热站排名 */
    @GetMapping("/temp-risk")
    public ApiResponse<List<StationTempStatDTO>> tempRisk() {
        return ApiResponse.success(monitorService.stationTempStat());
    }

    /** 手写 JPQL：欠费金额排行（Redis 缓存版） */
    @GetMapping("/arrears-rank")
    public ApiResponse<List<ArrearsRankDTO>> arrearsRank() {
        return ApiResponse.success(feeService.arrearsRank());
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
