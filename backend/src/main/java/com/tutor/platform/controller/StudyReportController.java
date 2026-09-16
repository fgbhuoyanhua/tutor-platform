package com.tutor.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tutor.platform.common.ApiResponse;
import com.tutor.platform.common.BizException;
import com.tutor.platform.common.UserContext;
import com.tutor.platform.entity.AppointmentEntity;
import com.tutor.platform.entity.StudyReportEntity;
import com.tutor.platform.mapper.AppointmentMapper;
import com.tutor.platform.mapper.StudyReportMapper;
import com.tutor.platform.security.RequireRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 课后学习报告：老师提交，学生查看
 */
@Tag(name = "学习报告")
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class StudyReportController {

    private final StudyReportMapper reportMapper;
    private final AppointmentMapper appointmentMapper;

    @Operation(summary = "老师提交课后学习报告（仅已完成订单）")
    @PostMapping
    @RequireRole({UserContext.ROLE_TUTOR})
    public ApiResponse<Void> submit(@RequestBody Map<String, String> body) {
        Long orderId = Long.valueOf(body.get("orderId"));
        String content = body.get("content");
        if (content == null || content.trim().isEmpty()) {
            throw new BizException(400, "报告内容不能为空");
        }
        if (content.length() > 2000) {
            throw new BizException(400, "报告内容不能超过2000字");
        }
        AppointmentEntity order = appointmentMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(404, "订单不存在");
        }
        if (!order.getTutorId().equals(UserContext.getUid())) {
            throw new BizException(403, "无权操作该订单");
        }
        if (order.getStatus() != 3) {
            throw new BizException(400, "仅已完成订单可提交学习报告");
        }
        // 幂等：已有报告则更新
        StudyReportEntity existing = reportMapper.selectOne(
                new LambdaQueryWrapper<StudyReportEntity>().eq(StudyReportEntity::getOrderId, orderId));
        if (existing != null) {
            existing.setContent(content);
            reportMapper.updateById(existing);
        } else {
            StudyReportEntity report = new StudyReportEntity();
            report.setOrderId(orderId);
            report.setTeacherId(UserContext.getUid());
            report.setStudentId(order.getStudentId());
            report.setContent(content);
            report.setCreateTime(LocalDateTime.now());
            reportMapper.insert(report);
        }
        return ApiResponse.ok();
    }

    @Operation(summary = "学生查看订单的学习报告")
    @GetMapping("/{orderId}")
    @RequireRole({UserContext.ROLE_STUDENT})
    public ApiResponse<StudyReportEntity> getReport(@PathVariable Long orderId) {
        StudyReportEntity report = reportMapper.selectOne(
                new LambdaQueryWrapper<StudyReportEntity>().eq(StudyReportEntity::getOrderId, orderId));
        return ApiResponse.ok(report);
    }
}
