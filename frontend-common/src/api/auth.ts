import { get, post } from './request';
import type { LoginVO, RegisterDTO } from '../models';

/** 发送短信验证码（Redis 可用时有效） */
export function sendCode(phone: string): Promise<void> {
  return get<void>('/auth/code', { phone });
}

/** 注册（手机号+验证码） */
export function register(data: RegisterDTO): Promise<void> {
  return post<void>('/auth/register', data);
}

/** 登录，返回 JWT 与角色信息 */
export function login(username: string, password: string): Promise<LoginVO> {
  return post<LoginVO>('/auth/login', { username, password });
}
