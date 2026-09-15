/**
 * Token 存取：默认使用 localStorage（Web 端）。
 * 移动端（uni-app x）在各自工程内用 uni.setStorageSync 实现同构方法。
 */

const TOKEN_KEY = 'tutor_platform_token';
const USER_KEY = 'tutor_platform_user';

export function getToken(): string | null {
  if (typeof localStorage === 'undefined') return null;
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  if (typeof localStorage === 'undefined') return;
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
  if (typeof localStorage === 'undefined') return;
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

export function getUserJSON(): string | null {
  if (typeof localStorage === 'undefined') return null;
  return localStorage.getItem(USER_KEY);
}

export function setUserJSON(json: string): void {
  if (typeof localStorage === 'undefined') return;
  localStorage.setItem(USER_KEY, json);
}
