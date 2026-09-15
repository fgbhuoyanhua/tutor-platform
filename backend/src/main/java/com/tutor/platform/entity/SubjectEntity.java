package com.tutor.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 科目分类表
 */
@Data
@TableName("subject")
public class SubjectEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 父分类ID，0为顶级 */
    private Long parentId;

    private Integer sort;
}
