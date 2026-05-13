import { lazy, Suspense } from 'react';
import { Routes, Route } from 'react-router-dom';
import { Layout } from './components/Layout';
import { LandingPage } from './pages/LandingPage';
import { DashboardPage } from './pages/DashboardPage';
import { ReportsPage } from './pages/ReportsPage';
import { ReportDetailPage } from './pages/ReportDetailPage';
import { StatisticsPage } from './pages/StatisticsPage';
import { AboutPage } from './pages/AboutPage';
import { EdukasiPage } from './pages/EdukasiPage';
import { AdminLoginPage } from './pages/AdminLoginPage';
import { AdminDashboardPage } from './pages/AdminDashboardPage';
import { ProtectedRoute } from './components/ProtectedRoute';
import { ScrollToTop } from './components/ScrollToTop';
import { LoadingSpinner } from './components/LoadingSpinner';

const ReportFormPage = lazy(() => import('./pages/ReportFormPage').then(m => ({ default: m.ReportFormPage })));

export function App() {
  return (
    <>
      <ScrollToTop />
      <Routes>
        {/* Public routes */}
        <Route element={<Layout />}>
          <Route path="/" element={<LandingPage />} />
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/reports" element={<ReportsPage />} />
          <Route path="/reports/new" element={<Suspense fallback={<LoadingSpinner text="Memuat form laporan..." />}><ReportFormPage /></Suspense>} />
          <Route path="/reports/:id" element={<ReportDetailPage />} />
          <Route path="/statistics" element={<StatisticsPage />} />
          <Route path="/edukasi" element={<EdukasiPage />} />
          <Route path="/about" element={<AboutPage />} />
        </Route>

        {/* Admin routes */}
        <Route path="/admin/login" element={<AdminLoginPage />} />
        <Route
          path="/admin"
          element={
            <ProtectedRoute>
              <AdminDashboardPage />
            </ProtectedRoute>
          }
        />
      </Routes>
    </>
  );
}
