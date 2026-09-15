import axios from 'axios';
import type { ApiResponse } from '../models';
import { getToken, clearToken } from '../utils/token';

/**
 * 统一请求封装：
 * - baseURL 为 /api，Web 端由 Vite 代理转发到后端 8080
 * - 请求自动携带 Bearer Token
 * - 响应统一校验 code，401 自动清理登录态并跳转登录页
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
});

request.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

request.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown>;
    if (body.code === 401) {
      clearToken();
      if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
        const redirect = encodeURIComponent(window.location.pathname + window.location.search);
        window.location.href = `/login?redirect=${redirect}`;
      }
      return Promise.reject(new Error(body.message || '未登录或登录已过期'));
    }
    if (body.code === 403) {
      return Promise.reject(new Error(body.message || '无权限访问'));
    }
    if (body.code !== 200) {
      const err = new Error(body.message || '请求失败') as Error & { code?: number };
      err.code = body.code;
      return Promise.reject(err);
    }
    return response;
  },
  (error) => {
    const body = error?.response?.data as ApiResponse<unknown> | undefined;
    let message: string;
    if (error?.code === 'ECONNABORTED') {
      message = '请求超时，请稍后再试';
    } else if (!error?.response) {
      message = '网络异常，请检查网络连接';
    } else {
      message = body?.message || error?.message || '请求失败，请稍后再试';
    }
    const err = new Error(message) as Error & { code?: number };
    err.code = body?.code ?? error?.response?.status;
    return Promise.reject(err);
  }
);

export async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const res = await request.get<ApiResponse<T>>(url, { params });
  return res.data.data;
}

export async function post<T>(url: string, data?: unknown): Promise<T> {
  const res = await request.post<ApiResponse<T>>(url, data);
  return res.data.data;
}

export async function put<T>(
  url: string,
  data?: unknown,
  params?: Record<string, unknown>
): Promise<T> {
  const res = await request.put<ApiResponse<T>>(url, data, { params });
  return res.data.data;
}

export async function del<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const res = await request.delete<ApiResponse<T>>(url, { params });
  return res.data.data;
}
