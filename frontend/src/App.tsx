import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { Login } from './pages/Login'
import { Home } from './pages/Home'

/**
 * Top-level routes. /login is public; everything else is gated by ProtectedRoute (which redirects
 * anonymous users to /login). The real app screens hang off "/" once the AppShell lands in 8c.
 */
function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route
        path="/"
        element={
          <ProtectedRoute>
            <Home />
          </ProtectedRoute>
        }
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
