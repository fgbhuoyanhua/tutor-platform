import { get, post, put } from '../utils/request'

export function pageTutors(params: Map<string, any>): Promise<UTSJSONObject> {
  return get('/tutors', params)
}

export function getTutor(id: number): Promise<UTSJSONObject> {
  return get('/tutors/' + id, null)
}

export function getTutorIncome(month: string): Promise<UTSJSONObject> {
  return get('/tutors/income?month=' + month, null)
}

export function getTutorDashboard(): Promise<UTSJSONObject> {
  return get('/tutors/dashboard', null)
}

export function getMyStudents(): Promise<UTSJSONObject> {
  return get('/tutors/my-students', new Map<string, any>())
}

export function recommendTutors(subjectId: number, limit: number): Promise<UTSJSONObject> {
  const params = new Map<string, any>()
  if (subjectId != 0) params.set('subjectId', subjectId)
  params.set('limit', limit)
  return get('/tutors/recommend', params)
}

export function submitReport(orderId: number, content: string): Promise<UTSJSONObject> {
  const body = new Map<string, any>()
  body.set('orderId', orderId)
  body.set('content', content)
  return post('/reports', body)
}

export function getReport(orderId: number): Promise<UTSJSONObject> {
  return get('/reports/' + orderId, null)
}

export function replyEvaluation(id: number, reply: string): Promise<UTSJSONObject> {
  const body = new Map<string, any>()
  body.set('reply', reply)
  return put('/evaluations/' + id + '/reply', body)
}
