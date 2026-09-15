import { get } from '../utils/request'

export function pageTutors(params: Map<string, any>): Promise<UTSJSONObject> {
  return get('/tutors', params)
}

export function getTutor(id: number): Promise<UTSJSONObject> {
  return get('/tutors/' + id, null)
}

export function getTutorIncome(month: string): Promise<UTSJSONObject> {
  return get('/tutors/income?month=' + month, null)
}

export function getMyStudents(): Promise<UTSJSONObject> {
  return get('/tutors/my-students', new Map<string, any>())
}
