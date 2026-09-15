package com.tutor.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 调课申请
 */
@Data
@TableName("reschedule")
public class RescheduleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long studentId;

    private LocalDate oldDate;

    private String oldSlot;

    private LocalDate newDate;

    private String newSlot;

    /** 0待同意 1已同意 2已拒绝 */
    private Integer status;

    private LocalDateTime createTime;
}
