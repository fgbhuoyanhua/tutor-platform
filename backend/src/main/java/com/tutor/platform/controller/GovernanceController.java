package com.tutor.platform.controller;

import com.tutor.platform.common.ApiResponse;
import com.tutor.platform.common.BizException;
import com.tutor.platform.common.UserContext;
import com.tutor.platform.dto.ComplaintCreateDTO;
import com.tutor.platform.security.RequireRole;
import com.tutor.platform.service.GovernanceService;
import com.tutor.platform.vo.BillVO;
import com.tutor.platform.vo.ComplaintVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 老师课时账单 / 投诉举报（学生与老师侧）
 */
@Tag(name = "账单与投诉")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GovernanceController {

    private final GovernanceService governanceService;

    @Operation(summary = "老师课时账单（按月，订单快照）")
    @GetMapping("/tutor/bill")
    @RequireRole({UserContext.ROLE_TUTOR})
    public ApiResponse<BillVO> bill(@RequestParam String month) {
        return ApiResponse.ok(governanceService.bill(UserContext.getUid(), month));
    }

    @Operation(summary = "老师课时账单导出 CSV（UTF-8 带 BOM）")
    @GetMapping("/tutor/bill/export")
    @RequireRole({UserContext.ROLE_TUTOR})
    public ResponseEntity<byte[]> export(@RequestParam String month) {
        String csv = governanceService.billCsv(UserContext.getUid(), month);
        String name = "bill-" + UserContext.getUid() + "-" + month + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @Operation(summary = "学生提交投诉举报")
    @PostMapping("/complaints")
    @RequireRole({UserContext.ROLE_STUDENT})
    public ApiResponse<Long> submit(@Valid @RequestBody ComplaintCreateDTO dto) {
        return ApiResponse.ok(governanceService.submitComplaint(UserContext.getUid(), dto).getId());
    }

    @Operation(summary = "我提交的投诉")
    @GetMapping("/complaints/my")
    @RequireRole({UserContext.ROLE_STUDENT})
    public ApiResponse<List<ComplaintVO>> my() {
        return ApiResponse.ok(governanceService.myComplaints(UserContext.getUid()));
    }

    @Operation(summary = "上传投诉证据（jpg/png/pdf，≤5MB）")
    @PostMapping("/complaints/evidence")
    @RequireRole({UserContext.ROLE_STUDENT})
    public ApiResponse<String> uploadEvidence(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(governanceService.uploadEvidence(UserContext.getUid(), file));
    }

    @Operation(summary = "读取投诉证据（本人或管理员）")
    @GetMapping("/complaints/evidence/{ownerUserId}/{fileName}")
    public ResponseEntity<Resource> evidenceFile(@PathVariable Long ownerUserId, @PathVariable String fileName) {
        Resource resource = governanceService.loadEvidence(
                UserContext.getUid(), UserContext.getRole(), ownerUserId, fileName);
        String lower = fileName.toLowerCase();
        MediaType type;
        if (lower.endsWith(".pdf")) {
            type = MediaType.APPLICATION_PDF;
        } else if (lower.endsWith(".png")) {
            type = MediaType.IMAGE_PNG;
        } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            type = MediaType.IMAGE_JPEG;
        } else {
            throw new BizException(400, "不支持的文件类型");
        }
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .body(resource);
    }
}
