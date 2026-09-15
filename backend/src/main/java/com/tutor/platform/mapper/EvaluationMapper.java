package com.tutor.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tutor.platform.entity.EvaluationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface EvaluationMapper extends BaseMapper<EvaluationEntity> {

    /**
     * 批量统计各老师(user.id)的评价数
     */
    @Select("<script>" +
            "SELECT tutor_id AS tutorId, COUNT(*) AS cnt FROM evaluation " +
            "WHERE tutor_id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "GROUP BY tutor_id" +
            "</script>")
    List<Map<String, Object>> countGroupByTutorIds(@Param("ids") List<Long> ids);
}
