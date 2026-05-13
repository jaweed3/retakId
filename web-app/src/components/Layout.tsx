import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { Home, LayoutDashboard, FileText, Info, MapPin, LogIn } from 'lucide-react';
import { ThemeToggle } from './ThemeToggle';
import { Footer } from './Footer';
import { cn } from '../utils/cn';

const NAV_ITEMS = [
  { to: '/', label: 'Beranda', icon: Home },
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/reports', label: 'Laporan', icon: FileText },
  { to: '/about', label: 'Tentang', icon: Info },
];

export function Layout() {
  const location = useLocation();

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    cn(
      'flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
      isActive
        ? 'bg-primary-surface text-primary font-semibold'
        : 'text-text-secondary hover:text-text-primary hover:bg-divider/30',
    );

  const mobileActive = (to: string) => {
    if (to === '/') return location.pathname === '/';
    return location.pathname.startsWith(to);
  };

  return (
    <div className="flex h-screen overflow-hidden bg-surface">
      {/* ─── Sidebar (desktop) ─── */}
      <aside className="hidden lg:flex w-64 shrink-0 flex-col border-r border-divider bg-card">
        {/* Logo */}
        <div className="px-5 py-5 border-b border-divider">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-primary to-primary-light shadow-md shadow-primary/25">
              <MapPin className="h-5 w-5 text-white" />
            </div>
            <div>
              <h1 className="text-base font-bold text-text-primary leading-tight">Retak.id</h1>
              <p className="text-[11px] text-text-secondary">Crowdsourcing Dashboard</p>
            </div>
          </div>
        </div>

        {/* Nav */}
        <nav className="flex flex-col gap-1 px-3 py-4 flex-1">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={linkClass}
              end={item.to === '/'}
            >
              <item.icon className="h-5 w-5" />
              {item.label}
            </NavLink>
          ))}
        </nav>

        {/* Footer sidebar */}
        <div className="px-4 py-3 border-t border-divider space-y-3">
          <NavLink
            to="/admin/login"
            className="flex items-center gap-2 text-[11px] text-text-secondary/60 hover:text-text-secondary transition-colors px-1"
          >
            <LogIn className="h-3 w-3" />
            Login Admin
          </NavLink>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="h-1.5 w-1.5 rounded-full bg-primary" />
              <span className="text-xs text-text-secondary">Live</span>
            </div>
            <ThemeToggle />
          </div>
        </div>
      </aside>

      {/* ─── Main content ─── */}
      <main className="flex-1 flex flex-col overflow-hidden">
        {/* Mobile header */}
        <header className="lg:hidden flex items-center justify-between px-3 py-2 border-b border-divider bg-card shrink-0">
          <div className="flex items-center gap-2.5">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-primary to-primary-light shadow-sm">
              <MapPin className="h-4 w-4 text-white" />
            </div>
            <div>
              <h1 className="text-sm font-bold text-text-primary leading-tight">Retak.id</h1>
              <p className="text-[10px] text-text-secondary">Dashboard</p>
            </div>
          </div>
          <div className="flex items-center gap-1">
            <ThemeToggle />
          </div>
        </header>

        {/* Page content */}
        <div className="flex-1 overflow-auto">
          <Outlet />
          <Footer />
        </div>

        {/* ─── Bottom nav (mobile) ─── */}
        <nav className="lg:hidden flex items-center justify-around border-t border-divider bg-card py-1.5 shrink-0 safe-bottom">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={cn(
                'flex flex-col items-center gap-0.5 py-1 px-3 text-[10px] font-medium min-w-0 transition-colors',
                mobileActive(item.to) ? 'text-primary' : 'text-text-secondary/50',
              )}
              end={item.to === '/'}
            >
              <item.icon className={cn('h-5 w-5', mobileActive(item.to) ? 'text-primary' : 'text-text-secondary/40')} />
              {item.label}
            </NavLink>
          ))}
        </nav>
      </main>
    </div>
  );
}
