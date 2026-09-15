import { ORDER_STATUS_LABELS, TUTOR_STATUS_LABELS, MESSAGE_TYPE_LABELS } from '../constants';

/** 订单状态文案 */
export function orderStatusLabel(status: number): string {
  return ORDER_STATUS_LABELS[status] ?? '未知';
}

/** 家教状态文案 */
export function tutorStatusLabel(status: number): string {
  return TUTOR_STATUS_LABELS[status] ?? '未知';
}

/** 消息类型文案 */
export function messageTypeLabel(type: number): string {
  return MESSAGE_TYPE_LABELS[type] ?? '系统';
}

/** 价格展示：¥120.00 */
export function formatPrice(price: number): string {
  return `¥${Number(price).toFixed(2)}`;
}

/** 评分展示：4.8 分 */
export function formatRating(rating: number): string {
  return `${Number(rating).toFixed(1)} 分`;
}

/** 日期时间展示：2026-09-15 14:00 */
export function formatDateTime(dt: string): string {
  if (!dt) return '';
  return dt.replace('T', ' ').substring(0, 16);
}

/** 预约日期展示：2026-09-15（周X） */
export function formatDateCN(dateStr: string): string {
  if (!dateStr) return '';
  const d = new Date(`${dateStr}T00:00:00`);
  const weeks = ['日', '一', '二', '三', '四', '五', '六'];
  return `${dateStr} 周${weeks[d.getDay()]}`;
}

/** 校验手机号 */
export function isValidPhone(phone: string): boolean {
  return /^1[3-9]\d{9}$/.test(phone);
}
