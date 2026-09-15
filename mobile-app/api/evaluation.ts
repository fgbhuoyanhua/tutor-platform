import { get, post } from '../utils/request'

export function submitEvaluation(body: UTSJSONObject): Promise<UTSJSONObject> {
  return post('/evaluations', body)
}

export function tutorEvaluations(tutorId: number, page: number, size: number): Promise<UTSJSONObject> {
  const params = new Map<string, any>()
  params.set('page', page)
  params.set('size', size)
  return get('/evaluations/tutor/' + tutorId, params)
}
