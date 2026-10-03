package com.heat.supply.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.heat.supply.common.ApiResponse;
import com.heat.supply.common.Dict;
import com.heat.supply.dto.IsolationConfirmRequest;
import com.heat.supply.dto.IsolationFinishRequest;
import com.heat.supply.dto.IsolationPlanVO;
import com.heat.supply.dto.IsolationTrialRequest;
import com.heat.supply.service.isolation.IsolationService;

/**
 * 管网隔离方案闭环：
 * 试算（含人工调整、现场不可操作、扩大隔离/拒绝启用）→ 激活 → 现场逐步确认 →
 * 失败收口（扩大隔离/恢复已动阀/保持现场）→ 复供顺序重生成与确认。
 */
@RestController
@RequestMapping("/api/isolation")
public class IsolationController {

    private final IsolationService isolationService;

    public IsolationController(IsolationService isolationService) {
        this.isolationService = isolationService;
    }

    /** 拓扑试算：选故障管段后生成关断序列、保供路径、受影响换热站与取舍选项 */
    @PostMapping("/trial")
    public ApiResponse<IsolationPlanVO> trial(@RequestBody IsolationTrialRequest request) {
        return ApiResponse.success(isolationService.trial(request));
    }

    /** 方案清单（active=只看未闭环） */
    @GetMapping("/plans")
    public ApiResponse<List<IsolationPlanVO>> plans(@RequestParam(required = false) String active) {
        return ApiResponse.success(isolationService.listPlans(active));
    }

    /** 方案详情：方案状态、阀门实况、受影响换热站同屏一致 */
    @GetMapping("/plans/{id}")
    public ApiResponse<IsolationPlanVO> plan(@PathVariable Long id) {
        return ApiResponse.success(isolationService.getPlan(id));
    }

    /** 激活启用：证明未通过或拒绝启用的方案一律拦截 */
    @PostMapping("/plans/{id}/activate")
    public ApiResponse<IsolationPlanVO> activate(@PathVariable Long id) {
        return ApiResponse.success(isolationService.activate(id));
    }

    /** 开始现场关断 */
    @PostMapping("/plans/{id}/start")
    public ApiResponse<IsolationPlanVO> start(@PathVariable Long id) {
        return ApiResponse.success(isolationService.startExecution(id));
    }

    /** 现场逐步确认隔离步骤（前一步未完成 / 反馈不符 / 阀门被他人改动 → 后续暂停） */
    @PostMapping("/plans/{id}/steps/{stepId}/confirm")
    public ApiResponse<IsolationPlanVO> confirm(@PathVariable Long id,
                                                @PathVariable Long stepId,
                                                @RequestBody IsolationConfirmRequest request) {
        return ApiResponse.success(isolationService.confirmStep(id, stepId, request));
    }

    /** 中途失败收口：EXPAND 扩大隔离 / REVERT 安全恢复已动阀 / HOLD 保持现场 */
    @PostMapping("/plans/{id}/finish-failure")
    public ApiResponse<IsolationPlanVO> finishFailure(@PathVariable Long id,
                                                      @RequestBody IsolationFinishRequest request) {
        return ApiResponse.success(isolationService.finishFailure(id, request));
    }

    /** 故障结束：按当前阀门状态重新生成复供顺序 */
    @PostMapping("/plans/{id}/restore/plan")
    public ApiResponse<IsolationPlanVO> planRestore(@PathVariable Long id) {
        return ApiResponse.success(isolationService.planRestore(id));
    }

    /** 确认复供步骤（仍不可达换热站留在异常清单） */
    @PostMapping("/plans/{id}/restore/steps/{stepId}/confirm")
    public ApiResponse<IsolationPlanVO> confirmRestore(@PathVariable Long id,
                                                       @PathVariable Long stepId,
                                                       @RequestBody IsolationConfirmRequest request) {
        return ApiResponse.success(isolationService.confirmRestoreStep(id, stepId, request));
    }

    /** 选择故障管段页用的简要拓扑 */
    @GetMapping("/topology")
    public ApiResponse<Map<String, Object>> topology() {
        return ApiResponse.success(isolationService.topologySnapshot());
    }

    @GetMapping("/dict")
    public ApiResponse<Map<String, Object>> dict() {
        Map<String, Object> dict = new LinkedHashMap<>();
        dict.put("phases", Dict.ISO_PHASES);
        dict.put("strategies", Dict.ISO_STRATEGIES);
        dict.put("actionKinds", Dict.ISO_ACTION_KINDS);
        dict.put("stepStatus", Dict.ISO_STEP_STATUS);
        return ApiResponse.success(dict);
    }
}
