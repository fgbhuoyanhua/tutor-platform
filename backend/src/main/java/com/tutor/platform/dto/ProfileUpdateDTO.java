package com.tutor.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改个人资料请求参数
 */
@Data
public class ProfileUpdateDTO {

    @Size(max = 50, message = "真实姓名不能超过50字")
    private String realName;

    @Size(max = 255, message = "头像地址过长")
    private String avatar;
}
