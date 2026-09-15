package com.tutor.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tutor.platform.common.BizException;
import com.tutor.platform.common.FailLimiter;
import com.tutor.platform.dto.LoginDTO;
import com.tutor.platform.dto.RegisterDTO;
import com.tutor.platform.entity.UserEntity;
import com.tutor.platform.mapper.UserMapper;
import com.tutor.platform.security.JwtUtil;
import com.tutor.platform.service.AuthService;
import com.tutor.platform.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现：验证码（Redis）→ 注册（BCrypt）→ 登录（JWT + 角色）
 * app.sms-mock=true 时（开发/演示环境），验证码固定为 123456 且不依赖 Redis。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final String CODE_KEY = "sms:code:";
    private static final long CODE_EXPIRE_MINUTES = 5;
    /** 演示模式固定验证码 */
    private static final String MOCK_CODE = "123456";

    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil;
    private final FailLimiter failLimiter;

    @Value("${app.sms-mock:false}")
    private boolean smsMock;

    @Value("${app.login.max-fail:5}")
    private int maxFail;

    @Value("${app.login.lock-minutes:15}")
    private long lockMinutes;

    @Value("${app.sms.max-fail:5}")
    private int smsMaxFail;

    @Value("${app.sms.lock-minutes:15}")
    private long smsLockMinutes;

    @Override
    public void sendCode(String phone) {
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
        // 毕设阶段不接真实短信通道：验证码写入 Redis 并打印到日志，便于联调
        try {
            redisTemplate.opsForValue().set(CODE_KEY + phone, code, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            if (!smsMock) {
                throw new BizException("验证码服务暂不可用，请稍后再试");
            }
            log.warn("[验证码] Redis 不可用，mock 模式跳过存储：{}", e.getMessage());
        }
        log.info("[验证码] phone={}, code={}, 有效期{}分钟", phone, code, CODE_EXPIRE_MINUTES);
    }

    @Override
    public void register(RegisterDTO dto) {
        // 服务层兜底：注册角色仅允许学生(1)/老师(2)，防止越权注册管理员(3)
        if (dto.getRole() == null || (dto.getRole() != 1 && dto.getRole() != 2)) {
            throw new BizException(400, "注册角色仅支持学生(1)或老师(2)");
        }
        String failKey = "sms:fail:" + dto.getPhone();
        // 验证码错误次数限制：连续错误达到上限后需重新获取验证码
        if (failLimiter.isLocked(failKey, smsMaxFail)) {
            throw new BizException(429, "验证码错误次数过多，请重新获取验证码");
        }
        if (smsMock) {
            if (!MOCK_CODE.equals(dto.getCode())) {
                failLimiter.recordFail(failKey, smsMaxFail, smsLockMinutes);
                throw new BizException(400, "演示模式验证码固定为 " + MOCK_CODE);
            }
        } else {
            String cached = redisTemplate.opsForValue().get(CODE_KEY + dto.getPhone());
            if (cached == null || !cached.equals(dto.getCode())) {
                failLimiter.recordFail(failKey, smsMaxFail, smsLockMinutes);
                throw new BizException("验证码错误或已过期");
            }
        }
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getPhone, dto.getPhone()));
        if (exists != null && exists > 0) {
            throw new BizException("该手机号已注册");
        }
        Long nameExists = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, dto.getUsername()));
        if (nameExists != null && nameExists > 0) {
            throw new BizException("用户名已存在");
        }
        UserEntity user = new UserEntity();
        user.setUsername(dto.getUsername());
        user.setPhone(dto.getPhone());
        user.setPassword(ENCODER.encode(dto.getPassword()));
        user.setRole(dto.getRole());
        user.setRealName(dto.getRealName());
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        try {
            userMapper.insert(user);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            // 并发注册兜底：唯一索引冲突时给出精确提示（uk_phone / uk_username）
            String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
            if (msg.contains("uk_phone")) {
                throw new BizException(400, "该手机号已注册");
            }
            if (msg.contains("uk_username")) {
                throw new BizException(400, "用户名已存在");
            }
            throw new BizException(400, "注册信息冲突，请重试");
        }
        // 注册成功清除验证码失败记录
        failLimiter.clear(failKey);
        if (!smsMock) {
            try {
                redisTemplate.delete(CODE_KEY + dto.getPhone());
            } catch (Exception ignored) {
                // mock 模式或 Redis 抖动时忽略清理失败
            }
        }
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        String lockKey = "login:fail:" + dto.getUsername();
        // 登录限流：连续失败达到上限后锁定一段时间（Redis 优先，本地降级）
        if (failLimiter.isLocked(lockKey, maxFail)) {
            throw new BizException(429, "登录失败次数过多，账号已锁定，请" + lockMinutes + "分钟后再试");
        }
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, dto.getUsername())
                .last("limit 1"));
        if (user == null || !ENCODER.matches(dto.getPassword(), user.getPassword())) {
            int cnt = failLimiter.recordFail(lockKey, maxFail, lockMinutes);
            int remain = Math.max(0, maxFail - cnt);
            throw new BizException(400, remain > 0
                    ? "用户名或密码错误，还可尝试" + remain + "次"
                    : "用户名或密码错误，账号已锁定，请" + lockMinutes + "分钟后再试");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(403, "账号已被禁用");
        }
        // 登录成功清除失败记录
        failLimiter.clear(lockKey);
        String token = jwtUtil.createToken(user.getId(), user.getRole());
        // 登录响应带角色，客户端据此按角色分发页面（学生/老师/管理员双端登录）
        return LoginVO.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .role(user.getRole())
                .avatar(user.getAvatar())
                .build();
    }
}
