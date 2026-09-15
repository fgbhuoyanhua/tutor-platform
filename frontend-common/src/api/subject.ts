import { get } from './request';
import type { SubjectEntity } from '../models';

/** 科目分类列表（公开） */
export function listSubjects(): Promise<SubjectEntity[]> {
  return get<SubjectEntity[]>('/subjects');
}
