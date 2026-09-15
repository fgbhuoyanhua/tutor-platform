package com.tutor.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tutor.platform.common.ApiResponse;
import com.tutor.platform.common.PageResult;
import com.tutor.platform.common.UserContext;
import com.tutor.platform.entity.AppointmentEntity;
import com.tutor.platform.entity.AdminLogEntity;
import com.tutor.platform.entity.TutorEntity;
import com.tutor.platform.entity.UserEntity;
import com.tutor.platform.mapper.AdminLogMapper;
import com.tutor.platform.mapper.AppointmentMapper;
import com.tutor.platform.mapper.SubjectMapper;
import com.tutor.platform.mapper.TutorMapper;
import com.tutor.platform.mapper.UserMapper;
import com.tutor.platform.security.AdminLog;
import com.tutor.platform.security.RequireRole;
import com.tutor.platform.service.OrderService;
import com.tutor.platform.service.TutorService;
import com.tutor.platform.vo.OrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员接口：审核家教信息、用户管理、订单监管、数据统计（全角色双端登录，管理功能 Web/移动端均可触达）
 */
@Tag(name = "管理员")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final TutorService tutorService;
    private final OrderService orderService;
    private final UserMapper userMapper;
    private final TutorMapper tutorMapper;
    private final AppointmentMapper appointmentMapper;
    private final AdminLogMapper adminLogMapper;
    private final SubjectMapper subjectMapper;

    @Operation(summary = "全量订单监管（可按状态筛选）")
    @GetMapping("/orders")
    @RequireRole({UserContext.ROLE_ADMIN})
    public ApiResponse<PageResult<OrderVO>> orders(@RequestParam(required = false) Integer status,
                                                   @RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "10") long size) {
        IPage<OrderVO> p = orderService.adminPage(status, page, size);
        return ApiResponse.ok(PageResult.of(p));
    }

    @Operation(summary = "新增科目分类")
    @PostMapping("/subjects")
    @RequireRole({UserContext.ROLE_ADMIN})
    @AdminLog(action = "新增科目", targetType = "subject", detail = "#name")
    public ApiResponse<Long> addSubject(@RequestParam String name,
                                        @RequestParam(defaultValue = "0") long parentId,
                                        @RequestParam(defaultValue = "0") int sort) {
        if (name == null || name.isBlank()) {
            throw new com.tutor.platform.common.BizException(400, "科目名称不能为空");
        }
        if (name.length() > 50) {
            throw new com.tutor.platform.common.BizException(400, "科目名称不能超过50字");
        }
        Long exists = subjectMapper.selectCount(new LambdaQueryWrapper<com.tutor.platform.entity.SubjectEntity>()
                .eq(com.tutor.platform.entity.SubjectEntity::getName, name.trim()));
        if (exists != null && exists > 0) {
            throw new com.tutor.platform.common.BizException(400, "科目名称已存在");
        }
        com.tutor.platform.entity.SubjectEntity s = new com.tutor.platform.entity.SubjectEntity();
        s.setName(name.trim());
        s.setParentId(parentId);
        s.setSort(sort);
        subjectMapper.insert(s);
        return ApiResponse.ok(s.getId());
    }

    @Operation(summary = "修改科目分类")
    @PutMapping("/subjects/{id}")
    @RequireRole({UserContext.ROLE_ADMIN})
    @AdminLog(action = "修改科目", targetType = "subject", target = "#id",
            detail = "#name == null ? '修改' : '重命名为：' + #name")
    public ApiResponse<Void> updateSubject(@PathVariable Long id,
                                           @RequestParam(required = false) String name,
                                           @RequestParam(required = false) Integer sort) {
        com.tutor.platform.entity.SubjectEntity old = subjectMapper.selectById(id);
        if (old == null) {
            throw new com.tutor.platform.common.BizException(404, "科目不存在");
        }
        if (name != null) {
            String n = name.trim();
            if (n.isEmpty() || n.length() > 50) {
                throw new com.tutor.platform.common.BizException(400, "科目名称需为1-50字");
            }
            Long exists = subjectMapper.selectCount(new LambdaQueryWrapper<com.tutor.platform.entity.SubjectEntity>()
                    .eq(com.tutor.platform.entity.SubjectEntity::getName, n)
                    .ne(com.tutor.platform.entity.SubjectEntity::getId, id));
            if (exists != null && exists > 0) {
                throw new com.tutor.platform.common.BizException(400, "科目名称已存在");
            }
            old.setName(n);
        }
        if (sort != null) {
            old.setSort(sort);
        }
        subjectMapper.updateById(old);
        return ApiResponse.ok();
    }

    @Operation(summary = "删除科目分类（被家教引用时禁止删除）")
    @DeleteMapping("/subjects/{id}")
    @RequireRole({UserContext.ROLE_ADMIN})
    @AdminLog(action = "删除科目", targetType = "subject", target = "#id", detail = "删除科目")
    public ApiResponse<Void> deleteSubject(@PathVariable Long id) {
        com.tutor.platform.entity.SubjectEntity old = subjectMapper.selectById(id);
        if (old == null) {
            throw new com.tutor.platform.common.BizException(404, "科目不存在");
        }
        Long child = subjectMapper.selectCount(new LambdaQueryWrapper<com.tutor.platform.entity.SubjectEntity>()
                .eq(com.tutor.platform.entity.SubjectEntity::getParentId, id));
        if (child != null && child > 0) {
            throw new com.tutor.platform.common.BizException(400, "该科目存在子分类，无法删除");
        }
        Long ref = tutorMapper.selectCount(new LambdaQueryWrapper<TutorEntity>()
                .eq(TutorEntity::getSubjectId, id));
        if (ref != null && ref > 0) {
            throw new com.tutor.platform.common.BizException(400, "该科目已被" + ref + "条家教信息引用，无法删除");
        }
        subjectMapper.deleteById(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "审核家教信息（通过/驳回）")
    @PutMapping("/tutors/{id}/audit")
    @RequireRole({UserContext.ROLE_ADMIN})
    @AdminLog(action = "审核家教", targetType = "tutor", target = "#id",
            detail = "#pass ? '审核通过' : '审核驳回'" +
                    " + (#reason != null && #reason.length() > 0 ? '，原因：' + #reason : '')")
    public ApiResponse<Void> audit(@PathVariable Long id,
                                   @RequestParam boolean pass,
                                   @RequestParam(required = false) String reason) {
        tutorService.audit(id, pass, reason);
        return ApiResponse.ok();
    }

    @Operation(summary = "待审核家教信息列表")
    @GetMapping("/tutors/pending")
    @RequireRole({UserContext.ROLE_ADMIN})
    public ApiResponse<List<TutorEntity>> pendingTutors() {
        return ApiResponse.ok(tutorMapper.selectList(new LambdaQueryWrapper<TutorEntity>()
                .eq(TutorEntity::getStatus, 0)));
    }

    @Operation(summary = "用户列表")
    @GetMapping("/users")
    @RequireRole({UserContext.ROLE_ADMIN})
    public ApiResponse<PageResult<UserEntity>> users(@RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "10") long size) {
        IPage<UserEntity> p = userMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<UserEntity>().orderByDesc(UserEntity::getCreateTime));
        return ApiResponse.ok(PageResult.of(p));
    }

    @Operation(summary = "用户管理：禁用/启用")
    @PutMapping("/users/{id}/status")
    @RequireRole({UserContext.ROLE_ADMIN})
    @AdminLog(action = "用户状态变更", targetType = "user", target = "#id",
            detail = "#status == 1 ? '启用用户' : '禁用用户'")
    public ApiResponse<Void> changeUserStatus(@PathVariable Long id, @RequestParam Integer status) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setStatus(status);
        userMapper.updateById(u);
        return ApiResponse.ok();
    }

    @Operation(summary = "管理员操作日志")
    @GetMapping("/logs")
    @RequireRole({UserContext.ROLE_ADMIN})
    public ApiResponse<PageResult<AdminLogEntity>> logs(@RequestParam(defaultValue = "1") long page,
                                                        @RequestParam(defaultValue = "10") long size) {
        IPage<AdminLogEntity> p = adminLogMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<AdminLogEntity>().orderByDesc(AdminLogEntity::getCreateTime));
        return ApiResponse.ok(PageResult.of(p));
    }

    @Operation(summary = "平台数据统计")
    @GetMapping("/stats")
    @RequireRole({UserContext.ROLE_ADMIN})
    public ApiResponse<Map<String, Object>> stats() {
        Map<String, Object> m = new HashMap<>();
        m.put("userCount", userMapper.selectCount(null));
        m.put("tutorCount", tutorMapper.selectCount(null));
        m.put("orderCount", appointmentMapper.selectCount(null));
        m.put("finishedOrderCount", appointmentMapper.selectCount(new LambdaQueryWrapper<AppointmentEntity>()
                .eq(AppointmentEntity::getStatus, 3)));
        return ApiResponse.ok(m);
    }
}
