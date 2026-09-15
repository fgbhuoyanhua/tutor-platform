package com.tutor.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tutor.platform.common.ApiResponse;
import com.tutor.platform.entity.SubjectEntity;
import com.tutor.platform.mapper.SubjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 科目分类接口：公开查询顶级科目，供前端筛选与下单选择
 */
@Tag(name = "科目")
@RestController
@RequestMapping("/api/subjects")
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectMapper subjectMapper;

    @Operation(summary = "科目分类列表（公开）")
    @GetMapping
    public ApiResponse<List<SubjectEntity>> list() {
        return ApiResponse.ok(subjectMapper.selectList(new LambdaQueryWrapper<SubjectEntity>()
                .eq(SubjectEntity::getParentId, 0)
                .orderByAsc(SubjectEntity::getSort)));
    }
}
