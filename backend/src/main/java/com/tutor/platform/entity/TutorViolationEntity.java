package com.tutor.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 老师违规记录（投诉成立 / 超时未确认 / 频繁拒绝 都会写一条）
 */
@Data
@TableName("tutor_violation")
public class TutorViolationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 老师 user.id */
    private Long tutorId;

    /** 1投诉成立 2超时未确认 3频繁拒绝 */
    private Integer type;

    /** 关联单据类型：complaint / appointment */
    private String refType;

    /** 关联单据 id */
    private Long refId;

    /** 本次扣减信用分（正数表示扣多少） */
    private Integer deduct;

    private String reason;

    /** 操作管理员 user.id，系统执行为 null */
    private Long operatorId;

    /** 1有效 0已撤销 */
    private Integer status;

    private LocalDateTime createTime;
}
