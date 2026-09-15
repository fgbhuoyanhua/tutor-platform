import { get, put } from './request';
import type { AdminLogEntity, PageResult, StatsVO, TutorEntity, UserEntity } from '../models';

/** 平台数据统计 */
export function getStats(): Promise<StatsVO> {
  return get<StatsVO>('/admin/stats');
}

/** 管理员操作日志 */
export function pageAdminLogs(params: { page?: number; size?: number } = {}): Promise<
  PageResult<AdminLogEntity>
> {
  return get<PageResult<AdminLogEntity>>('/admin/logs', params as Record<string, unknown>);
}

/** 待审核家教信息列表 */
export function pendingTutors(): Promise<TutorEntity[]> {
  return get<TutorEntity[]>('/admin/tutors/pending');
}

/** 审核家教信息（通过/驳回） */
export function auditTutor(id: number, pass: boolean, reason?: string): Promise<void> {
  return put<void>(`/admin/tutors/${id}/audit`, undefined, {
    pass,
    reason,
  });
}

/** 用户管理：禁用/启用 */
export function changeUserStatus(id: number, status: number): Promise<void> {
  return put<void>(`/admin/users/${id}/status`, undefined, { status });
}

/** 用户列表（管理员） */
export function pageUsers(params: { page?: number; size?: number } = {}): Promise<
  PageResult<UserEntity>
> {
  return get<PageResult<UserEntity>>('/admin/users', params as Record<string, unknown>);
}
