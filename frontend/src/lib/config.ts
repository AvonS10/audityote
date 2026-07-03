import { api } from './api'

/** Client-visible server capabilities (PLAN §7.12), from GET /api/config. */
export interface ServerConfig {
  /** Whether the backend can serve AI control suggestions — gates the "Suggest controls" UI. */
  aiSuggestionsEnabled: boolean
}

let cached: Promise<ServerConfig> | null = null

/**
 * Fetches server capabilities, memoised for the session (the config is server-wide, not per-user). On
 * failure the cache is cleared so a later call can retry.
 */
export function getConfig(): Promise<ServerConfig> {
  if (!cached) {
    cached = api.get<ServerConfig>('/config').catch((err) => {
      cached = null
      throw err
    })
  }
  return cached
}
