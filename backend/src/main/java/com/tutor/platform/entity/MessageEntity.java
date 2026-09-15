package com.tutor.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息表
 */
@Data
@TableName("message")
public class MessageEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long fromId;

    private Long toId;

    /** 1预约 2订单 3系统 */
    private Integer type;

    private String content;

    /** 0未读 1已读 */
    private Integer isRead;

    private LocalDateTime createTime;
}
