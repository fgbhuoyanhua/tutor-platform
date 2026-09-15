import { get, post, put } from '../utils/request'

export function listSubjects(): Promise<UTSJSONObject> {
  return get('/subjects', null)
}
