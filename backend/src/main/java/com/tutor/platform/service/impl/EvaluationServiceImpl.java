package com.tutor.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tutor.platform.common.BizException;
import com.tutor.platform.domain.OrderDomain;
import com.tutor.platform.dto.EvaluationCreateDTO;
import com.tutor.platform.entity.AppointmentEntity;
import com.tutor.platform.entity.EvaluationEntity;
import com.tutor.platform.entity.UserEntity;
import com.tutor.platform.mapper.AppointmentMapper;
import com.tutor.platform.mapper.EvaluationMapper;
import com.tutor.platform.mapper.TutorMapper;
import com.tutor.platform.mapper.UserMapper;
import com.tutor.platform.service.EvaluationService;
import com.tutor.platform.service.MessageService;
import com.tutor.platform.vo.EvaluationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 评价服务实现：仅已完成订单可评，防重复评价，提交后实时重算老师评分并通知老师
 */
@Service
@RequiredArgsConstructor
public class EvaluationServiceImpl implements EvaluationService {

    private final AppointmentMapper appointmentMapper;
    private final EvaluationMapper evaluationMapper;
    private final TutorMapper tutorMapper;
    private final UserMapper userMapper;
    private final MessageService messageService;

    @Override
    @Transactional
    public void submit(Long studentId, EvaluationCreateDTO dto) {
        AppointmentEntity order = appointmentMapper.selectById(dto.getOrderId());
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (!order.getStudentId().equals(studentId)) {
            throw new BizException(403, "无权评价该订单");
        }
        if (order.getStatus() != OrderDomain.FINISHED) {
            throw new BizException("仅已完成订单可评价");
        }
        Long exists = evaluationMapper.selectCount(new LambdaQueryWrapper<EvaluationEntity>()
                .eq(EvaluationEntity::getOrderId, order.getId())
                .eq(EvaluationEntity::getStudentId, studentId));
        if (exists != null && exists > 0) {
            throw new BizException("该订单已评价，请勿重复提交");
        }
        EvaluationEntity e = new EvaluationEntity();
        e.setOrderId(order.getId());
        e.setStudentId(studentId);
        e.setTutorId(order.getTutorId());
        e.setScore(dto.getScore());
        e.setContent(dto.getContent());
        e.setCreateTime(LocalDateTime.now());
        try {
            evaluationMapper.insert(e);
        } catch (DuplicateKeyException ex) {
            // 数据库唯一索引(uk_order_student)兜底，并发重复提交时友好提示
            throw new BizException("该订单已评价，请勿重复提交");
        }
        // 实时重算该老师全部家教信息的综合评分
        tutorMapper.updateRatingByUserId(order.getTutorId());
        // 站内消息通知老师
        messageService.send(studentId, order.getTutorId(), 3,
                "学生给您的授课打了" + dto.getScore() + "分" +
                        (dto.getContent() == null || dto.getContent().isBlank()
                                ? "" : "，评价：" + dto.getContent()));
    }

    @Override
    public IPage<EvaluationVO> pageByTutor(Long tutorUserId, long page, long size) {
        IPage<EvaluationEntity> p = evaluationMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<EvaluationEntity>()
                        .eq(EvaluationEntity::getTutorId, tutorUserId)
                        .orderByDesc(EvaluationEntity::getCreateTime));
        return p.convert(this::toVO);
    }

    private EvaluationVO toVO(EvaluationEntity e) {
        UserEntity stu = userMapper.selectById(e.getStudentId());
        return EvaluationVO.builder()
                .id(e.getId())
                .orderId(e.getOrderId())
                .studentId(e.getStudentId())
                .studentName(stu == null ? null : stu.getRealName())
                .score(e.getScore())
                .content(e.getContent())
                .reply(e.getReply())
                .createTime(e.getCreateTime())
                .build();
    }
}
