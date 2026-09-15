package com.tutor.platform.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 登录失败限流器单元测试：Redis 不可用时本地降级逻辑
 */
class FailLimiterTest {

    private FailLimiter newLocalLimiter() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RuntimeException("redis unavailable"));
        when(redis.delete(org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new RuntimeException("redis unavailable"));
        return new FailLimiter(redis);
    }

    @Test
    @DisplayName("Redis不可用时本地降级：未达上限不锁定")
    void notLockedBeforeThreshold() {
        FailLimiter limiter = newLocalLimiter();
        limiter.recordFail("login:fail:u1", 5, 1);
        assertFalse(limiter.isLocked("login:fail:u1", 5), "1次失败不应锁定");
    }

    @Test
    @DisplayName("达到上限后锁定，锁定期间拒绝")
    void lockedAtThreshold() {
        FailLimiter limiter = newLocalLimiter();
        String key = "login:fail:u2";
        for (int i = 0; i < 5; i++) {
            limiter.recordFail(key, 5, 1);
        }
        assertTrue(limiter.isLocked(key, 5), "第5次失败后应锁定");
    }

    @Test
    @DisplayName("clear 后立即解锁")
    void clearUnlocks() {
        FailLimiter limiter = newLocalLimiter();
        String key = "login:fail:u3";
        for (int i = 0; i < 5; i++) {
            limiter.recordFail(key, 5, 1);
        }
        limiter.clear(key);
        assertFalse(limiter.isLocked(key, 5), "clear 后不应锁定");
    }

    @Test
    @DisplayName("不同 key 相互独立")
    void keysIndependent() {
        FailLimiter limiter = newLocalLimiter();
        for (int i = 0; i < 5; i++) {
            limiter.recordFail("login:fail:u4", 5, 1);
        }
        assertTrue(limiter.isLocked("login:fail:u4", 5));
        assertFalse(limiter.isLocked("login:fail:other", 5), "其他用户不受影响");
    }
}
