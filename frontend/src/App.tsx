import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { AppShell } from './components/app/AppShell'
import { ControlCatalog } from './pages/ControlCatalog'
import { Login } from './pages/Login'
import { Placeholder } from './pages/Placeholder'

/**
 * Top-level routes. /login is public; everything else is gated by ProtectedRoute and rendered
 * inside the AppShell layout. The per-screen pages are placeholders until their build increments
 * (#9 catalog, #10 dashboard, #13 coverage, #17 reviews, #19 posture).
 */
function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route
        element={
          <ProtectedRoute>
            <AppShell />
          </ProtectedRoute>
        }
      >
        <Route path="/" element={<Placeholder title="Dashboard" note="The findings dashboard arrives in build increment #10." />} />
        <Route path="/catalog" element={<ControlCatalog />} />
        <Route path="/coverage" element={<Placeholder title="Control Coverage" note="Coverage and gaps arrive in build increment #13." />} />
        <Route path="/posture" element={<Placeholder title="Risk Posture" note="The risk posture dashboard arrives in build increment #19." />} />
        <Route path="/reviews" element={<Placeholder title="Review Queue" note="The reviewer sign-off queue arrives in build increment #17." />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
