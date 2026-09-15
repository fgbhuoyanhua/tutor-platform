package com.tutor.platform.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 登录成功返回：JWT 令牌 + 用户信息（含角色，用于客户端按角色分发）
 */
@Data
@Builder
public class LoginVO {

    private String token;

    private Long userId;

    private String username;

    private String realName;

    /** 1学生 2老师 3管理员 */
    private Integer role;

    private String avatar;
}
