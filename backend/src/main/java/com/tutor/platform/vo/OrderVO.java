package com.tutor.platform.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 预约订单视图对象
 */
@Data
@Builder
public class OrderVO {

    private Long id;

    private String orderNo;

    private Long studentId;

    private String studentName;

    private Long tutorId;

    private String tutorName;

    private Long subjectId;

    private String subjectName;

    private LocalDate appointDate;

    private String timeSlot;

    private BigDecimal totalPrice;

    /** 0待确认 1已预约 2授课中 3已完成 4已取消 5已拒绝 */
    private Integer status;

    private LocalDateTime createTime;
}
