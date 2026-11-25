package com.tutor.platform.controller;

import com.tutor.platform.common.ApiResponse;
import com.tutor.platform.common.BizException;
import com.tutor.platform.common.UserContext;
import com.tutor.platform.dto.CertSubmitDTO;
import com.tutor.platform.security.RequireRole;
import com.tutor.platform.service.TutorCertService;
import com.tutor.platform.vo.TutorCertVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 老师资格认证接口（老师端）
 */
@Tag(name = "老师资格认证")
@RestController
@RequestMapping("/api/tutor-cert")
@RequiredArgsConstructor
public class TutorCertController {

    private final TutorCertService tutorCertService;

    @Operation(summary = "上传证明材料（jpg/png/pdf，≤5MB）")
    @PostMapping("/upload")
    @RequireRole({UserContext.ROLE_TUTOR})
    public ApiResponse<String> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(tutorCertService.upload(UserContext.getUid(), file));
    }

    @Operation(summary = "提交资格认证申请")
    @PostMapping
    @RequireRole({UserContext.ROLE_TUTOR})
    public ApiResponse<Long> submit(@RequestBody CertSubmitDTO dto) {
        return ApiResponse.ok(tutorCertService.submit(UserContext.getUid(), dto));
    }

    @Operation(summary = "查看本人认证情况")
    @GetMapping("/my")
    @RequireRole({UserContext.ROLE_TUTOR})
    public ApiResponse<TutorCertVO> my() {
        return ApiResponse.ok(tutorCertService.my(UserContext.getUid()));
    }

    @Operation(summary = "读取证明材料（管理员可读任意，老师仅可读自己的）")
    @GetMapping("/file/{ownerUserId}/{fileName}")
    public ResponseEntity<Resource> file(@PathVariable Long ownerUserId, @PathVariable String fileName) {
        Resource resource = tutorCertService.loadFile(
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
