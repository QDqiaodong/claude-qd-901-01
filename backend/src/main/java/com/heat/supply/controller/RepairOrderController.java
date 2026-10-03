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
import com.heat.supply.dto.RepairOrderRequest;
import com.heat.supply.dto.RepairOrderVO;
import com.heat.supply.dto.RepairStatusRequest;
import com.heat.supply.service.RepairOrderService;

/**
 * 模块 4：报修工单看板（待派工 / 处理中 / 已闭环）。
 */
@RestController
@RequestMapping("/api/repair-orders")
public class RepairOrderController {

    private final RepairOrderService repairOrderService;

    public RepairOrderController(RepairOrderService repairOrderService) {
        this.repairOrderService = repairOrderService;
    }

    @GetMapping
    public ApiResponse<List<RepairOrderVO>> list(@RequestParam(required = false) String status) {
        return ApiResponse.success(repairOrderService.listOrders(status));
    }

    @GetMapping("/{id}")
    public ApiResponse<RepairOrderVO> detail(@PathVariable Long id) {
        return ApiResponse.success(repairOrderService.getOrder(id));
    }

    @PostMapping
    public ApiResponse<RepairOrderVO> create(@RequestBody RepairOrderRequest request) {
        return ApiResponse.success(repairOrderService.create(request));
    }

    /** 看板拖拽推进状态 */
    @PutMapping("/{id}/status")
    public ApiResponse<RepairOrderVO> updateStatus(@PathVariable Long id,
                                                   @RequestBody RepairStatusRequest request) {
        return ApiResponse.success(repairOrderService.updateStatus(id, request));
    }

    @PutMapping("/{id}/assignee")
    public ApiResponse<RepairOrderVO> assign(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResponse.success(repairOrderService.assign(id, body.get("assignee")));
    }

    @GetMapping("/dict")
    public ApiResponse<Map<String, Object>> dict() {
        Map<String, Object> dict = new LinkedHashMap<>();
        dict.put("faultTypes", Dict.FAULT_TYPES);
        dict.put("orderStatus", Dict.ORDER_STATUS);
        dict.put("orderFlow", Dict.ORDER_FLOW);
        return ApiResponse.success(dict);
    }
}
