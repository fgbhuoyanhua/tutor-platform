package com.tutor.platform.service;

import com.tutor.platform.entity.MessageEntity;
import com.tutor.platform.vo.ConversationVO;

import java.util.List;

/**
 * 消息服务：预约、订单、审核等节点的站内消息
 */
public interface MessageService {

    void send(Long fromId, Long toId, Integer type, String content);

    /** 与某用户的双向私信记录（升序，进入对话时自动已读对方消息） */
    List<MessageEntity> conversation(Long uid, Long otherId);

    /** 我的私信会话列表（按最近消息排序） */
    List<ConversationVO> conversations(Long uid);

    List<MessageEntity> listByUser(Long uid);

    int unreadCount(Long uid);

    void markRead(Long uid);

    void markOneRead(Long uid, Long msgId);

    void deleteRead(Long uid);
}
