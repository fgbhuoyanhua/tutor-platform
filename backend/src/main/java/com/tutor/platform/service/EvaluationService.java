package com.tutor.platform.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.tutor.platform.dto.EvaluationCreateDTO;
import com.tutor.platform.vo.EvaluationVO;

/**
 * 评价服务：订单完成后学生评分，家教评分实时聚合
 */
public interface EvaluationService {

    /**
     * 学生提交评价（仅已完成订单，每个订单每名学生仅一次）
     */
    void submit(Long studentId, EvaluationCreateDTO dto);

    /**
     * 按老师(user.id)分页查询历史评价
     */
    IPage<EvaluationVO> pageByTutor(Long tutorUserId, long page, long size);
}
