import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { AppShell } from './components/app/AppShell'
import { ControlCatalog } from './pages/ControlCatalog'
import { ControlCoverage } from './pages/ControlCoverage'
import { Dashboard } from './pages/Dashboard'
import { FindingDetailScreen } from './pages/FindingDetailScreen'
import { FindingForm } from './pages/FindingForm'
import { Login } from './pages/Login'
import { Register } from './pages/Register'
import { AccountSettings } from './pages/AccountSettings'
import { ReviewQueue } from './pages/ReviewQueue'
import { RiskPosture } from './pages/RiskPosture'

/**
 * Top-level routes. /login is public; everything else is gated by ProtectedRoute and rendered
 * inside the AppShell layout. The per-screen pages are placeholders until their build increments
 * (#9 catalog, #10 dashboard, #13 coverage, #17 reviews, #19 posture).
 */
function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route
        element={
          <ProtectedRoute>
            <AppShell />
          </ProtectedRoute>
        }
      >
        <Route path="/" element={<Dashboard />} />
        <Route path="/findings/new" element={<FindingForm />} />
        <Route path="/findings/:id/edit" element={<FindingForm />} />
        <Route path="/findings/:id" element={<FindingDetailScreen />} />
        <Route path="/catalog" element={<ControlCatalog />} />
        <Route path="/coverage" element={<ControlCoverage />} />
        <Route path="/posture" element={<RiskPosture />} />
        <Route path="/reviews" element={<ReviewQueue />} />
        <Route path="/account" element={<AccountSettings />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
