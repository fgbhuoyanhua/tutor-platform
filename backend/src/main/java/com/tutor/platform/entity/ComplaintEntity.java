package com.tutor.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 投诉举报。pending_flag 是生成列，实体不能映射（写了会报错）。
 */
@Data
@TableName("complaint")
public class ComplaintEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 投诉人 user.id */
    private Long complainantId;

    /** 被投诉老师 user.id */
    private Long tutorId;

    /** 关联订单 appointment.id（可选） */
    private Long orderId;

    /** 1爽约 2私改价格 3骚扰辱骂 4虚假信息 5其他 */
    private Integer type;

    private String content;

    private String evidenceUrl;

    /** 0待处理 1成立 2驳回 */
    private Integer status;

    private Long handlerId;

    private String handleNote;

    private LocalDateTime handlerTime;

    /** 成立时扣减的信用分 */
    private Integer deductScore;

    private LocalDateTime createTime;
}
