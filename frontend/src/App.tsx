import React, { Suspense, lazy } from 'react';
import { Routes, Route, Navigate, Link } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { ShieldAlert, LogOut } from 'lucide-react';
import { Button, buttonVariants } from '@/components/ui/button';
import { ThemeProvider } from '@/context/ThemeContext';

const AppShell = lazy(() => import('@/components/layout/AppShell').then(m => ({ default: m.AppShell })));
const LandingPage = lazy(() => import('@/pages/LandingPage').then(m => ({ default: m.LandingPage })));
const LoginPage = lazy(() => import('@/pages/LoginPage').then(m => ({ default: m.LoginPage })));
const DashboardPage = lazy(() => import('@/pages/DashboardPage').then(m => ({ default: m.DashboardPage })));
const MapPage = lazy(() => import('@/pages/MapPage').then(m => ({ default: m.MapPage })));
const ChatPage = lazy(() => import('@/pages/ChatPage').then(m => ({ default: m.ChatPage })));
const IncidentsPage = lazy(() => import('@/pages/IncidentsPage').then(m => ({ default: m.IncidentsPage })));
const AgenciesPage = lazy(() => import('@/pages/AgenciesPage').then(m => ({ default: m.AgenciesPage })));
const RespondersPage = lazy(() => import('@/pages/RespondersPage').then(m => ({ default: m.RespondersPage })));
const MessageCatalogPage = lazy(() => import('@/pages/MessageCatalogPage').then(m => ({ default: m.MessageCatalogPage })));
const EmergencyTypesPage = lazy(() => import('@/pages/EmergencyTypesPage').then(m => ({ default: m.EmergencyTypesPage })));
const TelemetryReportsPage = lazy(() => import('@/pages/TelemetryReportsPage').then(m => ({ default: m.TelemetryReportsPage })));
const AuditLogsPage = lazy(() => import('@/pages/AuditLogsPage').then(m => ({ default: m.AuditLogsPage })));
const UsersPage = lazy(() => import('@/pages/UsersPage').then(m => ({ default: m.UsersPage })));

function PageLoader() {
  return (
    <div role="status" className="flex h-[50vh] w-full flex-col items-center justify-center gap-3 text-muted-foreground">
      <div className="h-7 w-7 rounded-full border-2 border-primary border-t-transparent motion-safe:animate-spin" aria-hidden="true" />
      <span className="text-sm font-medium">Loading AGAP…</span>
    </div>
  );
}

function NotFoundPage({ workspace = false }: { workspace?: boolean }) {
  return (
    <div className={`flex items-center justify-center p-5 ${workspace ? 'min-h-[55dvh]' : 'min-h-dvh bg-background'}`}>
      <section className="surface-panel w-full max-w-md p-7 text-center sm:p-9">
        <p className="page-eyebrow">404 / Page not found</p>
        <h1 className="mt-3 text-3xl font-bold">This page is unavailable</h1>
        <p className="mt-3 text-sm leading-relaxed text-muted-foreground">The link may have changed or the page may have been removed.</p>
        <Link to={workspace ? '/dashboard' : '/'} className={buttonVariants({ className: 'mt-6' })}>{workspace ? 'Back to overview' : 'Back to AGAP'}</Link>
      </section>
    </div>
  );
}

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, user, loading, logout } = useAuth();

  if (loading) {
    return (
      <div role="status" className="flex min-h-dvh items-center justify-center bg-background text-sm text-muted-foreground">
        Loading AGAP Command Console...
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (user && user.role !== 'ADMIN') {
    return (
      <div className="flex min-h-dvh flex-col items-center justify-center bg-background p-6 text-center font-sans text-foreground">
        <div className="surface-panel w-full max-w-lg p-6 sm:p-8">
          <div className="tone-warning mx-auto mb-5 grid size-14 place-items-center rounded-xl">
            <ShieldAlert className="size-7" aria-hidden="true" />
          </div>
          <h1 className="text-2xl font-bold tracking-tight">Access denied — admin authorization required</h1>
          <p className="mt-3 text-sm leading-relaxed text-muted-foreground">
            The Command Center is restricted to authorized administrators. Your account (<strong>@{user.username}</strong>) is assigned role <strong>{user.role}</strong>.
          </p>
          <p className="mt-3 text-sm text-muted-foreground">Citizen and responder accounts use the AGAP Android app.</p>
          <Button onClick={() => logout()} className="mt-6 h-control gap-2">
            <LogOut className="h-4 w-4" /> Sign out and return to login
          </Button>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}

export function App() {
  return (
    <Suspense fallback={<PageLoader />}>
      <Routes>
        {/* Public Landing Page */}
        <Route path="/" element={<LandingPage />} />

        {/* Admin Login Page */}
        <Route path="/login" element={<LoginPage />} />

        {/* Command Center Dashboard App (Protected) */}
        <Route
          path="/dashboard"
          element={
            <ThemeProvider>
              <ProtectedRoute>
                <AppShell />
              </ProtectedRoute>
            </ThemeProvider>
          }
        >
          <Route index element={<DashboardPage />} />
          <Route path="map" element={<MapPage />} />
          <Route path="chat" element={<ChatPage />} />
          <Route path="telemetry-reports" element={<TelemetryReportsPage />} />
          <Route path="insights" element={<Navigate to="/dashboard/telemetry-reports" replace />} />
          <Route path="team" element={<RespondersPage />} />
          <Route path="users" element={<UsersPage />} />
          <Route path="archive" element={<IncidentsPage key="archive" variant="archive" />} />
          <Route path="incidents" element={<IncidentsPage key="incidents" variant="all" />} />
          <Route path="agencies" element={<AgenciesPage />} />
          <Route path="emergency-types" element={<EmergencyTypesPage />} />
          <Route path="messages" element={<MessageCatalogPage />} />
          <Route path="reports" element={<Navigate to="/dashboard/telemetry-reports" replace />} />
          <Route path="audit-logs" element={<AuditLogsPage />} />
          <Route path="*" element={<NotFoundPage workspace />} />
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Suspense>
  );
}

export default App;

