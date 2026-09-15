import { post } from '../utils/request'

export function login(username: string, password: string): Promise<UTSJSONObject> {
  const body = new Map<string, any>()
  body.set('username', username)
  body.set('password', password)
  return post('/auth/login', body)
}

export function register(
  username: string,
  password: string,
  role: number,
  realName: string
): Promise<UTSJSONObject> {
  const body = new Map<string, any>()
  body.set('username', username)
  body.set('password', password)
  body.set('role', role)
  body.set('realName', realName)
  return post('/auth/register', body)
}
