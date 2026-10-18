package com.tutor.platform.service.impl;

import com.tutor.platform.common.BizException;
import com.tutor.platform.dto.AppointmentCreateDTO;
import com.tutor.platform.entity.AppointmentEntity;
import com.tutor.platform.entity.TutorEntity;
import com.tutor.platform.mapper.AppointmentMapper;
import com.tutor.platform.mapper.RescheduleMapper;
import com.tutor.platform.mapper.SubjectMapper;
import com.tutor.platform.mapper.TutorMapper;
import com.tutor.platform.mapper.UserMapper;
import com.tutor.platform.service.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 预约下单核心校验单元测试：家教状态、自我预约、时段冲突、并发唯一键冲突兜底。
 */
class OrderServiceCreateTest {

    private AppointmentMapper appointmentMapper;
    private TutorMapper tutorMapper;
    private UserMapper userMapper;
    private SubjectMapper subjectMapper;
    private MessageService messageService;
    private RescheduleMapper rescheduleMapper;
    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        appointmentMapper = mock(AppointmentMapper.class);
        tutorMapper = mock(TutorMapper.class);
        userMapper = mock(UserMapper.class);
        subjectMapper = mock(SubjectMapper.class);
        messageService = mock(MessageService.class);
        rescheduleMapper = mock(RescheduleMapper.class);
        orderService = new OrderServiceImpl(
                appointmentMapper, tutorMapper, userMapper, subjectMapper,
                messageService, rescheduleMapper,
                mock(StringRedisTemplate.class), new ObjectMapper());
    }

    private TutorEntity tutor(long userId, int status) {
        TutorEntity t = new TutorEntity();
        t.setId(1L);
        t.setUserId(userId);
        t.setStatus(status);
        t.setPrice(new BigDecimal("120.00"));
        return t;
    }

    private AppointmentCreateDTO dto() {
        AppointmentCreateDTO d = new AppointmentCreateDTO();
        d.setTutorId(1L);
        d.setSubjectId(2L);
        d.setAppointDate(LocalDate.now().plusDays(3));
        d.setTimeSlot("09:00-11:00");
        return d;
    }

    @Test
    @DisplayName("家教不存在或未上架：拒绝下单")
    void rejectWhenTutorNotAvailable() {
        when(tutorMapper.selectById(1L)).thenReturn(null);
        BizException e = assertThrows(BizException.class, () -> orderService.create(10L, dto()));
        assertEquals("家教信息不存在或未上架", e.getMessage());
    }

    @Test
    @DisplayName("家教状态为待审核（0）：拒绝下单")
    void rejectWhenTutorNotApproved() {
        when(tutorMapper.selectById(1L)).thenReturn(tutor(2L, 0));
        assertThrows(BizException.class, () -> orderService.create(10L, dto()));
    }

    @Test
    @DisplayName("不能预约自己发布的家教")
    void rejectSelfBooking() {
        when(tutorMapper.selectById(1L)).thenReturn(tutor(10L, 1));
        BizException e = assertThrows(BizException.class, () -> orderService.create(10L, dto()));
        assertEquals("不能预约自己的家教信息", e.getMessage());
    }

    @Test
    @DisplayName("时段冲突：同一老师同期已有订单则拒绝")
    void rejectSlotConflict() {
        when(tutorMapper.selectById(1L)).thenReturn(tutor(2L, 1));
        when(appointmentMapper.countConflict(eq(2L), any(), eq("09:00-11:00"))).thenReturn(1L);
        BizException e = assertThrows(BizException.class, () -> orderService.create(10L, dto()));
        assertEquals("该时段已被预约，请选择其他时段", e.getMessage());
        verify(appointmentMapper, never()).insert(any(AppointmentEntity.class));
    }

    @Test
    @DisplayName("正常下单：写入订单并通知老师")
    void createSuccess() {
        when(tutorMapper.selectById(1L)).thenReturn(tutor(2L, 1));
        when(appointmentMapper.countConflict(anyLong(), any(), anyString())).thenReturn(0L);

        orderService.create(10L, dto());

        ArgumentCaptor<AppointmentEntity> captor = ArgumentCaptor.forClass(AppointmentEntity.class);
        verify(appointmentMapper).insert(captor.capture());
        AppointmentEntity saved = captor.getValue();

        assertEquals(10L, saved.getStudentId());
        assertEquals(2L, saved.getTutorId());
        assertEquals(2L, saved.getSubjectId());
        assertEquals(0, saved.getStatus(), "新订单应为待确认(0)");
        assertEquals(0, saved.getPayStatus());
        assertEquals(0, new BigDecimal("120.00").compareTo(saved.getTotalPrice()), "金额取家教资费");
        assertTrue(saved.getOrderNo().length() >= 14, "订单号应为时间戳+随机后缀");

        verify(messageService).send(eq(10L), eq(2L), any(Integer.class), anyString());
    }

    @Test
    @DisplayName("并发时段冲突：唯一索引 uk_slot 冲突转友好提示")
    void handleUniqueSlotConflict() {
        when(tutorMapper.selectById(1L)).thenReturn(tutor(2L, 1));
        when(appointmentMapper.countConflict(anyLong(), any(), anyString())).thenReturn(0L);
        when(appointmentMapper.insert(any(AppointmentEntity.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry for key 'uk_slot'"));

        BizException e = assertThrows(BizException.class, () -> orderService.create(10L, dto()));
        assertEquals("该时段已被预约，请选择其他时间", e.getMessage());
    }
}
