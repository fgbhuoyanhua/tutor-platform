package com.tutor.platform.controller;

import com.tutor.platform.common.ApiResponse;
import com.tutor.platform.common.UserContext;
import com.tutor.platform.security.AdminLog;
import com.tutor.platform.security.RequireRole;
import com.tutor.platform.service.GovernanceService;
import com.tutor.platform.vo.ComplaintVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员：投诉处理 + 推荐权重查看
 */
@Tag(name = "管理员·治理")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminGovernanceController {

    private final GovernanceService governanceService;

    @Operation(summary = "投诉列表（status 为空查全部）")
    @GetMapping("/complaints")
    @RequireRole({UserContext.ROLE_ADMIN})
    public ApiResponse<List<ComplaintVO>> list(@RequestParam(required = false) Integer status) {
        return ApiResponse.ok(governanceService.adminComplaints(status));
    }

    @Operation(summary = "处理投诉：成立（扣信用分）/ 驳回（必填说明）")
    @PutMapping("/complaints/{id}/handle")
    @RequireRole({UserContext.ROLE_ADMIN})
    @AdminLog(action = "处理投诉", targetType = "complaint", target = "#id",
            detail = "#accept ? '投诉成立，扣分' : '投诉驳回'")
    public ApiResponse<Void> handle(@PathVariable Long id,
                                    @RequestParam boolean accept,
                                    @RequestParam(required = false) Integer deduct,
                                    @RequestParam(required = false) String note) {
        governanceService.handleComplaint(id, accept, deduct, note, UserContext.getUid());
        return ApiResponse.ok();
    }
}
