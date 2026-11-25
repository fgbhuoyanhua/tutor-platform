package com.tutor.platform.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 课时账单行：订单快照 + 老师视角汇总。
 * 金额、单价全部取自订单表快照，不受老师之后改价影响。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillItemVO {

    private Long orderId;

    private String orderNo;

    /** 学生姓名（已脱敏） */
    private String studentName;

    private String subjectName;

    /** 授课日期 yyyy-MM-dd */
    private String appointDate;

    private String timeSlot;

    /** 时长（分钟），按时间段推算 */
    private Integer durationMinutes;

    /** 订单快照单价 */
    private BigDecimal unitPrice;

    /** 订单快照金额 */
    private BigDecimal amount;

    /** 订单状态：0待确认 1已预约 2授课中 3已完成 4已取消 5已拒绝 */
    private Integer status;

    private String statusName;
}
