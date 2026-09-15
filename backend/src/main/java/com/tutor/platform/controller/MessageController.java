package com.tutor.platform.controller;

import com.tutor.platform.common.ApiResponse;
import com.tutor.platform.common.UserContext;
import com.tutor.platform.dto.SendMessageDTO;
import com.tutor.platform.entity.MessageEntity;
import com.tutor.platform.service.MessageService;
import com.tutor.platform.vo.ConversationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 消息接口：站内消息列表、未读数、全部已读、私信收发
 */
@Tag(name = "消息")
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @Operation(summary = "学生/老师互发私信")
    @PostMapping
    public ApiResponse<Void> send(@Valid @RequestBody SendMessageDTO dto) {
        if (dto.getToId().equals(UserContext.getUid())) {
            return ApiResponse.fail("不能给自己发消息");
        }
        messageService.send(UserContext.getUid(), dto.getToId(), 4, dto.getContent().trim());
        return ApiResponse.ok();
    }

    @Operation(summary = "我的私信会话列表")
    @GetMapping("/conversations")
    public ApiResponse<List<ConversationVO>> conversations() {
        return ApiResponse.ok(messageService.conversations(UserContext.getUid()));
    }

    @Operation(summary = "与某人的对话记录（进入时自动已读对方私信）")
    @GetMapping("/conversation/{otherId}")
    public ApiResponse<List<MessageEntity>> conversation(@PathVariable Long otherId) {
        return ApiResponse.ok(messageService.conversation(UserContext.getUid(), otherId));
    }

    @Operation(summary = "我的消息列表")
    @GetMapping
    public ApiResponse<List<MessageEntity>> list() {
        return ApiResponse.ok(messageService.listByUser(UserContext.getUid()));
    }

    @Operation(summary = "未读消息数")
    @GetMapping("/unread")
    public ApiResponse<Integer> unreadCount() {
        return ApiResponse.ok(messageService.unreadCount(UserContext.getUid()));
    }

    @Operation(summary = "全部标记已读")
    @PutMapping("/read")
    public ApiResponse<Void> markRead() {
        messageService.markRead(UserContext.getUid());
        return ApiResponse.ok();
    }

    @Operation(summary = "删除所有已读消息")
    @DeleteMapping("/read")
    public ApiResponse<Void> deleteRead() {
        messageService.deleteRead(UserContext.getUid());
        return ApiResponse.ok();
    }

    @Operation(summary = "单条消息标记已读")
    @PutMapping("/{id}/read")
    public ApiResponse<Void> markOneRead(@PathVariable Long id) {
        messageService.markOneRead(UserContext.getUid(), id);
        return ApiResponse.ok();
    }
}
