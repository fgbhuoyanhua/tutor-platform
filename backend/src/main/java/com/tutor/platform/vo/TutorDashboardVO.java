package com.tutor.platform.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 老师端数据看板汇总视图对象
 */
@Data
@Builder
public class TutorDashboardVO {

    /** 累计收入（已完成订单） */
    private BigDecimal totalIncome;

    /** 累计订单数 */
    private Long totalOrders;

    /** 已完成订单数 */
    private Long completedOrders;

    /** 待确认订单数 */
    private Long pendingOrders;

    /** 近6个月收入趋势 */
    private List<IncomeVO> incomeTrend;

    /** 学科收入分布 */
    private List<SubjectIncomeVO> subjectIncome;

    /** 订单状态分布 */
    private List<StatusCountVO> statusDistribution;

    @Data
    @Builder
    public static class SubjectIncomeVO {
        private Long subjectId;
        private String subjectName;
        private BigDecimal income;
        private Long orderCount;
    }

    @Data
    @Builder
    public static class StatusCountVO {
        private Integer status;
        private String statusName;
        private Long count;
    }
}
