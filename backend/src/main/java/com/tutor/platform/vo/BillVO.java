package com.tutor.platform.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 课时账单：按月汇总 + 逐笔明细（导出 CSV 直接用它）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillVO {

    /** 账期 yyyy-MM */
    private String month;

    /** 已完成节数 */
    private int finishedCount;

    /** 已完成总时长（分钟） */
    private int finishedMinutes;

    /** 已完成总金额（元） */
    private BigDecimal totalAmount;

    /** 待确认/已预约/授课中的订单数（未结算） */
    private int pendingCount;

    private List<BillItemVO> items;
}
