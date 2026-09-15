package com.tutor.platform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.tutor.platform.common.ApiResponse;
import com.tutor.platform.common.PageResult;
import com.tutor.platform.common.UserContext;
import com.tutor.platform.dto.AppointmentCreateDTO;
import com.tutor.platform.dto.RescheduleDTO;
import com.tutor.platform.security.RequireRole;
import com.tutor.platform.service.OrderService;
import com.tutor.platform.vo.OrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 预约订单接口：学生提交/取消/完成，老师接单/拒绝，双方查列表
 */
@Tag(name = "预约订单")
@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "我的订单列表（按角色：学生看发的，老师看收到的）")
    @GetMapping
    public ApiResponse<PageResult<OrderVO>> list(@RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "10") long size) {
        IPage<OrderVO> p = orderService.pageList(UserContext.getUid(), UserContext.getRole(), page, size);
        return ApiResponse.ok(PageResult.of(p));
    }

    @Operation(summary = "学生提交预约")
    @PostMapping
    @RequireRole({UserContext.ROLE_STUDENT})
    public ApiResponse<Long> create(@Valid @RequestBody AppointmentCreateDTO dto) {
        return ApiResponse.ok(orderService.create(UserContext.getUid(), dto));
    }

    @Operation(summary = "老师接单确认")
    @PutMapping("/{id}/confirm")
    @RequireRole({UserContext.ROLE_TUTOR})
    public ApiResponse<Void> confirm(@PathVariable Long id) {
        orderService.confirm(id, UserContext.getUid());
        return ApiResponse.ok();
    }

    @Operation(summary = "老师拒绝预约")
    @PutMapping("/{id}/reject")
    @RequireRole({UserContext.ROLE_TUTOR})
    public ApiResponse<Void> reject(@PathVariable Long id) {
        orderService.reject(id, UserContext.getUid());
        return ApiResponse.ok();
    }

    @Operation(summary = "老师开始授课（已预约→授课中）")
    @PutMapping("/{id}/start")
    @RequireRole({UserContext.ROLE_TUTOR})
    public ApiResponse<Void> start(@PathVariable Long id) {
        orderService.start(id, UserContext.getUid());
        return ApiResponse.ok();
    }

    @Operation(summary = "学生取消预约（仅待确认）")
    @PutMapping("/{id}/cancel")
    @RequireRole({UserContext.ROLE_STUDENT})
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        orderService.cancel(id, UserContext.getUid());
        return ApiResponse.ok();
    }

    @Operation(summary = "学生确认完成授课")
    @PutMapping("/{id}/finish")
    @RequireRole({UserContext.ROLE_STUDENT})
    public ApiResponse<Void> finish(@PathVariable Long id) {
        orderService.finish(id, UserContext.getUid());
        return ApiResponse.ok();
    }

    @Operation(summary = "学生申请调课")
    @PostMapping("/{id}/reschedule")
    @RequireRole({UserContext.ROLE_STUDENT})
    public ApiResponse<Void> rescheduleRequest(@PathVariable Long id, @Valid @RequestBody RescheduleDTO dto) {
        orderService.rescheduleRequest(id, UserContext.getUid(), dto.getNewDate(), dto.getNewSlot());
        return ApiResponse.ok();
    }

    @Operation(summary = "老师同意调课")
    @PutMapping("/{id}/reschedule/accept")
    @RequireRole({UserContext.ROLE_TUTOR})
    public ApiResponse<Void> rescheduleAccept(@PathVariable Long id) {
        orderService.rescheduleAccept(id, UserContext.getUid());
        return ApiResponse.ok();
    }

    @Operation(summary = "老师拒绝调课")
    @PutMapping("/{id}/reschedule/reject")
    @RequireRole({UserContext.ROLE_TUTOR})
    public ApiResponse<Void> rescheduleReject(@PathVariable Long id) {
        orderService.rescheduleReject(id, UserContext.getUid());
        return ApiResponse.ok();
    }
}
