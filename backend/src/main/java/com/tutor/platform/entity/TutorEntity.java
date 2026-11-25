package com.tutor.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 家教信息表
 */
@Data
@TableName("tutor")
public class TutorEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long subjectId;

    private String grade;

    /** 每小时资费（元） */
    private BigDecimal price;

    private String introduce;

    /** 综合评分 0-5 */
    private BigDecimal rating;

    /** 0待审核 1已上架 2已下架 3未通过 */
    private Integer status;

    /** 就读院校（认证通过后写入） */
    private String school;

    /** 专业 */
    private String major;

    /** 年级：大一/大二/大三/大四/研一/研二/研三 */
    private String gradeLevel;

    /** 认证状态：0未提交 1待审 2已认证 3驳回 */
    private Integer certStatus;

    /** 认证通过时间 */
    private LocalDateTime certTime;

    /** 校园邮箱（可选） */
    private String eduEmail;

    /** 信用分：满分 100，违规扣分、完成订单加分 */
    private Integer creditScore;

    /** 违规累计次数 */
    private Integer violationCount;

    private LocalDateTime createTime;
}
