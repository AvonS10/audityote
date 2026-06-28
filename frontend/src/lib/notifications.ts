import { api } from './api'

/** A finding handed back to the signed-in user by a reviewer (#4). */
export interface Notification {
  findingId: number
  reference: string
  title: string
  severity: string
  returnedBy: string
  returnedAt: string
  comment: string | null
}

/** The caller's returned-to-them findings, with the reviewer's comment. */
export const getNotifications = () => api.get<Notification[]>('/notifications')
