import { useState } from 'react';
import { Navigate } from 'react-router-dom';
import { MapPin, LogIn, Eye, EyeOff, AlertCircle } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { ThemeToggle } from '../components/ThemeToggle';
import { LoadingSpinner } from '../components/LoadingSpinner';
import { SEOMeta } from '../components/SEOMeta';

export function AdminLoginPage() {
  const { isAdmin, isLoading, signIn } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (isLoading) {
    return <LoadingSpinner text="Memeriksa sesi..." />;
  }

  if (isAdmin) {
    return <Navigate to="/admin" replace />;
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    const result = await signIn(email, password);
    setSubmitting(false);
    if (result.error) setError(result.error);
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-surface px-4">
      <SEOMeta title="Login Admin" noindex />
      <div className="absolute top-4 right-4"><ThemeToggle /></div>
      <div className="w-full max-w-sm">
        {/* Logo */}
        <div className="text-center mb-8">
          <div className="flex h-14 w-14 mx-auto items-center justify-center rounded-2xl bg-gradient-to-br from-primary to-primary-light shadow-lg shadow-primary/25 mb-4">
            <MapPin className="h-7 w-7 text-white" />
          </div>
          <h1 className="text-xl font-bold text-text-primary">Retak.id</h1>
          <p className="text-sm text-text-secondary mt-1">Login Admin</p>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="rounded-2xl bg-card border border-divider shadow-sm p-6 space-y-4">
          {error && (
            <div className="flex items-center gap-2 rounded-lg bg-bahaya-bg border border-bahaya/20 px-3 py-2.5 text-xs text-bahaya">
              <AlertCircle className="h-4 w-4 shrink-0" />
              {error}
            </div>
          )}

          <div>
            <label className="block text-xs font-medium text-text-secondary mb-1.5">Email</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoFocus
              className="w-full rounded-xl border border-divider bg-surface px-3.5 py-2.5 text-sm text-text-primary placeholder:text-text-secondary/40 focus:outline-none focus:ring-2 focus:ring-primary/30 transition-shadow"
              placeholder="admin@unida.gontor.ac.id"
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-text-secondary mb-1.5">Password</label>
            <div className="relative">
              <input
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                className="w-full rounded-xl border border-divider bg-surface px-3.5 py-2.5 pr-10 text-sm text-text-primary placeholder:text-text-secondary/40 focus:outline-none focus:ring-2 focus:ring-primary/30 transition-shadow"
                placeholder="••••••••"
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-text-secondary/60 hover:text-text-secondary"
              >
                {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
          </div>

          <button
            type="submit"
            disabled={submitting}
            className="w-full flex items-center justify-center gap-2 rounded-xl bg-primary py-2.5 text-sm font-semibold text-white shadow-md shadow-primary/20 hover:bg-primary-light transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
          >
            <LogIn className="h-4 w-4" />
            {submitting ? 'Masuk...' : 'Masuk'}
          </button>
        </form>

        <p className="text-center text-[11px] text-text-secondary/60 mt-4">
          Hanya untuk admin dan BPBD. Akun dibuat oleh administrator.
        </p>
      </div>
    </div>
  );
}
