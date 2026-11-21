package com.tutor.platform.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 缓存失效策略的统一入口。
 *
 * <p>设计要点：
 * <ul>
 *   <li>老师看板(dashboard:{tutorUserId})：由订单状态变更、调课生效等事件主动删除，不等过期。</li>
 *   <li>老师列表(tutor:list:*)：由家教资料变更、审核结果、评分变化主动删除；用 SCAN 遍历而不使用 KEYS，避免大键空间阻塞。</li>
 *   <li>所有过期时间带抖动，避免同批 key 在同一秒集中失效造成缓存雪崩。</li>
 *   <li>缓存操作失败只记日志、不影响主流程：缓存是优化，数据库是真相。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheEvictor {

    /** 老师列表缓存键前缀 */
    public static final String TUTOR_LIST_PREFIX = "tutor:list:";
    /** 老师看板缓存键前缀 */
    public static final String DASHBOARD_PREFIX = "dashboard:";

    private final StringRedisTemplate redisTemplate;

    /**
     * 失效指定老师的看板缓存。
     *
     * @param tutorUserId 老师用户ID（appointment.tutor_id 存的就是它）
     */
    public void evictDashboard(Long tutorUserId) {
        if (tutorUserId == null) {
            return;
        }
        try {
            Boolean removed = redisTemplate.delete(DASHBOARD_PREFIX + tutorUserId);
            if (Boolean.TRUE.equals(removed)) {
                log.debug("看板缓存已失效: {}", tutorUserId);
            }
        } catch (Exception e) {
            log.warn("看板缓存失效失败（不影响主流程）: {}", e.getMessage());
        }
    }

    /**
     * 失效全部老师列表缓存（分页组合多，按前缀清理）。
     *
     * @return 实际删除的 key 数量
     */
    public int evictTutorList() {
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(TUTOR_LIST_PREFIX + "*").count(200).build())) {
            cursor.forEachRemaining(keys::add);
        } catch (Exception e) {
            log.warn("扫描老师列表缓存失败（不影响主流程）: {}", e.getMessage());
            return 0;
        }
        if (keys.isEmpty()) {
            return 0;
        }
        try {
            redisTemplate.delete(keys);
            log.debug("老师列表缓存已失效，共 {} 个 key", keys.size());
            return keys.size();
        } catch (Exception e) {
            log.warn("删除老师列表缓存失败（不影响主流程）: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 给过期时间加抖动：在基础值的 0~20% 区间随机上浮。
     * 同一批写入的缓存不会在同一秒集体过期。
     *
     * @param baseSeconds 基础过期秒数
     * @return 实际过期秒数
     */
    public static long jitter(int baseSeconds) {
        int span = Math.max(1, baseSeconds / 5);
        return baseSeconds + ThreadLocalRandom.current().nextInt(span + 1);
    }
}
