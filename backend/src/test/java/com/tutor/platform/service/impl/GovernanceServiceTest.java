package com.tutor.platform.service.impl;

import com.tutor.platform.common.BizException;
import com.tutor.platform.common.CacheEvictor;
import com.tutor.platform.dto.ComplaintCreateDTO;
import com.tutor.platform.entity.AppointmentEntity;
import com.tutor.platform.entity.ComplaintEntity;
import com.tutor.platform.entity.TutorEntity;
import com.tutor.platform.entity.TutorViolationEntity;
import com.tutor.platform.mapper.AppointmentMapper;
import com.tutor.platform.mapper.ComplaintMapper;
import com.tutor.platform.mapper.SubjectMapper;
import com.tutor.platform.mapper.TutorMapper;
import com.tutor.platform.mapper.TutorViolationMapper;
import com.tutor.platform.mapper.UserMapper;
import com.tutor.platform.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 治理闭环单元测试：投诉提交校验、投诉处理（成立扣分 + 违规留痕 + 幂等）、课时账单聚合。
 */
class GovernanceServiceTest {

    private AppointmentMapper appointmentMapper;
    private TutorMapper tutorMapper;
    private UserMapper userMapper;
    private SubjectMapper subjectMapper;
    private ComplaintMapper complaintMapper;
    private TutorViolationMapper violationMapper;
    private MessageService messageService;
    private CacheEvictor cacheEvictor;
    private GovernanceServiceImpl service;

    @BeforeEach
    void setUp() {
        appointmentMapper = mock(AppointmentMapper.class);
        tutorMapper = mock(TutorMapper.class);
        userMapper = mock(UserMapper.class);
        subjectMapper = mock(SubjectMapper.class);
        complaintMapper = mock(ComplaintMapper.class);
        violationMapper = mock(TutorViolationMapper.class);
        messageService = mock(MessageService.class);
        cacheEvictor = mock(CacheEvictor.class);
        service = new GovernanceServiceImpl(appointmentMapper, tutorMapper, userMapper, subjectMapper,
                complaintMapper, violationMapper, messageService, cacheEvictor);
    }

    private TutorEntity tutor(int credit) {
        TutorEntity t = new TutorEntity();
        t.setId(1L);
        t.setUserId(2L);
        t.setPrice(new BigDecimal("120.00"));
        t.setCreditScore(credit);
        t.setViolationCount(0);
        return t;
    }

    private ComplaintCreateDTO dto(Long tutorId, String content) {
        ComplaintCreateDTO d = new ComplaintCreateDTO();
        d.setTutorId(tutorId);
        d.setType(1);
        d.setContent(content);
        return d;
    }

    @Test
    @DisplayName("不能投诉自己")
    void rejectSelfComplaint() {
        BizException e = assertThrows(BizException.class, () -> service.submitComplaint(2L, dto(2L, "爽约")));
        assertEquals(400, e.getCode());
    }

    @Test
    @DisplayName("投诉说明为空拒绝")
    void rejectEmptyContent() {
        BizException e = assertThrows(BizException.class, () -> service.submitComplaint(4L, dto(2L, "   ")));
        assertEquals(400, e.getCode());
    }

    @Test
    @DisplayName("重复投诉（已有待处理）返回 409")
    void rejectDuplicatePending() {
        when(tutorMapper.selectList(any())).thenReturn(List.of(tutor(100)));
        when(complaintMapper.selectCount(any())).thenReturn(1L);
        BizException e = assertThrows(BizException.class, () -> service.submitComplaint(4L, dto(2L, "爽约")));
        assertEquals(409, e.getCode());
        verify(complaintMapper, never()).insert(any(ComplaintEntity.class));
    }

    @Test
    @DisplayName("并发重复投诉：唯一索引冲突转 409")
    void duplicateKeyTo409() {
        when(tutorMapper.selectList(any())).thenReturn(List.of(tutor(100)));
        when(complaintMapper.selectCount(any())).thenReturn(0L);
        when(complaintMapper.insert(any(ComplaintEntity.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry for key 'uk_complaint_pending'"));
        BizException e = assertThrows(BizException.class, () -> service.submitComplaint(4L, dto(2L, "爽约")));
        assertEquals(409, e.getCode());
    }

    @Test
    @DisplayName("投诉关联他人订单：403")
    void rejectOthersOrder() {
        when(tutorMapper.selectList(any())).thenReturn(List.of(tutor(100)));
        AppointmentEntity a = new AppointmentEntity();
        a.setId(50L);
        a.setStudentId(999L);
        a.setTutorId(2L);
        when(appointmentMapper.selectById(50L)).thenReturn(a);
        ComplaintCreateDTO d = dto(2L, "爽约");
        d.setOrderId(50L);
        BizException e = assertThrows(BizException.class, () -> service.submitComplaint(4L, d));
        assertEquals(403, e.getCode());
    }

    @Test
    @DisplayName("投诉成立：写违规记录 + 扣信用分 + 双方站内通知")
    void acceptComplaint() {
        ComplaintEntity c = new ComplaintEntity();
        c.setId(7L);
        c.setComplainantId(4L);
        c.setTutorId(2L);
        c.setType(2);
        c.setContent("私改价格");
        c.setStatus(0);
        when(complaintMapper.selectById(7L)).thenReturn(c);
        when(tutorMapper.selectList(any())).thenReturn(List.of(tutor(100)));

        service.handleComplaint(7L, true, 15, "核实属实", 1L);

        ArgumentCaptor<TutorViolationEntity> vc = ArgumentCaptor.forClass(TutorViolationEntity.class);
        verify(violationMapper).insert(vc.capture());
        assertEquals(2L, vc.getValue().getTutorId());
        assertEquals(1, vc.getValue().getType(), "投诉成立记为类型1");
        assertEquals(15, vc.getValue().getDeduct());
        verify(tutorMapper).updateById(any(TutorEntity.class));
        verify(messageService, org.mockito.Mockito.times(2)).send(anyLong(), anyLong(), any(), any());
        assertEquals(1, c.getStatus());
        assertEquals(15, c.getDeductScore());
    }

    @Test
    @DisplayName("投诉驳回必须填说明")
    void rejectNeedsNote() {
        ComplaintEntity c = new ComplaintEntity();
        c.setId(7L);
        c.setStatus(0);
        c.setTutorId(2L);
        c.setComplainantId(4L);
        when(complaintMapper.selectById(7L)).thenReturn(c);
        BizException e = assertThrows(BizException.class,
                () -> service.handleComplaint(7L, false, 0, "  ", 1L));
        assertEquals(400, e.getCode());
    }

    @Test
    @DisplayName("重复处理同一条投诉被拦（幂等）")
    void cannotHandleTwice() {
        ComplaintEntity c = new ComplaintEntity();
        c.setId(7L);
        c.setStatus(1);
        when(complaintMapper.selectById(7L)).thenReturn(c);
        BizException e = assertThrows(BizException.class,
                () -> service.handleComplaint(7L, true, 10, "x", 1L));
        assertEquals(400, e.getCode());
        verify(violationMapper, never()).insert(any(TutorViolationEntity.class));
    }

    @Test
    @DisplayName("信用分累加：扣分夹在 0 下限，违规次数累加")
    void addCreditClamp() {
        when(tutorMapper.selectList(any())).thenReturn(List.of(tutor(5)));
        int next = service.addCredit(2L, -10, 1);
        assertEquals(0, next);
        verify(tutorMapper).updateById(any(TutorEntity.class));
    }

    @Test
    @DisplayName("课时账单：只统计已完成金额，未结算单数单独计")
    void billAggregation() {
        AppointmentEntity done = new AppointmentEntity();
        done.setId(1L);
        done.setOrderNo("A1");
        done.setStudentId(4L);
        done.setTutorId(2L);
        done.setSubjectId(2L);
        done.setAppointDate(LocalDate.of(2026, 11, 20));
        done.setTimeSlot("08:00-10:00");
        done.setTotalPrice(new BigDecimal("120.00"));
        done.setStatus(3);
        AppointmentEntity cancelled = new AppointmentEntity();
        cancelled.setId(2L);
        cancelled.setOrderNo("A2");
        cancelled.setStudentId(4L);
        cancelled.setTutorId(2L);
        cancelled.setSubjectId(2L);
        cancelled.setAppointDate(LocalDate.of(2026, 11, 21));
        cancelled.setTimeSlot("09:00-11:00");
        cancelled.setTotalPrice(new BigDecimal("120.00"));
        cancelled.setStatus(4);
        AppointmentEntity waiting = new AppointmentEntity();
        waiting.setId(3L);
        waiting.setOrderNo("A3");
        waiting.setStudentId(4L);
        waiting.setTutorId(2L);
        waiting.setSubjectId(2L);
        waiting.setAppointDate(LocalDate.of(2026, 11, 25));
        waiting.setTimeSlot("19:00-21:00");
        waiting.setTotalPrice(new BigDecimal("120.00"));
        waiting.setStatus(0);
        when(appointmentMapper.selectList(any())).thenReturn(List.of(done, cancelled, waiting));
        when(tutorMapper.selectList(any())).thenReturn(List.of(tutor(100)));

        var bill = service.bill(2L, "2026-11");

        assertEquals(1, bill.getFinishedCount());
        assertEquals(120, bill.getFinishedMinutes());
        assertEquals(new BigDecimal("120.00"), bill.getTotalAmount());
        assertEquals(1, bill.getPendingCount());
        assertEquals(3, bill.getItems().size());
        assertTrue(bill.getItems().get(0).getStatusName().contains("已完成"));
    }

    @Test
    @DisplayName("月份格式不合法拒绝")
    void billMonthValidation() {
        BizException e = assertThrows(BizException.class, () -> service.bill(2L, "2026/11"));
        assertEquals(400, e.getCode());
    }
}

