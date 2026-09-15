package com.tutor.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tutor.platform.entity.MessageEntity;
import com.tutor.platform.entity.UserEntity;
import com.tutor.platform.mapper.MessageMapper;
import com.tutor.platform.mapper.UserMapper;
import com.tutor.platform.service.MessageService;
import com.tutor.platform.vo.ConversationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageMapper messageMapper;
    private final UserMapper userMapper;

    @Override
    public void send(Long fromId, Long toId, Integer type, String content) {
        MessageEntity m = new MessageEntity();
        m.setFromId(fromId);
        m.setToId(toId);
        m.setType(type);
        m.setContent(content);
        m.setIsRead(0);
        m.setCreateTime(LocalDateTime.now());
        messageMapper.insert(m);
    }

    @Override
    public List<MessageEntity> conversation(Long uid, Long otherId) {
        // 只拉私信（type=4），订单通知不进聊天记录
        List<MessageEntity> list = messageMapper.selectList(new LambdaQueryWrapper<MessageEntity>()
                .eq(MessageEntity::getType, 4)
                .and(w -> w.eq(MessageEntity::getFromId, uid).eq(MessageEntity::getToId, otherId)
                        .or(o -> o.eq(MessageEntity::getFromId, otherId).eq(MessageEntity::getToId, uid)))
                .orderByAsc(MessageEntity::getCreateTime)
                .last("limit 200"));
        // 进入对话：把对方发来、我未读的私信标记已读
        MessageEntity upd = new MessageEntity();
        upd.setIsRead(1);
        messageMapper.update(upd, new LambdaQueryWrapper<MessageEntity>()
                .eq(MessageEntity::getToId, uid)
                .eq(MessageEntity::getFromId, otherId)
                .eq(MessageEntity::getType, 4)
                .eq(MessageEntity::getIsRead, 0));
        return list;
    }

    @Override
    public List<ConversationVO> conversations(Long uid) {
        List<MessageEntity> msgs = messageMapper.selectList(new LambdaQueryWrapper<MessageEntity>()
                .eq(MessageEntity::getType, 4)
                .and(w -> w.eq(MessageEntity::getFromId, uid).or().eq(MessageEntity::getToId, uid))
                .orderByDesc(MessageEntity::getCreateTime));
        // 按对端分组，每组取最新一条；msgs 已按时间倒序，插入序即最新在前
        Map<Long, ConversationVO> map = new LinkedHashMap<>();
        for (MessageEntity m : msgs) {
            Long otherId = m.getFromId().equals(uid) ? m.getToId() : m.getFromId();
            ConversationVO cv = map.computeIfAbsent(otherId, k -> {
                ConversationVO v = new ConversationVO();
                v.setOtherId(k);
                v.setUnread(0);
                return v;
            });
            if (cv.getLastTime() == null || m.getCreateTime().isAfter(cv.getLastTime())) {
                cv.setLastContent(m.getContent());
                cv.setLastTime(m.getCreateTime());
            }
            if (m.getToId().equals(uid) && m.getIsRead() == 0) {
                cv.setUnread(cv.getUnread() + 1);
            }
        }
        // 批量补姓名
        List<Long> ids = new ArrayList<>(map.keySet());
        if (!ids.isEmpty()) {
            Map<Long, String> names = userMapper.selectBatchIds(ids).stream()
                    .collect(Collectors.toMap(UserEntity::getId, u -> u.getRealName() != null ? u.getRealName() : "用户" + u.getId()));
            map.forEach((k, v) -> v.setOtherName(names.getOrDefault(k, "用户" + k)));
        }
        return new ArrayList<>(map.values());
    }

    @Override
    public List<MessageEntity> listByUser(Long uid) {
        return messageMapper.selectList(new LambdaQueryWrapper<MessageEntity>()
                .eq(MessageEntity::getToId, uid)
                .orderByDesc(MessageEntity::getCreateTime));
    }

    @Override
    public int unreadCount(Long uid) {
        Long c = messageMapper.selectCount(new LambdaQueryWrapper<MessageEntity>()
                .eq(MessageEntity::getToId, uid)
                .eq(MessageEntity::getIsRead, 0));
        return c == null ? 0 : c.intValue();
    }

    @Override
    public void markRead(Long uid) {
        MessageEntity upd = new MessageEntity();
        upd.setIsRead(1);
        messageMapper.update(upd, new LambdaQueryWrapper<MessageEntity>()
                .eq(MessageEntity::getToId, uid)
                .eq(MessageEntity::getIsRead, 0));
    }

    @Override
    public void deleteRead(Long uid) {
        messageMapper.delete(new LambdaQueryWrapper<MessageEntity>()
                .eq(MessageEntity::getToId, uid)
                .eq(MessageEntity::getIsRead, 1)
                .ne(MessageEntity::getType, 4));
    }

    @Override
    public void markOneRead(Long uid, Long msgId) {
        MessageEntity upd = new MessageEntity();
        upd.setIsRead(1);
        messageMapper.update(upd, new LambdaQueryWrapper<MessageEntity>()
                .eq(MessageEntity::getId, msgId)
                .eq(MessageEntity::getToId, uid));
    }
}
