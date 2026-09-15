package com.tutor.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生收藏老师
 */
@Data
@TableName("favorite")
public class FavoriteEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long studentId;

    private Long tutorId;

    private LocalDateTime createTime;
}
