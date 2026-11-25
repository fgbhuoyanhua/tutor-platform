import { get, post, postForm, put } from './request';
import type { CertSubmitDTO, TutorCertVO } from '../models';

/** 上传证明材料，返回后端访问路径（/api/tutor-cert/file/{userId}/{name}） */
export async function uploadCertFile(file: File): Promise<string> {
  const form = new FormData();
  form.append('file', file);
  return postForm<string>('/tutor-cert/upload', form);
}

/** 老师提交资格认证申请 */
export function submitCert(data: CertSubmitDTO): Promise<number> {
  return post<number>('/tutor-cert', data);
}

/** 老师查看本人认证情况 */
export function getMyCert(): Promise<TutorCertVO> {
  return get<TutorCertVO>('/tutor-cert/my');
}

/** 管理员：待审核认证列表 */
export function pendingCerts(): Promise<TutorCertVO[]> {
  return get<TutorCertVO[]>('/admin/certs/pending');
}

/** 管理员：审核认证（pass=true 通过，false 驳回） */
export function auditCert(id: number, pass: boolean, reason?: string): Promise<void> {
  return put<void>(`/admin/certs/${id}/audit`, undefined, { pass, reason });
}

/** 认证状态文案 */
export function certStatusLabel(status?: number): string {
  switch (status) {
    case 1: return '待审核';
    case 2: return '已认证';
    case 3: return '已驳回';
    default: return '未认证';
  }
}

/** 证明类型文案 */
export function certTypeLabel(type?: number | null): string {
  switch (type) {
    case 1: return '学生证';
    case 2: return '学信网在线验证报告';
    case 3: return '校园卡';
    case 4: return '校园邮箱';
    case 5: return '其他';
    default: return '未选择';
  }
}
