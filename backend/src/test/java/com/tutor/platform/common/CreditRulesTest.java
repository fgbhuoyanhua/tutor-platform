package com.tutor.platform.common;

import com.tutor.platform.service.impl.GovernanceServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 信用分与推荐排序公式的单元测试（论文里"可调加权公式"的口径就是这里）。
 */
class CreditRulesTest {

    private final CreditRules rules = new CreditRules(0.35, 0.25, 0.20, 0.20);

    @Test
    @DisplayName("信用分边界：不低于0、不高于100")
    void clampBoundary() {
        assertEquals(0, CreditRules.apply(5, -20));
        assertEquals(100, CreditRules.apply(95, 10));
        assertEquals(90, CreditRules.apply(100, -10));
        assertEquals(0, CreditRules.clamp(-1));
    }

    @Test
    @DisplayName("等级阈值：90 A / 75 B / 60 C / 其余 D")
    void levelThresholds() {
        assertEquals("A", CreditRules.levelName(100));
        assertEquals("A", CreditRules.levelName(90));
        assertEquals("B", CreditRules.levelName(89));
        assertEquals("B", CreditRules.levelName(75));
        assertEquals("C", CreditRules.levelName(74));
        assertEquals("C", CreditRules.levelName(60));
        assertEquals("D", CreditRules.levelName(59));
        assertEquals("金牌老师", CreditRules.levelLabel(95));
        assertEquals("受限老师", CreditRules.levelLabel(30));
    }

    @Test
    @DisplayName("低于60分限制接单")
    void bookableGate() {
        assertTrue(CreditRules.isBookable(60));
        assertFalse(CreditRules.isBookable(59));
    }

    @Test
    @DisplayName("推荐分：认证权重最高，全满为1.0")
    void recommendScoreShape() {
        double full = rules.recommendScore(2, 5.0, 99L, 100);
        assertEquals(1.0, full, 0.001);
        double none = rules.recommendScore(0, 0.0, 0L, 0);
        assertEquals(0.0, none, 0.001);
        // 已认证但无其他数据 ≈ 0.35
        assertEquals(0.35, rules.recommendScore(2, 0.0, 0L, 0), 0.001);
        // 待审比未认证高（0.35 权重 × 0.35）
        assertTrue(rules.recommendScore(1, 0.0, 0L, 0) > rules.recommendScore(0, 0.0, 0L, 0));
    }

    @Test
    @DisplayName("推荐理由只列命中项，全空返回新入驻老师")
    void reasonLabel() {
        assertEquals("新入驻老师", rules.reasonLabel(0, 0.0, 0L, 20));
        String r = rules.reasonLabel(2, 4.8, 12L, 95);
        assertTrue(r.contains("已认证教师"));
        assertTrue(r.contains("完课 12 单"));
        assertTrue(r.contains("好评 4.8"));
        assertTrue(r.contains("信用 95"));
    }

    @Test
    @DisplayName("账单时长解析：时段相减，非法值兜底120分钟")
    void slotMinutes() {
        assertEquals(120, GovernanceServiceImpl.slotMinutes("08:00-10:00"));
        assertEquals(120, GovernanceServiceImpl.slotMinutes("19:00-21:00"));
        assertEquals(120, GovernanceServiceImpl.slotMinutes(null));
        assertEquals(120, GovernanceServiceImpl.slotMinutes("乱码"));
    }
}
