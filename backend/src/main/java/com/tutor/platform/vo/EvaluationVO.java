package com.tutor.platform.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评价视图对象（列表展示用，含评价人姓名）
 */
@Data
@Builder
public class EvaluationVO {

    private Long id;

    private Long orderId;

    private Long studentId;

    private String studentName;

    private Integer score;

    private String content;

    private LocalDateTime createTime;
}
