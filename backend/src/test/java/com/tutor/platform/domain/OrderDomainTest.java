package com.tutor.platform.domain;

import com.tutor.platform.common.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 订单状态机单元测试：覆盖全部合法流转与非法越权/乱序拦截
 */
class OrderDomainTest {

    @Test
    @DisplayName("合法流转：待确认->已预约->授课中->已完成")
    void legalFlow() {
        int s0 = OrderDomain.next(OrderDomain.PENDING, OrderDomain.OP_TUTOR, "confirm");
        assertEquals(OrderDomain.CONFIRMED, s0);
        int s1 = OrderDomain.next(s0, OrderDomain.OP_TUTOR, "start");
        assertEquals(OrderDomain.TEACHING, s1);
        int s2 = OrderDomain.next(s1, OrderDomain.OP_STUDENT, "finish");
        assertEquals(OrderDomain.FINISHED, s2);
    }

    @Test
    @DisplayName("老师拒绝：待确认->已拒绝")
    void rejectFlow() {
        int s = OrderDomain.next(OrderDomain.PENDING, OrderDomain.OP_TUTOR, "reject");
        assertEquals(OrderDomain.REJECTED, s);
    }

    @Test
    @DisplayName("学生取消：待确认->已取消")
    void cancelFlow() {
        int s = OrderDomain.next(OrderDomain.PENDING, OrderDomain.OP_STUDENT, "cancel");
        assertEquals(OrderDomain.CANCELED, s);
    }

    @Test
    @DisplayName("越权拦截：学生接单/学生开始授课/老师取消/老师确认完成")
    void forbiddenOperators() {
        assertThrows(BizException.class,
                () -> OrderDomain.next(OrderDomain.PENDING, OrderDomain.OP_STUDENT, "confirm"));
        assertThrows(BizException.class,
                () -> OrderDomain.next(OrderDomain.CONFIRMED, OrderDomain.OP_STUDENT, "start"));
        assertThrows(BizException.class,
                () -> OrderDomain.next(OrderDomain.PENDING, OrderDomain.OP_TUTOR, "cancel"));
        assertThrows(BizException.class,
                () -> OrderDomain.next(OrderDomain.TEACHING, OrderDomain.OP_TUTOR, "finish"));
    }

    @Test
    @DisplayName("乱序拦截：跳过授课中直接完成/重复接单/未接单开始/已取消开始")
    void illegalTransitions() {
        // 跳过授课中直接完成
        assertThrows(BizException.class,
                () -> OrderDomain.next(OrderDomain.CONFIRMED, OrderDomain.OP_STUDENT, "finish"));
        // 重复接单
        assertThrows(BizException.class,
                () -> OrderDomain.next(OrderDomain.CONFIRMED, OrderDomain.OP_TUTOR, "confirm"));
        // 未接单开始授课
        assertThrows(BizException.class,
                () -> OrderDomain.next(OrderDomain.PENDING, OrderDomain.OP_TUTOR, "start"));
        // 已取消后开始
        assertThrows(BizException.class,
                () -> OrderDomain.next(OrderDomain.CANCELED, OrderDomain.OP_TUTOR, "start"));
        // 已拒绝后确认
        assertThrows(BizException.class,
                () -> OrderDomain.next(OrderDomain.REJECTED, OrderDomain.OP_STUDENT, "finish"));
    }

    @Test
    @DisplayName("未知动作拒绝")
    void unknownAction() {
        assertThrows(BizException.class,
                () -> OrderDomain.next(OrderDomain.PENDING, OrderDomain.OP_STUDENT, "pay"));
    }

    @Test
    @DisplayName("状态文案")
    void statusText() {
        assertEquals("待确认", OrderDomain.statusText(OrderDomain.PENDING));
        assertEquals("已预约", OrderDomain.statusText(OrderDomain.CONFIRMED));
        assertEquals("授课中", OrderDomain.statusText(OrderDomain.TEACHING));
        assertEquals("已完成", OrderDomain.statusText(OrderDomain.FINISHED));
        assertEquals("已取消", OrderDomain.statusText(OrderDomain.CANCELED));
        assertEquals("已拒绝", OrderDomain.statusText(OrderDomain.REJECTED));
        assertEquals("未知", OrderDomain.statusText(99));
    }
}
