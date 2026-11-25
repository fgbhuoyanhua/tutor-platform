package com.tutor.platform.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 老师资格认证视图对象（老师端"我的认证"与管理员待审列表共用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TutorCertVO {

    private Long id;

    /** 老师 user.id */
    private Long tutorId;

    private String tutorName;

    /** 手机号（管理员端脱敏展示） */
    private String phone;

    private String school;

    private String major;

    private String gradeLevel;

    private Integer certType;

    /** 证明类型中文名 */
    private String certTypeLabel;

    private String certNo;

    private String certUrl;

    private String note;

    /** 0待审 1通过 2驳回 */
    private Integer status;

    private String reviewNote;

    private LocalDateTime reviewTime;

    private LocalDateTime createTime;

    /** 该老师当前的整体认证状态：0未提交 1待审 2已认证 3驳回 */
    private Integer certStatus;
}
