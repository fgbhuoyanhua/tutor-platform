import { get, post, put, del } from '../utils/request'

export function pageOrders(params: Map<string, any>): Promise<UTSJSONObject> {
  return get('/orders', params)
}

export function createOrder(body: UTSJSONObject): Promise<UTSJSONObject> {
  return post('/orders', body)
}

export function confirmOrder(id: number): Promise<UTSJSONObject> {
  return put('/orders/' + id + '/confirm', new Map<string, any>())
}

export function rejectOrder(id: number): Promise<UTSJSONObject> {
  return put('/orders/' + id + '/reject', new Map<string, any>())
}

export function cancelOrder(id: number): Promise<UTSJSONObject> {
  return put('/orders/' + id + '/cancel', new Map<string, any>())
}

export function finishOrder(id: number): Promise<UTSJSONObject> {
  return put('/orders/' + id + '/finish', new Map<string, any>())
}

export function startOrder(id: number): Promise<UTSJSONObject> {
  return put('/orders/' + id + '/start', new Map<string, any>())
}

export function rescheduleRequest(orderId: number, newDate: string, newSlot: string): Promise<UTSJSONObject> {
  const body = new Map<string, any>()
  body.set('newDate', newDate)
  body.set('newSlot', newSlot)
  return post('/orders/' + orderId + '/reschedule', body)
}

export function rescheduleAccept(id: number): Promise<UTSJSONObject> {
  return put('/orders/' + id + '/reschedule/accept', new Map<string, any>())
}

export function rescheduleReject(id: number): Promise<UTSJSONObject> {
  return put('/orders/' + id + '/reschedule/reject', new Map<string, any>())
}
