package com.tutor.platform.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 老师视角"我的学生"聚合视图
 */
@Data
@Builder
public class MyStudentVO {

    private Long studentId;

    private String studentName;

    /** 累计有效订单数（已预约/授课中/已完成） */
    private Long orderCount;

    /** 已完成课时数 */
    private Long finishedCount;

    /** 最近一次上课日期 */
    private String lastDate;
}
