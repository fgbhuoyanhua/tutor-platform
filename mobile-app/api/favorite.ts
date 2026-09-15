import { get, post, del } from '../utils/request'

export function listFavorites(): Promise<UTSJSONObject> {
  return get('/favorites', null)
}

export function addFavorite(tutorId: number): Promise<UTSJSONObject> {
  return post('/favorites/' + tutorId, new Map<string, any>())
}

export function removeFavorite(tutorId: number): Promise<UTSJSONObject> {
  return del('/favorites/' + tutorId)
}
