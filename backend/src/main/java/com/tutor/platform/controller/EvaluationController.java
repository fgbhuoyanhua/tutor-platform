package com.tutor.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.tutor.platform.common.ApiResponse;
import com.tutor.platform.common.BizException;
import com.tutor.platform.common.PageResult;
import com.tutor.platform.common.UserContext;
import com.tutor.platform.dto.EvaluationCreateDTO;
import com.tutor.platform.entity.EvaluationEntity;
import com.tutor.platform.mapper.EvaluationMapper;
import com.tutor.platform.security.RequireRole;
import com.tutor.platform.service.EvaluationService;
import com.tutor.platform.service.MessageService;
import com.tutor.platform.vo.EvaluationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 评价接口：学生提交评价，公开查询老师历史评价
 */
@Tag(name = "评价")
@RestController
@RequestMapping("/api/evaluations")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService evaluationService;
    private final EvaluationMapper evaluationMapper;
    private final MessageService messageService;

    @Operation(summary = "学生提交评价（仅已完成订单）")
    @PostMapping
    @RequireRole({UserContext.ROLE_STUDENT})
    public ApiResponse<Void> submit(@Valid @RequestBody EvaluationCreateDTO dto) {
        evaluationService.submit(UserContext.getUid(), dto);
        return ApiResponse.ok();
    }

    @Operation(summary = "老师历史评价列表（公开，分页）")
    @GetMapping("/tutor/{userId}")
    public ApiResponse<PageResult<EvaluationVO>> listByTutor(@PathVariable Long userId,
                                                             @RequestParam(defaultValue = "1") long page,
                                                             @RequestParam(defaultValue = "10") long size) {
        IPage<EvaluationVO> p = evaluationService.pageByTutor(userId, page, size);
        return ApiResponse.ok(PageResult.of(p));
    }

    @Operation(summary = "老师回复评价")
    @PutMapping("/{id}/reply")
    @RequireRole({UserContext.ROLE_TUTOR})
    public ApiResponse<Void> reply(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        EvaluationEntity e = evaluationMapper.selectById(id);
        if (e == null) {
            throw new BizException(404, "评价不存在");
        }
        if (!e.getTutorId().equals(UserContext.getUid())) {
            throw new BizException(403, "无权回复该评价");
        }
        String content = body.get("reply");
        if (content == null || content.trim().isEmpty()) {
            throw new BizException(400, "回复内容不能为空");
        }
        if (content.length() > 500) {
            throw new BizException(400, "回复不能超过500字");
        }
        e.setReply(content);
        evaluationMapper.updateById(e);
        messageService.send(UserContext.getUid(), e.getStudentId(), 3,
                "老师回复了您的评价：" + content);
        return ApiResponse.ok();
    }
}
