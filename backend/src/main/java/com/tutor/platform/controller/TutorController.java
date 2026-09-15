package com.tutor.platform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.tutor.platform.common.ApiResponse;
import com.tutor.platform.common.PageResult;
import com.tutor.platform.common.UserContext;
import com.tutor.platform.entity.TutorEntity;
import com.tutor.platform.security.RequireRole;
import com.tutor.platform.service.OrderService;
import com.tutor.platform.service.TutorService;
import com.tutor.platform.vo.IncomeVO;
import com.tutor.platform.vo.MyStudentVO;
import com.tutor.platform.vo.TutorVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 家教信息接口：公开查询已上架列表/详情，老师发布/编辑/收入统计
 */
@Tag(name = "家教信息")
@RestController
@RequestMapping("/api/tutors")
@RequiredArgsConstructor
public class TutorController {

    private final TutorService tutorService;
    private final OrderService orderService;

    @Operation(summary = "分页+多条件查询家教信息（公开）")
    @GetMapping
    public ApiResponse<PageResult<TutorVO>> page(@RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "10") long size,
                                                 @RequestParam(required = false) Long subjectId,
                                                 @RequestParam(required = false) BigDecimal priceMin,
                                                 @RequestParam(required = false) BigDecimal priceMax,
                                                 @RequestParam(required = false) String keyword) {
        IPage<TutorVO> p = tutorService.pageList(page, size, subjectId, priceMin, priceMax, keyword);
        return ApiResponse.ok(PageResult.of(p));
    }

    @Operation(summary = "老师收入统计（按月，仅已完成订单）")
    @GetMapping("/income")
    @RequireRole({UserContext.ROLE_TUTOR})
    public ApiResponse<IncomeVO> income(@RequestParam String month) {
        return ApiResponse.ok(orderService.incomeByMonth(UserContext.getUid(), month));
    }

    @Operation(summary = "老师近6个月收入趋势")
    @GetMapping("/income/trend")
    @RequireRole({UserContext.ROLE_TUTOR})
    public ApiResponse<java.util.List<IncomeVO>> incomeTrend() {
        return ApiResponse.ok(orderService.incomeLast6Months(UserContext.getUid()));
    }

    @Operation(summary = "老师的我的学生列表")
    @GetMapping("/my-students")
    @RequireRole({UserContext.ROLE_TUTOR})
    public ApiResponse<java.util.List<MyStudentVO>> myStudents() {
        return ApiResponse.ok(orderService.myStudents(UserContext.getUid()));
    }

    @Operation(summary = "家教信息详情（公开）")
    @GetMapping("/{id}")
    public ApiResponse<TutorVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(tutorService.detail(id));
    }

    @Operation(summary = "老师发布家教信息")
    @PostMapping
    @RequireRole({UserContext.ROLE_TUTOR})
    public ApiResponse<Long> publish(@RequestBody TutorEntity tutor) {
        return ApiResponse.ok(tutorService.publish(UserContext.getUid(), tutor));
    }

    @Operation(summary = "老师编辑家教信息（重新进入待审核）")
    @PutMapping("/{id}")
    @RequireRole({UserContext.ROLE_TUTOR})
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody TutorEntity tutor) {
        tutor.setId(id);
        tutorService.update(UserContext.getUid(), tutor);
        return ApiResponse.ok();
    }
}
