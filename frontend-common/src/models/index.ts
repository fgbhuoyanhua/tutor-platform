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
  /** 就读院校（认证通过后展示） */
  school?: string | null;
  /** 专业 */
  major?: string | null;
  /** 年级 */
  gradeLevel?: string | null;
  /** 认证状态：0未提交 1待审 2已认证 3驳回 */
  certStatus?: number;
  /** 认证通过时间 */
  certTime?: string | null;
  /** 信用分 0-100 */
  creditScore?: number;
  /** 等级：A 金牌 / B 优质 / C 普通 / D 受限 */
  creditLevel?: string;
  /** 等级中文名 */
  creditLevelLabel?: string;
  /** 违规累计次数 */
  violationCount?: number;
  /** 推荐分 0~1（仅推荐排序接口返回） */
  recommendScore?: number;
  /** 推荐理由文案（仅推荐排序接口返回） */
  recommendReason?: string;
}

/** 老师资格认证记录（老师端"我的认证"与管理员待审列表共用） */
export interface TutorCertVO {
  id: number;
  tutorId: number;
  tutorName: string | null;
  phone?: string | null;
  school: string | null;
  major: string | null;
  gradeLevel: string | null;
  certType: number | null;
  certTypeLabel?: string | null;
  certNo: string | null;
  certUrl: string | null;
  note: string | null;
  /** 本条记录状态：0待审 1通过 2驳回 */
  status: number;
  reviewNote: string | null;
  reviewTime: string | null;
  createTime: string;
  /** 该老师整体认证状态：0未提交 1待审 2已认证 3驳回 */
  certStatus?: number;
}

/** 提交资格认证的请求体 */
export interface CertSubmitDTO {
  school: string;
  major: string;
  gradeLevel: string;
  /** 证明类型：1学生证 2学信网报告 3校园卡 4校园邮箱 5其他 */
  certType: number;
  certNo?: string;
  certUrl?: string;
  note?: string;
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

/** 老师端数据看板 */
export interface TutorDashboardVO {
  totalIncome: number;
  totalOrders: number;
  completedOrders: number;
  pendingOrders: number;
  incomeTrend: IncomeVO[];
  subjectIncome: { subjectId: number; subjectName: string; income: number; orderCount: number }[];
  statusDistribution: { status: number; statusName: string; count: number }[];
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

/** 课时账单逐笔明细（订单快照） */
export interface BillItemVO {
  orderId: number;
  orderNo: string;
  studentName: string;
  subjectName: string;
  appointDate: string;
  timeSlot: string;
  durationMinutes: number;
  unitPrice: number;
  amount: number;
  status: number;
  statusName: string;
}

/** 老师月度课时账单 */
export interface BillVO {
  month: string;
  finishedCount: number;
  finishedMinutes: number;
  totalAmount: number;
  pendingCount: number;
  items: BillItemVO[];
}

/** 投诉举报（学生端/管理端共用） */
export interface ComplaintVO {
  id: number;
  complainantId: number;
  complainantName: string;
  tutorId: number;
  tutorName: string;
  orderId: number | null;
  orderNo: string | null;
  type: number;
  typeLabel: string;
  content: string;
  evidenceUrl: string | null;
  /** 0待处理 1成立 2驳回 */
  status: number;
  statusLabel: string;
  handlerId: number | null;
  handleNote: string | null;
  handlerTime: string | null;
  deductScore: number;
  createTime: string;
}

/** 提交投诉请求体 */
export interface ComplaintCreateDTO {
  tutorId: number;
  orderId?: number | null;
  /** 1爽约 2私改价格 3骚扰辱骂 4虚假信息 5其他 */
  type: number;
  content: string;
  evidenceUrl?: string | null;
}

/** 推荐排序权重（后端配置项映射） */
export interface RecommendWeights {
  cert: number;
  rating: number;
  finish: number;
  credit: number;
}

/** 老师违规记录（管理端「违规记录」页） */
export interface TutorViolationVO {
  id: number;
  tutorId: number;
  tutorName: string;
  /** 1投诉成立 2超时未确认 3频繁拒绝 */
  type: number;
  typeLabel: string;
  /** 关联单据类型：complaint / appointment */
  refType: string | null;
  refId: number | null;
  /** 本次扣减信用分（正数） */
  deduct: number;
  reason: string | null;
  operatorId: number | null;
  operatorName: string;
  /** 1有效 0已撤销 */
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
