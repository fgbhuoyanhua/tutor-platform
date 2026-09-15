package com.tutor.platform.service;

import com.tutor.platform.dto.LoginDTO;
import com.tutor.platform.dto.RegisterDTO;
import com.tutor.platform.vo.LoginVO;

/**
 * 认证服务：验证码、注册、登录
 */
public interface AuthService {

    /** 发送短信验证码（毕设用 Redis 缓存 + 日志输出，不接真实短信通道） */
    void sendCode(String phone);

    void register(RegisterDTO dto);

    LoginVO login(LoginDTO dto);
}
