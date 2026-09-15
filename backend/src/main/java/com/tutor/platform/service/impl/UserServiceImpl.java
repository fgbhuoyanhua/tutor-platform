package com.tutor.platform.service.impl;

import com.tutor.platform.common.BizException;
import com.tutor.platform.dto.PasswordUpdateDTO;
import com.tutor.platform.dto.ProfileUpdateDTO;
import com.tutor.platform.entity.UserEntity;
import com.tutor.platform.mapper.UserMapper;
import com.tutor.platform.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 用户服务实现：资料修改与密码修改（改密需原密码认证）
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private final UserMapper userMapper;

    @Override
    public void updateProfile(Long uid, ProfileUpdateDTO dto) {
        UserEntity user = userMapper.selectById(uid);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        UserEntity upd = new UserEntity();
        upd.setId(uid);
        if (StringUtils.hasText(dto.getRealName())) {
            upd.setRealName(dto.getRealName());
        }
        if (StringUtils.hasText(dto.getAvatar())) {
            upd.setAvatar(dto.getAvatar());
        }
        userMapper.updateById(upd);
    }

    @Override
    public void updatePassword(Long uid, PasswordUpdateDTO dto) {
        UserEntity user = userMapper.selectById(uid);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        if (!ENCODER.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BizException(400, "原密码不正确");
        }
        if (ENCODER.matches(dto.getNewPassword(), user.getPassword())) {
            throw new BizException(400, "新密码不能与原密码相同");
        }
        UserEntity upd = new UserEntity();
        upd.setId(uid);
        upd.setPassword(ENCODER.encode(dto.getNewPassword()));
        userMapper.updateById(upd);
    }
}
