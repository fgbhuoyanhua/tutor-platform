import { put } from '../utils/request'

export function updateProfile(realName: string): Promise<UTSJSONObject> {
  const body = new Map<string, any>()
  body.set('realName', realName)
  return put('/user/profile', body)
}

export function updatePassword(oldPwd: string, newPwd: string): Promise<UTSJSONObject> {
  const body = new Map<string, any>()
  body.set('oldPassword', oldPwd)
  body.set('newPassword', newPwd)
  return put('/user/password', body)
}
