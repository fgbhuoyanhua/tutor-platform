package com.tutor.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tutor.platform.common.BizException;
import com.tutor.platform.common.UserContext;
import com.tutor.platform.domain.OrderDomain;
import com.tutor.platform.dto.AppointmentCreateDTO;
import com.tutor.platform.entity.AppointmentEntity;
import com.tutor.platform.entity.RescheduleEntity;
import com.tutor.platform.entity.SubjectEntity;
import com.tutor.platform.entity.TutorEntity;
import com.tutor.platform.entity.UserEntity;
import com.tutor.platform.mapper.AppointmentMapper;
import com.tutor.platform.mapper.RescheduleMapper;
import com.tutor.platform.mapper.SubjectMapper;
import com.tutor.platform.mapper.TutorMapper;
import com.tutor.platform.mapper.UserMapper;
import com.tutor.platform.service.MessageService;
import com.tutor.platform.service.OrderService;
import com.tutor.platform.vo.IncomeVO;
import com.tutor.platform.vo.MyStudentVO;
import com.tutor.platform.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 预约订单服务实现：核心流转均经 OrderDomain 状态机校验 + 条件更新防并发
 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final AppointmentMapper appointmentMapper;
    private final TutorMapper tutorMapper;
    private final UserMapper userMapper;
    private final SubjectMapper subjectMapper;
    private final MessageService messageService;
    private final RescheduleMapper rescheduleMapper;

    @Override
    @Transactional
    public Long create(Long studentId, AppointmentCreateDTO dto) {
        TutorEntity tutor = tutorMapper.selectById(dto.getTutorId());
        if (tutor == null || tutor.getStatus() != 1) {
            throw new BizException("家教信息不存在或未上架");
        }
        if (tutor.getUserId().equals(studentId)) {
            throw new BizException("不能预约自己的家教信息");
        }
        // 时段冲突校验：同一老师同一日期同一时间段仅允许一个有效预约
        if (appointmentMapper.countConflict(tutor.getUserId(), dto.getAppointDate(), dto.getTimeSlot()) > 0) {
            throw new BizException("该时段已被预约，请选择其他时段");
        }
        AppointmentEntity order = new AppointmentEntity();
        order.setOrderNo(genOrderNo());
        order.setStudentId(studentId);
        order.setTutorId(tutor.getUserId());
        order.setSubjectId(dto.getSubjectId());
        order.setAppointDate(dto.getAppointDate());
        order.setTimeSlot(dto.getTimeSlot());
        order.setTotalPrice(tutor.getPrice());
        order.setStatus(OrderDomain.PENDING);
        order.setPayStatus(0);
        order.setCreateTime(LocalDateTime.now());
        try {
            appointmentMapper.insert(order);
        } catch (DuplicateKeyException ex) {
            // 时段唯一索引(uk_slot)冲突 → 并发下同一时段被抢先预约，给出精确提示
            String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
            if (msg.contains("uk_slot")) {
                throw new BizException(400, "该时段已被预约，请选择其他时间");
            }
            // 订单号极小概率碰撞：换号重试一次，仍冲突则兜底提示
            order.setOrderNo(genOrderNo());
            try {
                appointmentMapper.insert(order);
            } catch (DuplicateKeyException ex2) {
                String msg2 = ex2.getMessage() == null ? "" : ex2.getMessage().toLowerCase();
                if (msg2.contains("uk_slot")) {
                    throw new BizException(400, "该时段已被预约，请选择其他时间");
                }
                throw new BizException(400, "下单过于频繁，请稍后重试");
            }
        }
        // 下单后站内消息通知老师
        messageService.send(studentId, tutor.getUserId(), 1,
                "您有新的预约订单，订单号：" + order.getOrderNo()
                        + "，预约时间：" + dto.getAppointDate() + " " + dto.getTimeSlot());
        return order.getId();
    }

    @Override
    @Transactional
    public void confirm(Long orderId, Long tutorId) {
        transition(orderId, tutorId, OrderDomain.OP_TUTOR, "confirm", OrderDomain.CONFIRMED);
    }

    @Override
    @Transactional
    public void reject(Long orderId, Long tutorId) {
        transition(orderId, tutorId, OrderDomain.OP_TUTOR, "reject", OrderDomain.REJECTED);
    }

    @Override
    @Transactional
    public void cancel(Long orderId, Long studentId) {
        transition(orderId, studentId, OrderDomain.OP_STUDENT, "cancel", OrderDomain.CANCELED);
    }

    @Override
    @Transactional
    public void start(Long orderId, Long tutorId) {
        transition(orderId, tutorId, OrderDomain.OP_TUTOR, "start", OrderDomain.TEACHING);
    }

    @Override
    @Transactional
    public void finish(Long orderId, Long studentId) {
        transition(orderId, studentId, OrderDomain.OP_STUDENT, "finish", OrderDomain.FINISHED);
    }

    @Override
    public IPage<OrderVO> pageList(Long uid, Integer role, long page, long size) {
        LambdaQueryWrapper<AppointmentEntity> qw = new LambdaQueryWrapper<>();
        if (role != null && role == UserContext.ROLE_TUTOR) {
            qw.eq(AppointmentEntity::getTutorId, uid);
        } else {
            qw.eq(AppointmentEntity::getStudentId, uid);
        }
        qw.orderByDesc(AppointmentEntity::getCreateTime);
        IPage<AppointmentEntity> p = appointmentMapper.selectPage(new Page<>(page, size), qw);
        return p.convert(this::toVO);
    }

    /**
     * 统一流转：Domain 状态机校验合法流转 → 条件更新防并发 → 影响行数为0则拒绝
     */
    private void transition(Long orderId, Long operatorId, int operatorType, String action, int expectStatus) {
        AppointmentEntity order = appointmentMapper.selectById(orderId);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        // 状态机校验：非法流转直接抛异常
        OrderDomain.next(order.getStatus(), operatorType, action);
        int rows = appointmentMapper.updateStatusIf(orderId, order.getStatus(), expectStatus, operatorId);
        if (rows == 0) {
            throw new BizException("订单状态已变化或无权操作");
        }
        // 状态流转后站内消息通知对端（fromId=操作者，toId=接收者）
        String orderNo = order.getOrderNo();
        switch (action) {
            case "confirm" ->
                    messageService.send(operatorId, order.getStudentId(), 2,
                            "您的预约已接单，订单号：" + orderNo + "，请按时赴约");
            case "start" ->
                    messageService.send(operatorId, order.getStudentId(), 2,
                            "老师已开始授课，订单号：" + orderNo + "，请按时参加");
            case "reject" ->
                    messageService.send(operatorId, order.getStudentId(), 2,
                            "很抱歉，您的预约被老师拒绝，订单号：" + orderNo);
            case "cancel" ->
                    messageService.send(operatorId, order.getTutorId(), 2,
                            "学生取消了预约，订单号：" + orderNo);
            case "finish" ->
                    messageService.send(operatorId, order.getTutorId(), 2,
                            "学生已确认完成授课，订单号：" + orderNo + "，可查看评价");
            default -> { }
        }
    }

    @Override
    public IPage<OrderVO> adminPage(Integer status, long page, long size) {
        LambdaQueryWrapper<AppointmentEntity> qw = new LambdaQueryWrapper<>();
        if (status != null) {
            qw.eq(AppointmentEntity::getStatus, status);
        }
        qw.orderByDesc(AppointmentEntity::getCreateTime);
        IPage<AppointmentEntity> p = appointmentMapper.selectPage(new Page<>(page, size), qw);
        return p.convert(this::toVO);
    }

    @Override
    public IncomeVO incomeByMonth(Long tutorId, String month) {
        if (month == null || !month.matches("\\d{4}-\\d{2}")) {
            throw new BizException(400, "月份格式应为 yyyy-MM");
        }
        Map<String, Object> row = appointmentMapper.sumIncomeByMonth(tutorId, month);
        long cnt = row == null || row.get("cnt") == null ? 0 : ((Number) row.get("cnt")).longValue();
        BigDecimal amount = row == null || row.get("amount") == null
                ? BigDecimal.ZERO : new BigDecimal(row.get("amount").toString());
        return IncomeVO.builder()
                .month(month)
                .orderCount(cnt)
                .totalIncome(amount)
                .build();
    }

    @Override
    public List<IncomeVO> incomeLast6Months(Long tutorId) {
        List<Map<String, Object>> rows = appointmentMapper.sumIncomeLast6Months(tutorId);
        List<IncomeVO> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String month = row.get("month") == null ? "" : row.get("month").toString();
            long cnt = row.get("cnt") == null ? 0 : ((Number) row.get("cnt")).longValue();
            BigDecimal amount = row.get("amount") == null
                    ? BigDecimal.ZERO : new BigDecimal(row.get("amount").toString());
            result.add(IncomeVO.builder().month(month).orderCount(cnt).totalIncome(amount).build());
        }
        return result;
    }

    @Override
    public List<MyStudentVO> myStudents(Long tutorId) {
        List<Map<String, Object>> rows = appointmentMapper.myStudents(tutorId);
        List<MyStudentVO> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long studentId = row.get("student_id") == null ? null : ((Number) row.get("student_id")).longValue();
            if (studentId == null) continue;
            UserEntity stu = userMapper.selectById(studentId);
            long orderCount = row.get("order_count") == null ? 0 : ((Number) row.get("order_count")).longValue();
            long finished = row.get("finished_count") == null ? 0 : ((Number) row.get("finished_count")).longValue();
            Object lastDate = row.get("last_date");
            result.add(MyStudentVO.builder()
                    .studentId(studentId)
                    .studentName(stu == null ? "未知" : stu.getRealName())
                    .orderCount(orderCount)
                    .finishedCount(finished)
                    .lastDate(lastDate == null ? null : lastDate.toString())
                    .build());
        }
        return result;
    }

    private OrderVO toVO(AppointmentEntity o) {
        UserEntity stu = userMapper.selectById(o.getStudentId());
        UserEntity tut = userMapper.selectById(o.getTutorId());
        SubjectEntity s = subjectMapper.selectById(o.getSubjectId());
        return OrderVO.builder()
                .id(o.getId())
                .orderNo(o.getOrderNo())
                .studentId(o.getStudentId())
                .studentName(stu == null ? null : stu.getRealName())
                .tutorId(o.getTutorId())
                .tutorName(tut == null ? null : tut.getRealName())
                .subjectId(o.getSubjectId())
                .subjectName(s == null ? null : s.getName())
                .appointDate(o.getAppointDate())
                .timeSlot(o.getTimeSlot())
                .totalPrice(o.getTotalPrice())
                .status(o.getStatus())
                .createTime(o.getCreateTime())
                .build();
    }

    @Override
    @Transactional
    public void rescheduleRequest(Long orderId, Long studentId, String newDate, String newSlot) {
        AppointmentEntity order = appointmentMapper.selectById(orderId);
        if (order == null || !order.getStudentId().equals(studentId)) {
            throw new BizException("订单不存在或无权操作");
        }
        if (order.getStatus() != OrderDomain.CONFIRMED) {
            throw new BizException("仅已预约的订单可申请调课");
        }
        // 校验新时间
        if (newDate == null || !newDate.matches("\\d{4}-\\d{2}-\\d{2}")
                || LocalDate.parse(newDate).isBefore(LocalDate.now())) {
            throw new BizException("调课日期不合法，不能早于今天");
        }
        if (newSlot == null || !newSlot.matches("\\d{2}:\\d{2}-\\d{2}:\\d{2}")) {
            throw new BizException("时间段格式不合法");
        }
        // 已有待处理申请不重复提交
        Long pending = rescheduleMapper.selectCount(new LambdaQueryWrapper<RescheduleEntity>()
                .eq(RescheduleEntity::getOrderId, orderId)
                .eq(RescheduleEntity::getStatus, 0));
        if (pending != null && pending > 0) {
            throw new BizException("已有待处理的调课申请，请等待老师回复");
        }
        RescheduleEntity r = new RescheduleEntity();
        r.setOrderId(orderId);
        r.setStudentId(studentId);
        r.setOldDate(order.getAppointDate());
        r.setOldSlot(order.getTimeSlot());
        r.setNewDate(LocalDate.parse(newDate));
        r.setNewSlot(newSlot);
        r.setStatus(0);
        r.setCreateTime(LocalDateTime.now());
        rescheduleMapper.insert(r);
        messageService.send(studentId, order.getTutorId(), 2,
                "学生申请调课，订单号：" + order.getOrderNo() + "，原时间："
                        + order.getAppointDate() + " " + order.getTimeSlot() + "，希望改为："
                        + newDate + " " + newSlot + "，请及时处理");
    }

    @Override
    @Transactional
    public void rescheduleAccept(Long orderId, Long tutorId) {
        AppointmentEntity order = appointmentMapper.selectById(orderId);
        if (order == null || !order.getTutorId().equals(tutorId)) {
            throw new BizException("订单不存在或无权操作");
        }
        RescheduleEntity r = rescheduleMapper.selectOne(new LambdaQueryWrapper<RescheduleEntity>()
                .eq(RescheduleEntity::getOrderId, orderId)
                .eq(RescheduleEntity::getStatus, 0)
                .orderByDesc(RescheduleEntity::getCreateTime)
                .last("limit 1"));
        if (r == null) {
            throw new BizException("没有待处理的调课申请");
        }
        // 新时段冲突校验
        if (appointmentMapper.countConflict(tutorId, r.getNewDate(), r.getNewSlot()) > 0) {
            throw new BizException("新时段已被其他预约占用，请与学生协商其他时间");
        }
        // 更新订单时间
        order.setAppointDate(r.getNewDate());
        order.setTimeSlot(r.getNewSlot());
        appointmentMapper.updateById(order);
        r.setStatus(1);
        rescheduleMapper.updateById(r);
        messageService.send(tutorId, order.getStudentId(), 2,
                "您的调课申请已同意，新课程时间：" + r.getNewDate() + " " + r.getNewSlot());
    }

    @Override
    @Transactional
    public void rescheduleReject(Long orderId, Long tutorId) {
        AppointmentEntity order = appointmentMapper.selectById(orderId);
        if (order == null || !order.getTutorId().equals(tutorId)) {
            throw new BizException("订单不存在或无权操作");
        }
        RescheduleEntity r = rescheduleMapper.selectOne(new LambdaQueryWrapper<RescheduleEntity>()
                .eq(RescheduleEntity::getOrderId, orderId)
                .eq(RescheduleEntity::getStatus, 0)
                .orderByDesc(RescheduleEntity::getCreateTime)
                .last("limit 1"));
        if (r == null) {
            throw new BizException("没有待处理的调课申请");
        }
        r.setStatus(2);
        rescheduleMapper.updateById(r);
        messageService.send(tutorId, order.getStudentId(), 2,
                "很抱歉，您的调课申请被老师拒绝，仍按原时间上课："
                        + order.getAppointDate() + " " + order.getTimeSlot());
    }

    private String genOrderNo() {
        // 时间戳 + 6位随机：同秒并发碰撞概率约百万分之一，配合插入冲突重试兜底
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
    }
}
