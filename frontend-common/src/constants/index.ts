/**
 * 全站常量：角色、订单状态、消息类型
 */

/** 角色 */
export const ROLE_STUDENT = 1;
export const ROLE_TUTOR = 2;
export const ROLE_ADMIN = 3;

export const ROLE_LABELS: Record<number, string> = {
  [ROLE_STUDENT]: '学生',
  [ROLE_TUTOR]: '老师',
  [ROLE_ADMIN]: '管理员',
};

/** 订单状态：0待确认 1已预约 2授课中 3已完成 4已取消 5已拒绝 */
export const ORDER_STATUS_LABELS: Record<number, string> = {
  0: '待确认',
  1: '已预约',
  2: '授课中',
  3: '已完成',
  4: '已取消',
  5: '已拒绝',
};

export const ORDER_STATUS_TAG_TYPES: Record<number, string> = {
  0: 'warning',
  1: 'primary',
  2: 'info',
  3: 'success',
  4: 'info',
  5: 'danger',
};

/** 家教信息状态：0待审核 1已上架 2已下架 3未通过 */
export const TUTOR_STATUS_LABELS: Record<number, string> = {
  0: '待审核',
  1: '已上架',
  2: '已下架',
  3: '未通过',
};

/** 消息类型：1预约 2订单 3系统 4私信 */
export const MESSAGE_TYPE_LABELS: Record<number, string> = {
  1: '预约',
  2: '订单',
  3: '系统',
  4: '私信',
};

/** 常用预约时间段 */
export const TIME_SLOTS: string[] = [
  '08:00-10:00',
  '09:00-11:00',
  '10:00-12:00',
  '14:00-16:00',
  '15:00-17:00',
  '16:00-18:00',
  '19:00-21:00',
];
