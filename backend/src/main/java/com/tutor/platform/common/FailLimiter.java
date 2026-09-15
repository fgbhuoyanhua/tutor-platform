package com.tutor.platform.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 登录/验证码失败限流器：Redis 优先，Redis 不可用时自动降级为进程内缓存，保证演示环境可用。
 * key 规则：业务前缀 + 业务键（如 login:fail:{username}）
 */
@Slf4j
@Component
public class FailLimiter {

    private final StringRedisTemplate redisTemplate;
    private final ConcurrentHashMap<String, LocalRecord> local = new ConcurrentHashMap<>();

    public FailLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private static class LocalRecord {
        int count;
        long lockedUntil; // 0 表示未锁定
    }

    /** 记录一次失败；返回当前累计失败次数 */
    public int recordFail(String key, int maxFail, long lockMinutes) {
        try {
            Long cnt = redisTemplate.opsForValue().increment(key);
            if (cnt != null && cnt == 1L) {
                redisTemplate.expire(key, lockMinutes, TimeUnit.MINUTES);
            }
            return cnt == null ? 1 : cnt.intValue();
        } catch (Exception e) {
            // Redis 不可用：本地降级
            LocalRecord r = local.computeIfAbsent(key, k -> new LocalRecord());
            r.count++;
            if (r.count >= maxFail) {
                r.lockedUntil = System.currentTimeMillis() + lockMinutes * 60_000L;
            }
            return r.count;
        }
    }

    /** 是否已锁定（达到失败上限且未过锁定时间） */
    public boolean isLocked(String key, int maxFail) {
        try {
            String v = redisTemplate.opsForValue().get(key);
            if (v == null) {
                return false;
            }
            return Integer.parseInt(v) >= maxFail;
        } catch (Exception e) {
            LocalRecord r = local.get(key);
            if (r == null || r.lockedUntil == 0) {
                return false;
            }
            if (System.currentTimeMillis() > r.lockedUntil) {
                local.remove(key);
                return false;
            }
            return true;
        }
    }

    /** 成功后清除失败记录 */
    public void clear(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            local.remove(key);
        }
    }
}
