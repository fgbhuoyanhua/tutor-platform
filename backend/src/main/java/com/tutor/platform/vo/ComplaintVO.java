package com.tutor.platform.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 投诉举报（管理端待处理/历史列表、学生端我的投诉共用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintVO {

    private Long id;

    private Long complainantId;

    private String complainantName;

    private Long tutorId;

    private String tutorName;

    private Long orderId;

    private String orderNo;

    private Integer type;

    private String typeLabel;

    private String content;

    private String evidenceUrl;

    /** 0待处理 1成立 2驳回 */
    private Integer status;

    private String statusLabel;

    private Long handlerId;

    private String handleNote;

    private LocalDateTime handlerTime;

    private Integer deductScore;

    private LocalDateTime createTime;
}
