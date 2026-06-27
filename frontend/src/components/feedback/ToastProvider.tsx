import { createContext, useCallback, useContext, useState, type ReactNode } from 'react'
import { ToastViewport, type ToastItem, type ToastTone } from './Toast'

interface ToastInput {
  tone?: ToastTone
  title: string
  message?: string
}

interface ToastApi {
  /** Show a toast. success/info auto-dismiss at 5s; errors stay until dismissed (PLAN §7.9). */
  toast: (t: ToastInput) => void
}

const ToastContext = createContext<ToastApi | null>(null)

let seq = 0

/** App-level toast host: provides the {@link useToast} hook and renders the fixed viewport. */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([])

  const dismiss = useCallback((id: number) => {
    setToasts((items) => items.filter((t) => t.id !== id))
  }, [])

  const toast = useCallback(({ tone = 'success', title, message }: ToastInput) => {
    const id = ++seq
    const duration = tone === 'error' ? undefined : 5000
    setToasts((items) => [...items, { id, tone, title, message, duration }])
  }, [])

  return (
    <ToastContext.Provider value={{ toast }}>
      {children}
      <ToastViewport toasts={toasts} onDismiss={dismiss} />
    </ToastContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useToast(): ToastApi {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast must be used within a ToastProvider')
  return ctx
}
