package com.tutor.platform.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutor.platform.common.BizException;
import com.tutor.platform.common.CacheEvictor;
import com.tutor.platform.domain.OrderDomain;
import com.tutor.platform.entity.AppointmentEntity;
import com.tutor.platform.mapper.AppointmentMapper;
import com.tutor.platform.mapper.RescheduleMapper;
import com.tutor.platform.mapper.SubjectMapper;
import com.tutor.platform.mapper.TutorMapper;
import com.tutor.platform.mapper.UserMapper;
import com.tutor.platform.service.GovernanceService;
import com.tutor.platform.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单完成 → 信用分加分接线回归：
 * 加分只在「授课中(2) → 已完成(3)」真正成功的那一次调用，
 * 重复完成（库里已是 3）被状态机拦下，不会再加分。
 */
class OrderServiceFinishTest {

    private AppointmentMapper appointmentMapper;
    private GovernanceService governanceService;
    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        appointmentMapper = mock(AppointmentMapper.class);
        governanceService = mock(GovernanceService.class);
        orderService = new OrderServiceImpl(
                appointmentMapper, mock(TutorMapper.class), mock(UserMapper.class), mock(SubjectMapper.class),
                mock(MessageService.class), mock(RescheduleMapper.class),
                mock(StringRedisTemplate.class), new ObjectMapper(),
                mock(CacheEvictor.class), governanceService);
    }

    private AppointmentEntity order(int status) {
        AppointmentEntity a = new AppointmentEntity();
        a.setId(9L);
        a.setOrderNo("20261125090000001");
        a.setStudentId(10L);
        a.setTutorId(2L);
        a.setStatus(status);
        return a;
    }

    @Test
    @DisplayName("授课中确认完成：加 1 信用分，且只加一次")
    void finishAddsCreditOnce() {
        when(appointmentMapper.selectById(9L)).thenReturn(order(OrderDomain.TEACHING));
        when(appointmentMapper.updateStatusIf(eq(9L), eq(OrderDomain.TEACHING), eq(OrderDomain.FINISHED),
                eq(10L))).thenReturn(1);

        orderService.finish(9L, 10L);

        verify(governanceService).onOrderFinished(2L);
    }

    @Test
    @DisplayName("重复确认已完成订单：状态机拦截，不再加分")
    void cannotFinishTwice() {
        when(appointmentMapper.selectById(9L)).thenReturn(order(OrderDomain.FINISHED));

        assertThrows(BizException.class, () -> orderService.finish(9L, 10L));
        verify(governanceService, never()).onOrderFinished(anyLong());
    }

    @Test
    @DisplayName("条件更新失败（并发已变化）：抛异常且不加分")
    void noCreditWhenConditionalUpdateFails() {
        when(appointmentMapper.selectById(9L)).thenReturn(order(OrderDomain.TEACHING));
        when(appointmentMapper.updateStatusIf(anyLong(), anyInt(), anyInt(), anyLong())).thenReturn(0);

        assertThrows(BizException.class, () -> orderService.finish(9L, 10L));
        verify(governanceService, never()).onOrderFinished(anyLong());
    }

    @Test
    @DisplayName("接单、开始授课不加信用分")
    void otherActionsDoNotAddCredit() {
        when(appointmentMapper.selectById(9L)).thenReturn(order(OrderDomain.PENDING));
        when(appointmentMapper.updateStatusIf(anyLong(), anyInt(), anyInt(), anyLong())).thenReturn(1);

        orderService.confirm(9L, 2L);
        verify(governanceService, never()).onOrderFinished(anyLong());
    }
}
