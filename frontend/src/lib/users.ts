import { api } from './api'

/** A user in the admin Users list (#admin). */
export interface UserSummary {
  id: number
  name: string
  email: string
  /** 'ANALYST' | 'REVIEWER' | 'ADMIN' */
  role: string
  active: boolean
}

export const getUsers = () => api.get<UserSummary[]>('/users')

export const changeUserRole = (id: number, role: string) =>
  api.put<UserSummary>(`/users/${id}/role`, { role })

export const setUserActive = (id: number, active: boolean) =>
  api.put<UserSummary>(`/users/${id}/active`, { active })

export const resetUserPassword = (id: number, newPassword: string) =>
  api.put<void>(`/users/${id}/password`, { newPassword })
