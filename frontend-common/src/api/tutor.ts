import { get, post, postForm, put } from './request';
import type {
  BillVO,
  ComplaintCreateDTO,
  ComplaintVO,
  IncomeVO,
  MyStudentVO,
  PageResult,
  RecommendWeights,
  TutorDashboardVO,
  TutorVO,
  TutorDTO,
} from '../models';

export interface TutorQuery {
  page?: number;
  size?: number;
  subjectId?: number;
  priceMin?: number;
  priceMax?: number;
  keyword?: string;
}

/** 分页+多条件查询家教（公开） */
export function pageTutors(params: TutorQuery = {}): Promise<PageResult<TutorVO>> {
  return get<PageResult<TutorVO>>('/tutors', params as Record<string, unknown>);
}

/** 家教详情（公开） */
export function getTutor(id: number): Promise<TutorVO> {
  return get<TutorVO>(`/tutors/${id}`);
}

/** 老师发布家教信息 */
export function publishTutor(data: TutorDTO): Promise<number> {
  return post<number>('/tutors', data);
}

/** 老师编辑家教信息 */
export function updateTutor(id: number, data: TutorDTO): Promise<void> {
  return put<void>(`/tutors/${id}`, data);
}

/** 老师近6个月收入趋势 */
export function getIncomeTrend(): Promise<IncomeVO[]> {
  return get<IncomeVO[]>('/tutors/income/trend');
}

/** 老师端数据看板 */
export function getTutorDashboard(): Promise<TutorDashboardVO> {
  return get<TutorDashboardVO>('/tutors/dashboard');
}

/** 老师的我的学生列表 */
export function getMyStudents(): Promise<MyStudentVO[]> {
  return get<MyStudentVO[]>('/tutors/my-students');
}

/** 智能推荐老师 */
export function recommendTutors(subjectId?: number, priceMax?: number, limit = 3): Promise<TutorVO[]> {
  return get<TutorVO[]>('/tutors/recommend', { subjectId, priceMax, limit });
}

/** 推荐排序版：认证/评分/完课/信用 四维加权，返回带推荐分与推荐理由 */
export function recommendRanked(subjectId?: number, limit = 5): Promise<TutorVO[]> {
  return get<TutorVO[]>('/tutors/recommend-ranked', { subjectId, limit });
}

/** 推荐排序权重（公开） */
export function getRecommendWeights(): Promise<RecommendWeights> {
  return get<RecommendWeights>('/tutors/recommend-weights');
}

/** 老师课时账单（按月，订单快照） */
export function getTutorBill(month: string): Promise<BillVO> {
  return get<BillVO>('/tutor/bill', { month });
}

/** 学生提交投诉举报 */
export function submitComplaint(data: ComplaintCreateDTO): Promise<number> {
  return post<number>('/complaints', data);
}

/** 我提交的投诉 */
export function myComplaints(): Promise<ComplaintVO[]> {
  return get<ComplaintVO[]>('/complaints/my');
}

/** 上传投诉证据（jpg/png/pdf，≤5MB），返回 /api/complaints/evidence/{userId}/{name} */
export function uploadComplaintEvidence(file: File): Promise<string> {
  const form = new FormData();
  form.append('file', file);
  return postForm<string>('/complaints/evidence', form);
}

/** 老师回复评价 */
export function replyEvaluation(id: number, reply: string): Promise<void> {
  return put<void>(`/evaluations/${id}/reply`, { reply });
}

/** 老师提交课后学习报告 */
export function submitReport(orderId: number, content: string): Promise<void> {
  return post<void>('/reports', { orderId, content });
}

/** 学生查看学习报告 */
export function getReport(orderId: number): Promise<{ id: number; content: string; createTime: string } | null> {
  return get(`/reports/${orderId}`);
}
