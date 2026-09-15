import { get, post, put } from './request';
import type { PageResult, OrderVO, AppointmentCreateDTO } from '../models';

export interface OrderQuery {
  page?: number;
  size?: number;
}

/** 我的订单列表（学生看发出的，老师看收到的） */
export function pageOrders(params: OrderQuery = {}): Promise<PageResult<OrderVO>> {
  return get<PageResult<OrderVO>>('/appointments', params as Record<string, unknown>);
}

/** 学生提交预约 */
export function createOrder(data: AppointmentCreateDTO): Promise<number> {
  return post<number>('/appointments', data);
}

/** 老师接单确认 */
export function confirmOrder(id: number): Promise<void> {
  return put<void>(`/appointments/${id}/confirm`);
}

/** 老师拒绝预约 */
export function rejectOrder(id: number): Promise<void> {
  return put<void>(`/appointments/${id}/reject`);
}

/** 学生取消预约（仅待确认） */
export function cancelOrder(id: number): Promise<void> {
  return put<void>(`/appointments/${id}/cancel`);
}

/** 学生确认完成授课 */
export function finishOrder(id: number): Promise<void> {
  return put<void>(`/appointments/${id}/finish`);
}

/** 学生申请调课 */
export function rescheduleRequest(id: number, newDate: string, newSlot: string): Promise<void> {
  return post<void>(`/appointments/${id}/reschedule`, { newDate, newSlot });
}

/** 老师同意调课 */
export function rescheduleAccept(id: number): Promise<void> {
  return put<void>(`/appointments/${id}/reschedule/accept`);
}

/** 老师拒绝调课 */
export function rescheduleReject(id: number): Promise<void> {
  return put<void>(`/appointments/${id}/reschedule/reject`);
}
