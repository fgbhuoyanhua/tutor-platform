package com.tutor.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tutor.platform.entity.AppointmentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface AppointmentMapper extends BaseMapper<AppointmentEntity> {

    /**
     * 状态条件更新：仅当订单处于 fromStatus 时才更新为 toStatus，防止并发重复接单。
     * @return 影响行数，0 表示状态已变化或无权操作
     */
    @Update("UPDATE appointment SET status = #{toStatus} " +
            "WHERE id = #{id} AND status = #{fromStatus} " +
            "AND (tutor_id = #{operatorId} OR student_id = #{operatorId})")
    int updateStatusIf(@Param("id") Long id,
                       @Param("fromStatus") Integer fromStatus,
                       @Param("toStatus") Integer toStatus,
                       @Param("operatorId") Long operatorId);

    /**
     * 查询同一老师同一日期同一时间段的未结束订单数（用于时段冲突校验）
     */
    @Select("SELECT COUNT(*) FROM appointment " +
            "WHERE tutor_id = #{tutorId} AND appoint_date = #{date} AND time_slot = #{timeSlot} " +
            "AND status IN (0, 1, 2)")
    long countConflict(@Param("tutorId") Long tutorId,
                       @Param("date") java.time.LocalDate date,
                       @Param("timeSlot") String timeSlot);

    /**
     * 批量统计各老师(user.id)的已完成订单数（授课经验，供列表展示）
     */
    @Select("<script>" +
            "SELECT tutor_id AS tutorId, COUNT(*) AS cnt FROM appointment " +
            "WHERE status = 3 AND tutor_id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "GROUP BY tutor_id" +
            "</script>")
    List<Map<String, Object>> countFinishedGroupByTutorIds(@Param("ids") List<Long> ids);

    /**
     * 按月统计老师已完成订单数与收入合计
     */
    @Select("SELECT COUNT(*) AS cnt, COALESCE(SUM(total_price), 0) AS amount " +
            "FROM appointment " +
            "WHERE tutor_id = #{tutorId} AND status = 3 " +
            "AND DATE_FORMAT(appoint_date, '%Y-%m') = #{month}")
    Map<String, Object> sumIncomeByMonth(@Param("tutorId") Long tutorId,
                                         @Param("month") String month);

    /**
     * 近6个月每月收入（老师端收入页柱状图）
     */
    @Select("SELECT DATE_FORMAT(appoint_date, '%Y-%m') AS month, COUNT(*) AS cnt, " +
            "COALESCE(SUM(total_price), 0) AS amount " +
            "FROM appointment " +
            "WHERE tutor_id = #{tutorId} AND status = 3 " +
            "AND appoint_date >= DATE_SUB(CURDATE(), INTERVAL 5 MONTH) " +
            "GROUP BY DATE_FORMAT(appoint_date, '%Y-%m') " +
            "ORDER BY month")
    List<Map<String, Object>> sumIncomeLast6Months(@Param("tutorId") Long tutorId);

    /**
     * 老师视角：按学生聚合订单（我的学生列表）
     */
    @Select("SELECT student_id AS student_id, COUNT(*) AS order_count, " +
            "MAX(appoint_date) AS last_date, SUM(CASE WHEN status = 3 THEN 1 ELSE 0 END) AS finished_count " +
            "FROM appointment " +
            "WHERE tutor_id = #{tutorId} AND status IN (1,2,3) " +
            "GROUP BY student_id " +
            "ORDER BY last_date DESC")
    List<Map<String, Object>> myStudents(@Param("tutorId") Long tutorId);
}
