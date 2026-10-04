package com.heat.supply.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.heat.supply.common.ApiResponse;
import com.heat.supply.dto.MergeAuditVO;
import com.heat.supply.dto.MergeCandidateVO;
import com.heat.supply.dto.MergeConfirmRequest;
import com.heat.supply.dto.MergePreviewVO;
import com.heat.supply.dto.ResolveProfileVO;
import com.heat.supply.service.ResidentMergeService;

import jakarta.validation.Valid;

/**
 * 模块 6：重复热用户档案归并。
 * 预检（只读）→ 状态不一致时明确供热状态 → 确认（单事务迁移引用 + 处置收费 + 落别名 + 写审计）。
 */
@RestController
@RequestMapping("/api/residents/merge")
public class ResidentMergeController {

    private final ResidentMergeService mergeService;

    public ResidentMergeController(ResidentMergeService mergeService) {
        this.mergeService = mergeService;
    }

    /** 疑似重复候选：同一住房地址下存在 2 个及以上在档档案 */
    @GetMapping("/candidates")
    public ApiResponse<List<MergeCandidateVO>> candidates() {
        return ApiResponse.success(mergeService.listCandidates());
    }

    /** 归并预检：一次看清地址 / 热表号 / 换热站 / 供热状态与抽测、报修、各年度采暖费 */
    @GetMapping("/preview")
    public ApiResponse<MergePreviewVO> preview(@RequestParam Long keeperId,
                                               @RequestParam Long mergedId) {
        return ApiResponse.success(mergeService.preview(keeperId, mergedId));
    }

    /**
     * 确认归并：引用迁移 / 收费处置 / 审计写入任一失败整次回退；
     * 已成功的归并重复确认不会再次搬移数据或重算金额。
     */
    @PostMapping("/confirm")
    public ApiResponse<MergeAuditVO> confirm(@Valid @RequestBody MergeConfirmRequest request) {
        return ApiResponse.success("归并成功：抽测、报修、收费引用已全部改挂保留档案，重复档案已转只读",
                mergeService.confirm(request));
    }

    /** 按地址 / 热表号 / 旧档案编号查询：命中已归并档案时统一跳到保留档案，只落到同一户 */
    @GetMapping("/resolve")
    public ApiResponse<ResolveProfileVO> resolve(@RequestParam String keyword) {
        return ApiResponse.success(mergeService.resolve(keyword));
    }

    /** 归并审计记录（迁移条数、重算/复核年度、逐类处置明细） */
    @GetMapping("/audits")
    public ApiResponse<List<MergeAuditVO>> audits() {
        return ApiResponse.success(mergeService.listAudits());
    }
}
