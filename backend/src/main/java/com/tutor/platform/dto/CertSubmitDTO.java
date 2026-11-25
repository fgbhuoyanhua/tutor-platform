package com.tutor.platform.dto;

import lombok.Data;

/**
 * 老师提交资格认证的请求体
 */
@Data
public class CertSubmitDTO {

    /** 就读院校 */
    private String school;

    /** 专业 */
    private String major;

    /** 年级 */
    private String gradeLevel;

    /** 证明类型：1学生证 2学信网在线验证报告 3校园卡 4校园邮箱 5其他 */
    private Integer certType;

    /** 证明编号（学信网报告编号等，类型 2 时必填） */
    private String certNo;

    /** 上传接口返回的文件路径，可为空（线下核验） */
    private String certUrl;

    /** 补充说明 */
    private String note;
}
