package com.tutor.platform.service.impl;

import com.tutor.platform.common.BizException;
import com.tutor.platform.entity.TutorEntity;
import com.tutor.platform.mapper.AppointmentMapper;
import com.tutor.platform.mapper.EvaluationMapper;
import com.tutor.platform.mapper.SubjectMapper;
import com.tutor.platform.mapper.TutorMapper;
import com.tutor.platform.mapper.UserMapper;
import com.tutor.platform.service.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 家教信息发布/编辑的参数校验与状态流转单元测试。
 */
class TutorServicePublishTest {

    private TutorMapper tutorMapper;
    private TutorServiceImpl tutorService;

    @BeforeEach
    void setUp() {
        tutorMapper = mock(TutorMapper.class);
        tutorService = new TutorServiceImpl(
                tutorMapper,
                mock(UserMapper.class),
                mock(SubjectMapper.class),
                mock(EvaluationMapper.class),
                mock(AppointmentMapper.class),
                mock(MessageService.class),
                mock(StringRedisTemplate.class),
                new ObjectMapper());
    }

    private TutorEntity valid() {
        TutorEntity t = new TutorEntity();
        t.setSubjectId(2L);
        t.setPrice(new BigDecimal("100.00"));
        t.setGrade("高中");
        t.setIntroduce("擅长数学辅导");
        return t;
    }

    @Test
    @DisplayName("发布成功：初始状态待审核(0)、评分为5.00")
    void publishSuccess() {
        when(tutorMapper.insert(any(TutorEntity.class))).thenAnswer(inv -> {
            TutorEntity t = inv.getArgument(0);
            t.setId(99L);
            return 1;
        });

        Long id = tutorService.publish(7L, valid());

        assertEquals(99L, id);
        verify(tutorMapper).insert(any(TutorEntity.class));
    }

    @Test
    @DisplayName("资费必须大于0")
    void rejectZeroPrice() {
        TutorEntity t = valid();
        t.setPrice(BigDecimal.ZERO);
        BizException e = assertThrows(BizException.class, () -> tutorService.publish(7L, t));
        assertEquals("资费必须大于0", e.getMessage());
        verify(tutorMapper, never()).insert(any(TutorEntity.class));
    }

    @Test
    @DisplayName("资费为 null 拒绝")
    void rejectNullPrice() {
        TutorEntity t = valid();
        t.setPrice(null);
        assertThrows(BizException.class, () -> tutorService.publish(7L, t));
    }

    @Test
    @DisplayName("资费超过9999拒绝")
    void rejectPriceTooHigh() {
        TutorEntity t = valid();
        t.setPrice(new BigDecimal("10000"));
        BizException e = assertThrows(BizException.class, () -> tutorService.publish(7L, t));
        assertEquals("资费不能超过9999元/时", e.getMessage());
    }

    @Test
    @DisplayName("科目必填")
    void rejectNullSubject() {
        TutorEntity t = valid();
        t.setSubjectId(null);
        BizException e = assertThrows(BizException.class, () -> tutorService.publish(7L, t));
        assertEquals("授课科目不能为空", e.getMessage());
    }

    @Test
    @DisplayName("简介超过2000字拒绝")
    void rejectLongIntroduce() {
        TutorEntity t = valid();
        t.setIntroduce("x".repeat(2001));
        BizException e = assertThrows(BizException.class, () -> tutorService.publish(7L, t));
        assertEquals("个人简介不能超过2000字", e.getMessage());
    }

    @Test
    @DisplayName("编辑他人家教信息：403 越权")
    void rejectUpdateOthersTutor() {
        TutorEntity target = valid();
        target.setId(5L);
        target.setUserId(100L);
        when(tutorMapper.selectById(5L)).thenReturn(target);

        BizException e = assertThrows(BizException.class, () -> tutorService.update(200L, target));
        assertEquals(403, e.getCode());
        assertEquals("无权操作该家教信息", e.getMessage());
    }
}
