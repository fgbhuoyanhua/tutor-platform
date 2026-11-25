package com.tutor.platform.controller;

import com.tutor.platform.common.ApiResponse;
import com.tutor.platform.common.UserContext;
import com.tutor.platform.security.AdminLog;
import com.tutor.platform.security.RequireRole;
import com.tutor.platform.service.TutorCertService;
import com.tutor.platform.vo.TutorCertVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员：老师资格认证审核
 */
@Tag(name = "管理员-资格认证")
@RestController
@RequestMapping("/api/admin/certs")
@RequiredArgsConstructor
public class AdminCertController {

    private final TutorCertService tutorCertService;

    @Operation(summary = "待审核的资格认证列表")
    @GetMapping("/pending")
    @RequireRole({UserContext.ROLE_ADMIN})
    public ApiResponse<List<TutorCertVO>> pending() {
        return ApiResponse.ok(tutorCertService.pendingList());
    }

    @Operation(summary = "审核资格认证（通过/驳回）")
    @PutMapping("/{id}/audit")
    @RequireRole({UserContext.ROLE_ADMIN})
    @AdminLog(action = "审核资格认证", targetType = "tutor_cert", target = "#id",
            detail = "#pass ? '认证通过' : '认证驳回' + (#reason != null && #reason.length() > 0 ? '，原因：' + #reason : '')")
    public ApiResponse<Void> audit(@PathVariable Long id,
                                   @RequestParam boolean pass,
                                   @RequestParam(required = false) String reason) {
        tutorCertService.audit(id, pass, reason, UserContext.getUid());
        return ApiResponse.ok();
    }
}
