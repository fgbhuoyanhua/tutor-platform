import { del, get, post } from './request';
import type { TutorVO } from '../models';

/** 收藏老师 */
export function addFavorite(tutorId: number): Promise<void> {
  return post<void>(`/favorites/${tutorId}`);
}

/** 取消收藏 */
export function removeFavorite(tutorId: number): Promise<void> {
  return del<void>(`/favorites/${tutorId}`);
}

/** 我的收藏列表 */
export function listFavorites(): Promise<TutorVO[]> {
  return get<TutorVO[]>('/favorites');
}
