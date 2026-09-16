import { get, post, put } from './request';
import type { IncomeVO, MyStudentVO, PageResult, TutorVO, TutorDTO } from '../models';

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

/** 老师的我的学生列表 */
export function getMyStudents(): Promise<MyStudentVO[]> {
  return get<MyStudentVO[]>('/tutors/my-students');
}

/** 智能推荐老师 */
export function recommendTutors(subjectId?: number, priceMax?: number, limit = 3): Promise<TutorVO[]> {
  return get<TutorVO[]>('/tutors/recommend', { subjectId, priceMax, limit });
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
