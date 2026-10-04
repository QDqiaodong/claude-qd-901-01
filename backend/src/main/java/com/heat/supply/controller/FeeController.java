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
import com.heat.supply.dto.ArrearsRankDTO;
import com.heat.supply.dto.FeeReviewRequest;
import com.heat.supply.dto.FeeVO;
import com.heat.supply.dto.PayRequest;
import com.heat.supply.service.FeeService;

/**
 * 模块 5：采暖费收缴台账（欠费降序排序 + 行内登记缴费）。
 */
@RestController
@RequestMapping("/api/fees")
public class FeeController {

    private final FeeService feeService;

    public FeeController(FeeService feeService) {
        this.feeService = feeService;
    }

    @GetMapping
    public ApiResponse<List<FeeVO>> list(@RequestParam(required = false) Integer year) {
        return ApiResponse.success(feeService.listFees(year));
    }

    /** 手写 JPQL + Redis 缓存：欠费金额排行 */
    @GetMapping("/arrears-rank")
    public ApiResponse<List<ArrearsRankDTO>> arrearsRank() {
        return ApiResponse.success(feeService.arrearsRank());
    }

    @GetMapping("/years")
    public ApiResponse<List<Integer>> years() {
        return ApiResponse.success(feeService.listFeeYears());
    }

    @GetMapping("/total-arrears")
    public ApiResponse<Map<String, Object>> totalArrears() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalArrears", feeService.sumArrears());
        return ApiResponse.success(data);
    }

    /** 行内登记缴费（超额 / 负数 / 待复核年度都会被拒绝） */
    @PostMapping("/{id}/pay")
    public ApiResponse<FeeVO> pay(@PathVariable Long id, @RequestBody PayRequest request) {
        return ApiResponse.success(feeService.pay(id, request));
    }

    /** 待财务复核台账清单（归并产生，复核前该年度禁止登记缴费） */
    @GetMapping("/pending-review")
    public ApiResponse<List<FeeVO>> pendingReview() {
        return ApiResponse.success(feeService.listPendingReview());
    }

    /** 财务复核：同年度待复核台账成组放行，金额一律保留不动 */
    @PostMapping("/{id}/review")
    public ApiResponse<List<FeeVO>> review(@PathVariable Long id, @RequestBody(required = false) FeeReviewRequest request) {
        String note = request == null ? null : request.getNote();
        return ApiResponse.success("财务复核完成，原始金额保留，可继续登记该年度缴费", feeService.review(id, note));
    }

    @GetMapping("/dict")
    public ApiResponse<Map<String, Object>> dict() {
        Map<String, Object> dict = new LinkedHashMap<>();
        dict.put("payStatus", Dict.PAY_STATUS);
        dict.put("unitPriceByFuel", Dict.unitPriceOf("燃煤"));
        return ApiResponse.success(dict);
    }
}
