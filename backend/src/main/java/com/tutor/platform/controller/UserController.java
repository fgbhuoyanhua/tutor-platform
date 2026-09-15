package com.tutor.platform.controller;

import com.tutor.platform.common.ApiResponse;
import com.tutor.platform.common.UserContext;
import com.tutor.platform.dto.PasswordUpdateDTO;
import com.tutor.platform.dto.ProfileUpdateDTO;
import com.tutor.platform.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户接口：个人资料维护、修改密码（登录后所有角色可用）
 */
@Tag(name = "用户")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "修改个人资料（真实姓名/头像）")
    @PutMapping("/profile")
    public ApiResponse<Void> updateProfile(@Valid @RequestBody ProfileUpdateDTO dto) {
        userService.updateProfile(UserContext.getUid(), dto);
        return ApiResponse.ok();
    }

    @Operation(summary = "修改密码（需校验原密码）")
    @PutMapping("/password")
    public ApiResponse<Void> updatePassword(@Valid @RequestBody PasswordUpdateDTO dto) {
        userService.updatePassword(UserContext.getUid(), dto);
        return ApiResponse.ok();
    }
}
