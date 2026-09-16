package com.tutor.platform.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.tutor.platform.entity.TutorEntity;
import com.tutor.platform.vo.TutorVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 家教信息服务：发布、编辑、分页查询、审核
 */
public interface TutorService {

    /** 老师发布家教信息（初始为待审核） */
    Long publish(Long userId, TutorEntity tutor);

    void update(Long userId, TutorEntity tutor);

    /** 前台分页查询已上架信息（多条件筛选） */
    IPage<TutorVO> pageList(long page, long size, Long subjectId,
                            BigDecimal priceMin, BigDecimal priceMax, String keyword);

    TutorVO detail(Long id);

    /** 按 tutor.id 列表批量组装 VO（收藏等场景用） */
    List<TutorVO> listByIds(List<Long> ids);

    /** 管理员审核：0待审核 → 1通过 / 3驳回 */
    void audit(Long id, boolean pass, String reason);

    /** 智能推荐：按科目+价格+评分推荐老师 */
    List<TutorVO> recommend(Long subjectId, BigDecimal priceMax, int limit);
}
