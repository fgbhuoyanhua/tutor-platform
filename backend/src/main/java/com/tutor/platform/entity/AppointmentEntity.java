package com.tutor.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 预约订单表
 */
@Data
@TableName("appointment")
public class AppointmentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 唯一订单编号 */
    private String orderNo;

    private Long studentId;

    private Long tutorId;

    private Long subjectId;

    private LocalDate appointDate;

    /** 时间段，如 09:00-11:00 */
    private String timeSlot;

    private BigDecimal totalPrice;

    /** 0待确认 1已预约 2授课中 3已完成 4已取消 5已拒绝 */
    private Integer status;

    /** 0未支付 1已支付 */
    private Integer payStatus;

    private LocalDateTime createTime;
}
