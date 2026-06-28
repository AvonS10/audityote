import type { User } from '../auth/AuthContext'
import { api } from './api'

/** Update the signed-in user's display name (#5). Returns the refreshed user. */
export const updateProfile = (name: string) => api.put<User>('/account/profile', { name })

/** Change the signed-in user's password; the server re-verifies the current one (#5). */
export const changePassword = (currentPassword: string, newPassword: string) =>
  api.put<void>('/account/password', { currentPassword, newPassword })
