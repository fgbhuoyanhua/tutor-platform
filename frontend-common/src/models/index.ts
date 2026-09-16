/**
 * 全站统一接口类型：与后端 common 包一一对应
 */

/** 后端统一响应体 */
export interface ApiResponse<T = unknown> {
  code: number;
  message: string;
  data: T;
}

/** 分页结果 */
export interface PageResult<T> {
  total: number;
  page: number;
  size: number;
  records: T[];
}

/** 登录返回 */
export interface LoginVO {
  token: string;
  userId: number;
  username: string;
  realName: string;
  role: number;
  avatar: string | null;
}

/** 注册参数 */
export interface RegisterDTO {
  username: string;
  password: string;
  phone: string;
  code: string;
  realName: string;
  role: number;
}

/** 家教信息（展示用） */
export interface TutorVO {
  id: number;
  userId: number;
  tutorName: string;
  avatar: string | null;
  subjectId: number;
  subjectName: string;
  grade: string | null;
  price: number;
  rating: number;
  introduce: string | null;
  status: number;
  createTime: string;
  /** 历史评价数 */
  evaluateCount?: number;
  /** 已完成订单数（授课经验） */
  finishedOrderCount?: number;
}

/** 学生对老师的评价（详情抽屉展示用） */
export interface EvaluationVO {
  id: number;
  orderId: number;
  studentId: number;
  studentName: string;
  score: number;
  content: string;
  reply?: string;
  createTime: string;
}

/** 家教信息（发布/编辑用，与后端 TutorEntity 对应） */
export interface TutorDTO {
  id?: number;
  userId?: number;
  subjectId: number;
  grade: string;
  price: number;
  introduce: string;
  rating?: number;
  status?: number;
}

/** 家教信息（后端实体映射，管理员审核用） */
export interface TutorEntity {
  id: number;
  userId: number;
  subjectId: number;
  grade: string | null;
  price: number;
  introduce: string | null;
  rating: number;
  status: number;
  createTime: string;
}

/** 订单（列表/详情） */
export interface OrderVO {
  id: number;
  orderNo: string;
  studentId: number;
  studentName: string;
  tutorId: number;
  tutorName: string;
  subjectId: number;
  subjectName: string;
  appointDate: string;
  timeSlot: string;
  totalPrice: number;
  status: number;
  createTime: string;
}

/** 下单参数 */
export interface AppointmentCreateDTO {
  tutorId: number;
  subjectId: number;
  appointDate: string;
  timeSlot: string;
}

/** 科目分类 */
export interface SubjectEntity {
  id: number;
  name: string;
  parentId: number;
  sort: number;
}

/** 站内消息 */
export interface MessageEntity {
  id: number;
  fromId: number | null;
  toId: number;
  type: number;
  content: string;
  isRead: number;
  createTime: string;
}

/** 私信会话（与某用户最近一条消息） */
export interface ConversationVO {
  otherId: number;
  otherName: string;
  lastContent: string | null;
  lastTime: string | null;
  unread: number;
}

/** 平台统计（管理员） */
export interface StatsVO {
  userCount: number;
  tutorCount: number;
  orderCount: number;
  finishedOrderCount: number;
}

/** 老师月度收入 */
export interface IncomeVO {
  month: string;
  orderCount: number;
  totalIncome: number;
}

/** 老师视角"我的学生" */
export interface MyStudentVO {
  studentId: number;
  studentName: string;
  orderCount: number;
  finishedCount: number;
  lastDate: string | null;
}

/** 用户信息（管理员用户管理用） */
export interface UserEntity {
  id: number;
  username: string;
  realName: string;
  phone: string;
  role: number;
  status: number;
  createTime: string;
}

/** 管理员操作日志 */
export interface AdminLogEntity {
  id: number;
  adminId: number;
  adminName: string | null;
  action: string;
  targetType: string | null;
  targetId: number | null;
  detail: string | null;
  createTime: string;
}
