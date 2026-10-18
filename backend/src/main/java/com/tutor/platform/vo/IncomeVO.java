package com.tutor.platform.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 老师收入统计视图对象（按月）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncomeVO {

    /** 统计月份 yyyy-MM */
    private String month;

    /** 已完成订单数 */
    private Long orderCount;

    /** 收入合计（元） */
    private BigDecimal totalIncome;
}
