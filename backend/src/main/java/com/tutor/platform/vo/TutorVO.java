package com.tutor.platform.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 家教信息视图对象（列表/详情通用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TutorVO {

    private Long id;

    private Long userId;

    private String tutorName;

    private String avatar;

    private Long subjectId;

    private String subjectName;

    private String grade;

    private BigDecimal price;

    private BigDecimal rating;

    /** 历史评价数 */
    private Long evaluateCount;

    /** 已完成订单数（授课经验） */
    private Long finishedOrderCount;

    private String introduce;

    /** 0待审核 1已上架 2已下架 3未通过 */
    private Integer status;

    /** 就读院校（认证通过后展示） */
    private String school;

    /** 专业 */
    private String major;

    /** 年级 */
    private String gradeLevel;

    /** 认证状态：0未提交 1待审 2已认证 3驳回 */
    private Integer certStatus;

    /** 认证通过时间 */
    private LocalDateTime certTime;

    private LocalDateTime createTime;
}
