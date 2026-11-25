package com.tutor.platform.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 老师违规记录（管理端「违规记录」页用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TutorViolationVO {

    private Long id;

    /** 老师 user.id */
    private Long tutorId;

    private String tutorName;

    /** 1投诉成立 2超时未确认 3频繁拒绝 */
    private Integer type;

    private String typeLabel;

    /** 关联单据类型：complaint / appointment */
    private String refType;

    private Long refId;

    /** 本次扣减信用分（正数） */
    private Integer deduct;

    private String reason;

    private Long operatorId;

    private String operatorName;

    /** 1有效 0已撤销 */
    private Integer status;

    private LocalDateTime createTime;
}
