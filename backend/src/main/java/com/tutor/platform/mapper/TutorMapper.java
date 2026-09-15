package com.tutor.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tutor.platform.entity.TutorEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TutorMapper extends BaseMapper<TutorEntity> {

    /**
     * 按老师 user.id 实时重算综合评分（取该老师全部评价的平均分）
     */
    @Update("UPDATE tutor SET rating = " +
            "COALESCE((SELECT ROUND(AVG(e.score), 2) FROM evaluation e WHERE e.tutor_id = #{userId}), 5.00) " +
            "WHERE user_id = #{userId}")
    int updateRatingByUserId(@Param("userId") Long userId);
}
