import { api } from './api'

/** Catalog API types + calls (mirror the backend FrameworkResponse / ControlResponse DTOs). */
export interface Framework {
  id: number
  slug: string
  name: string
  version: string
}

export interface Control {
  id: number
  framework: string
  code: string
  title: string
  description: string | null
  category: string | null
}

export const getFrameworks = () => api.get<Framework[]>('/frameworks')

export const getControls = (slug: string) =>
  api.get<Control[]>(`/controls?framework=${encodeURIComponent(slug)}`)
