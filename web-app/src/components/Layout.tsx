import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';
import { Home, LayoutDashboard, FileText, BarChart3, Info, LogIn } from 'lucide-react';
import { ThemeToggle } from './ThemeToggle';
import { Footer } from './Footer';
import { RealtimeAlert } from './RealtimeAlert';
import { cn } from '../utils/cn';

const NAV_ITEMS = [
  { to: '/', label: 'Beranda', icon: Home },
  { to: '/dashboard', label: 'Pemantauan', icon: LayoutDashboard },
  { to: '/reports', label: 'Laporan', icon: FileText },
  { to: '/statistics', label: 'Statistik', icon: BarChart3 },
  { to: '/about', label: 'Tentang', icon: Info },
];

export function Layout() {
  const location = useLocation();

  const isActive = (to: string) => {
    if (to === '/') return location.pathname === '/';
    return location.pathname.startsWith(to);
  };

  return (
    <div className="flex flex-col h-screen bg-surface">
      <RealtimeAlert />
      {/* ─── Top Navbar (desktop) ─── */}
      <header className="hidden lg:flex items-center justify-between h-16 shrink-0 border-b bg-white dark:bg-black border-gray-200 dark:border-white/10 px-6">
        {/* Left: Logo — clickable to home */}
        <Link to="/" className="flex items-center gap-3">
          <img src="/retak-favicon.svg" alt="Retak.id" className="h-9 w-9 rounded-xl shadow-md shadow-primary/25" />
          <div>
            <span className="text-lg font-bold text-gray-900 dark:text-white">Retak.id</span>
            <span className="text-xs text-gray-500 dark:text-white/60 ml-2">Crowdsourcing Dashboard</span>
          </div>
        </Link>

        {/* Center: Nav Buttons */}
        <nav className="flex items-center gap-1.5">
          {NAV_ITEMS.map((item) => {
            const active = isActive(item.to);
            return (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/'}
                className={cn(
                  'flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-medium transition-all duration-200',
                  active
                    ? 'bg-primary text-white shadow-sm shadow-primary/20'
                    : 'text-gray-500 dark:text-white/60 hover:text-gray-900 dark:hover:text-white hover:bg-gray-100 dark:hover:bg-white/10',
                )}
              >
                <item.icon className="h-4 w-4" />
                {item.label}
              </NavLink>
            );
          })}
        </nav>

        {/* Right: Actions */}
        <div className="flex items-center gap-2">
          <NavLink
            to="/admin/login"
            className="flex items-center gap-1.5 rounded-lg border border-gray-200 dark:border-white/20 px-3 py-1.5 text-[11px] text-gray-400 dark:text-white/50 hover:text-gray-600 dark:hover:text-white hover:border-gray-300 dark:hover:border-white/40 transition-all"
          >
            <LogIn className="h-3 w-3" />
            Admin
          </NavLink>
          <ThemeToggle />
        </div>
      </header>

      {/* Mobile header */}
      <header className="lg:hidden flex items-center justify-between px-3 py-2 border-b border-divider bg-card shrink-0">
        <Link to="/" className="flex items-center gap-2.5">
          <img src="/retak-favicon.svg" alt="Retak.id" className="h-7 w-7 rounded-lg shadow-sm" />
          <div>
            <h1 className="text-sm font-bold text-text-primary leading-tight">Retak.id</h1>
            <p className="text-[10px] text-text-secondary">Monitoring</p>
          </div>
        </Link>
        <div className="flex items-center gap-1">
          <ThemeToggle />
        </div>
      </header>

      {/* ─── Main scrollable content ─── */}
      <main className="flex-1 overflow-y-auto scroll-smooth pb-16 lg:pb-0" data-main-content>
        <Outlet />
        <Footer />
      </main>

      {/* ─── Bottom nav (mobile) — fixed to viewport ─── */}
      <nav className="lg:hidden fixed bottom-0 left-0 right-0 z-50 flex items-center justify-around border-t border-divider bg-card py-1.5 safe-bottom shadow-lg">
        {NAV_ITEMS.map((item) => {
          const active = isActive(item.to);
          return (
            <NavLink
              key={item.to}
              to={item.to}
              className={cn(
                'flex flex-col items-center gap-0.5 py-1 px-3 text-[10px] font-medium min-w-0 transition-colors',
                active ? 'text-primary' : 'text-text-secondary/50',
              )}
              end={item.to === '/'}
            >
              <item.icon className={cn('h-5 w-5', active ? 'text-primary' : 'text-text-secondary/40')} />
              {item.label}
            </NavLink>
          );
        })}
      </nav>
    </div>
  );
}
