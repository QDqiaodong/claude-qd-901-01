package com.heat.supply.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.heat.supply.common.ApiResponse;
import com.heat.supply.dto.MergeLogVO;
import com.heat.supply.dto.MergePrecheckVO;
import com.heat.supply.dto.MergeResultVO;
import com.heat.supply.dto.ResidentMergeRequest;
import com.heat.supply.dto.ResidentVO;
import com.heat.supply.dto.ResolveTargetVO;
import com.heat.supply.service.ResidentMergeService;

/**
 * 重复热用户档案归并：预检 → 选择保留/待归并档案 → 地址确认 + 供热状态裁决 → 整事务归并。
 * 按地址 / 热表号 / 旧档案编号查询都只能落到同一户（归档档案自动跳到保留档案）。
 */
@RestController
@RequestMapping("/api/monitor/residents")
public class ResidentMergeController {

    private final ResidentMergeService mergeService;

    public ResidentMergeController(ResidentMergeService mergeService) {
        this.mergeService = mergeService;
    }

    /** 归并预检：地址/热表号/换热站/供热状态 + 双方抽测/报修/各年度采暖费一次看清 */
    @PostMapping("/merge/precheck")
    public ApiResponse<MergePrecheckVO> precheck(@RequestParam Long keptId, @RequestParam Long awayId) {
        return ApiResponse.success(mergeService.precheck(keptId, awayId));
    }

    /** 确认归并（幂等：已成功的归并重复提交只回传原结果，不再搬移数据、不重算金额） */
    @PostMapping("/merge/confirm")
    public ApiResponse<MergeResultVO> confirm(@RequestBody ResidentMergeRequest request) {
        MergeResultVO result = mergeService.confirmMerge(request);
        String message = result.isIdempotent()
                ? "该归并已成功过，本次为幂等返回，未再次搬移数据或重算金额（归并单 " + result.getMergeNo() + "）"
                : "归并完成（归并单 " + result.getMergeNo() + "），抽测、报修、收费记录已全部改挂保留档案";
        return ApiResponse.success(message, result);
    }

    /** 同地址候选档案（选择待归并档案） */
    @GetMapping("/merge/candidates")
    public ApiResponse<List<ResidentVO>> candidates(@RequestParam Long residentId) {
        return ApiResponse.success(mergeService.listCandidates(residentId));
    }

    /** 归并审计记录（含失败回退记录） */
    @GetMapping("/merge/logs")
    public ApiResponse<List<MergeLogVO>> logs() {
        return ApiResponse.success(mergeService.listLogs());
    }

    /**
     * 统一落点查询：按地址 / 热表号 / 旧档案编号查热用户，只能落到同一户。
     * type=meter：key=热表号；type=legacy：key=旧档案编号（R17 或 r017）；
     * type=address：需传 community/buildingNo/unitNo/roomNo。
     */
    @GetMapping("/resolve")
    public ApiResponse<ResolveTargetVO> resolve(@RequestParam String type,
                                                @RequestParam(required = false) String key,
                                                @RequestParam(required = false) String community,
                                                @RequestParam(required = false) String buildingNo,
                                                @RequestParam(required = false) String unitNo,
                                                @RequestParam(required = false) String roomNo) {
        ResolveTargetVO target;
        switch (type == null ? "" : type.trim()) {
            case "meter" -> target = mergeService.resolveByMeterNo(key);
            case "legacy" -> target = mergeService.resolveByLegacyCode(key);
            case "address" -> target = mergeService.resolveByAddress(community, buildingNo, unitNo, roomNo);
            default -> {
                return ApiResponse.fail("查询类型只能是：meter / legacy / address");
            }
        }
        return ApiResponse.success(target);
    }
}
