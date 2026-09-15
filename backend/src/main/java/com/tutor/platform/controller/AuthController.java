package com.tutor.platform.controller;

import com.tutor.platform.common.ApiResponse;
import com.tutor.platform.dto.LoginDTO;
import com.tutor.platform.dto.RegisterDTO;
import com.tutor.platform.service.AuthService;
import com.tutor.platform.vo.LoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口：公开访问，登录返回 JWT 与角色（双端登录按角色分发）
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "发送短信验证码")
    @GetMapping("/code")
    public ApiResponse<Void> sendCode(@RequestParam String phone) {
        authService.sendCode(phone);
        return ApiResponse.ok();
    }

    @Operation(summary = "注册（手机号+验证码，可指定学生/老师角色）")
    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterDTO dto) {
        authService.register(dto);
        return ApiResponse.ok();
    }

    @Operation(summary = "登录，返回JWT与角色信息")
    @PostMapping("/login")
    public ApiResponse<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return ApiResponse.ok(authService.login(dto));
    }
}
