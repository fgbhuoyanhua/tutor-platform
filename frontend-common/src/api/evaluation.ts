import { get } from './request';
import type { PageResult, EvaluationVO } from '../models';

/** 老师历史评价列表（公开） */
export function listTutorEvaluations(tutorUserId: number, page = 1, size = 10): Promise<PageResult<EvaluationVO>> {
  return get<PageResult<EvaluationVO>>(`/evaluations/tutor/${tutorUserId}`, { page, size });
}
