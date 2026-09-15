package com.tutor.platform.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求参数（用户名 + 手机号 + 验证码 + 密码 + 角色）
 */
@Data
public class RegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度需为3-20位")
    private String username;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "验证码不能为空")
    private String code;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度需为6-20位")
    private String password;

    /** 真实姓名（可选） */
    private String realName;

    /** 注册角色：1学生 2老师（默认学生）；禁止注册管理员(3) */
    @Min(value = 1, message = "注册角色仅支持学生(1)或老师(2)")
    @Max(value = 2, message = "注册角色仅支持学生(1)或老师(2)")
    private Integer role = 1;
}
