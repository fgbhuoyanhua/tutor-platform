import { get, post } from '../utils/request'

/** 老师课时账单（按月，订单快照） */
export function getTutorBill(month: string): Promise<UTSJSONObject> {
  const params = new Map<string, any>()
  params.set('month', month)
  return get('/tutor/bill', params)
}

/** 我提交的投诉 */
export function myComplaints(): Promise<UTSJSONObject> {
  return get('/complaints/my', null)
}

/** 学生提交投诉举报 */
export function submitComplaint(tutorId: number, type: number, content: string, orderId: number): Promise<UTSJSONObject> {
  const body = new Map<string, any>()
  body.set('tutorId', tutorId)
  body.set('type', type)
  body.set('content', content)
  if (orderId > 0) body.set('orderId', orderId)
  return post('/complaints', body)
}
