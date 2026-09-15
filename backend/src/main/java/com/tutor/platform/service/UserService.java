package com.tutor.platform.service;

import com.tutor.platform.dto.PasswordUpdateDTO;
import com.tutor.platform.dto.ProfileUpdateDTO;

/**
 * 用户服务：个人资料维护、密码修改
 */
public interface UserService {

    /** 修改个人资料（真实姓名/头像），uid 由调用方从登录上下文取 */
    void updateProfile(Long uid, ProfileUpdateDTO dto);

    /** 修改密码：校验原密码后更新 */
    void updatePassword(Long uid, PasswordUpdateDTO dto);
}
