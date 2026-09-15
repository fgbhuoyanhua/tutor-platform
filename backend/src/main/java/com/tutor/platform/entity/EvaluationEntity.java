package com.tutor.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评价表
 */
@Data
@TableName("evaluation")
public class EvaluationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long studentId;

    private Long tutorId;

    /** 评分1-5 */
    private Integer score;

    private String content;

    private LocalDateTime createTime;
}
