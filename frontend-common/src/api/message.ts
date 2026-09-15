import { get, post, put } from './request';
import type { MessageEntity, ConversationVO } from '../models';

/** 我的消息列表 */
export function listMessages(): Promise<MessageEntity[]> {
  return get<MessageEntity[]>('/messages');
}

/** 未读消息数 */
export function unreadCount(): Promise<number> {
  return get<number>('/messages/unread');
}

/** 全部标记已读 */
export function markAllRead(): Promise<void> {
  return put<void>('/messages/read');
}

/** 给某人发私信 */
export function sendMessage(toId: number, content: string): Promise<void> {
  return post<void>('/messages', { toId, content });
}

/** 与某人的对话记录（进入时自动已读） */
export function listConversation(otherId: number): Promise<MessageEntity[]> {
  return get<MessageEntity[]>(`/messages/conversation/${otherId}`);
}

/** 我的私信会话列表 */
export function listConversations(): Promise<ConversationVO[]> {
  return get<ConversationVO[]>('/messages/conversations');
}
