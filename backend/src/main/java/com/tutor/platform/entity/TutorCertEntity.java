package com.tutor.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 老师资格认证记录表
 * 注意：pending_flag 是数据库生成列（status=0 时为 1），仅在唯一索引里使用，实体不映射，
 * 否则 insert 时会尝试向生成列写值而报错。
 */
@Data
@TableName("tutor_cert")
public class TutorCertEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 老师 user.id */
    private Long tutorId;

    private String school;

    private String major;

    private String gradeLevel;

    /** 证明类型：1学生证 2学信网在线验证报告 3校园卡 4校园邮箱 5其他 */
    private Integer certType;

    /** 证明编号（学信网报告编号等） */
    private String certNo;

    /** 证明文件访问路径（私有，需鉴权） */
    private String certUrl;

    private String note;

    /** 0待审 1通过 2驳回 */
    private Integer status;

    private Long reviewerId;

    private String reviewNote;

    private LocalDateTime reviewTime;

    private LocalDateTime createTime;
}
