import { get, post } from './request';
import type { PageResult, EvaluationVO } from '../models';

/** 老师历史评价列表（公开） */
export function listTutorEvaluations(tutorUserId: number, page = 1, size = 10): Promise<PageResult<EvaluationVO>> {
  return get<PageResult<EvaluationVO>>(`/evaluations/tutor/${tutorUserId}`, { page, size });
}

/** 学生提交评价（仅已完成订单） */
export function createEvaluation(orderId: number, score: number, content?: string): Promise<void> {
  return post<void>('/evaluations', { orderId, score, content });
}
