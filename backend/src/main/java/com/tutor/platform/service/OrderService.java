package com.tutor.platform.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.tutor.platform.dto.AppointmentCreateDTO;
import com.tutor.platform.vo.IncomeVO;
import com.tutor.platform.vo.MyStudentVO;
import com.tutor.platform.vo.OrderVO;
import com.tutor.platform.vo.TutorDashboardVO;

import java.util.List;

/**
 * 预约订单服务：提交预约、接单、拒绝、取消、完成
 */
public interface OrderService {

    Long create(Long studentId, AppointmentCreateDTO dto);

    void confirm(Long orderId, Long tutorId);

    void reject(Long orderId, Long tutorId);

    void cancel(Long orderId, Long studentId);

    void start(Long orderId, Long tutorId);

    void finish(Long orderId, Long studentId);

    /** 按角色查订单列表（学生看自己的，老师看收到的） */
    IPage<OrderVO> pageList(Long uid, Integer role, long page, long size);

    /** 老师收入统计（按授课月份，仅已完成订单） */
    IncomeVO incomeByMonth(Long tutorId, String month);

    /** 老师近6个月收入趋势 */
    List<IncomeVO> incomeLast6Months(Long tutorId);

    /** 老师端数据看板汇总（收入/订单/趋势/学科分布/状态分布） */
    TutorDashboardVO dashboard(Long tutorId);

    /** 老师"我的学生"聚合列表 */
    List<MyStudentVO> myStudents(Long tutorId);

    /** 管理员：全量订单分页查询（可按状态筛选） */
    IPage<OrderVO> adminPage(Integer status, long page, long size);

    /** 学生发起调课申请（仅已预约订单） */
    void rescheduleRequest(Long orderId, Long studentId, String newDate, String newSlot);

    /** 老师同意调课 */
    void rescheduleAccept(Long orderId, Long tutorId);

    /** 老师拒绝调课 */
    void rescheduleReject(Long orderId, Long tutorId);
}
