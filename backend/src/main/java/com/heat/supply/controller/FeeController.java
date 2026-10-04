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
import com.heat.supply.dto.FeeReviewResolveRequest;
import com.heat.supply.dto.FeeVO;
import com.heat.supply.dto.PayRequest;
import com.heat.supply.service.FeeService;

import jakarta.validation.Valid;

/**
 * 模块 5：采暖费收缴台账（欠费降序排序 + 行内登记缴费 + 归并待财务复核）。
 */
@RestController
@RequestMapping("/api/fees")
public class FeeController {

    private final FeeService feeService;

    public FeeController(FeeService feeService) {
        this.feeService = feeService;
    }

    /** 收费台账：默认隐藏归并作废行，includeVoided=true 可一并查看（置灰只读） */
    @GetMapping
    public ApiResponse<List<FeeVO>> list(@RequestParam(required = false) Integer year,
                                         @RequestParam(required = false, defaultValue = "false")
                                         boolean includeVoided) {
        return ApiResponse.success(feeService.listFees(year, includeVoided));
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

    /** 行内登记缴费（超额 / 负数 / 待复核 / 已作废 / 只读归并档案都会报错） */
    @PostMapping("/{id}/pay")
    public ApiResponse<FeeVO> pay(@PathVariable Long id, @RequestBody PayRequest request) {
        return ApiResponse.success(feeService.pay(id, request));
    }

    /** 财务复核台账：归并后同年度双份实收、待财务复核的台账行 */
    @GetMapping("/reviews")
    public ApiResponse<List<FeeVO>> reviews() {
        return ApiResponse.success(feeService.listReviewFees());
    }

    /** 财务复核完成：两条台账收口为一条，重复行作废冻结，随后恢复该年度缴费登记 */
    @PostMapping("/reviews/resolve")
    public ApiResponse<FeeVO> resolveReview(@Valid @RequestBody FeeReviewResolveRequest request) {
        return ApiResponse.success("财务复核已完成，重复台账已作废冻结，该年度恢复缴费登记",
                feeService.resolveReview(request));
    }

    @GetMapping("/dict")
    public ApiResponse<Map<String, Object>> dict() {
        Map<String, Object> dict = new LinkedHashMap<>();
        dict.put("payStatus", Dict.PAY_STATUS);
        dict.put("unitPriceByFuel", Dict.unitPriceOf("燃煤"));
        return ApiResponse.success(dict);
    }
}
