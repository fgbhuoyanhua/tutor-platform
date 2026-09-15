package com.tutor.platform.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 私信会话（与某用户最近一条消息 + 未读数）
 */
@Data
public class ConversationVO {

    private Long otherId;

    private String otherName;

    private String lastContent;

    private LocalDateTime lastTime;

    private Integer unread;
}
