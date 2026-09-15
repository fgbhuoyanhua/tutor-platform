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

    private LocalDateTime createTime;
}
