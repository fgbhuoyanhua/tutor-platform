import { get, post, put, del } from '../utils/request'

export function listMessages(): Promise<UTSJSONObject> {
  return get('/messages', null)
}

export function listConversations(): Promise<UTSJSONObject> {
  return get('/messages/conversations', null)
}

export function getConversation(otherId: number): Promise<UTSJSONObject> {
  return get('/messages/conversation/' + otherId, null)
}

export function sendMessage(otherId: number, content: string): Promise<UTSJSONObject> {
  const body = new Map<string, any>()
  body.set('toId', otherId)
  body.set('content', content)
  return post('/messages', body)
}

export function markRead(): Promise<UTSJSONObject> {
  return put('/messages/read', new Map<string, any>())
}

export function markOneRead(id: number): Promise<UTSJSONObject> {
  return put('/messages/' + id + '/read', new Map<string, any>())
}

export function deleteRead(): Promise<UTSJSONObject> {
  return del('/messages/read')
}
