package com.tutor.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 课后学习报告表
 */
@Data
@TableName("study_report")
public class StudyReportEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long teacherId;

    private Long studentId;

    /** 学习内容/课堂表现 */
    private String content;

    private LocalDateTime createTime;
}
