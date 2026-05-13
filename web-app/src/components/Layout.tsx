import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { Home, LayoutDashboard, FileText, BarChart3, Info, MapPin, LogIn } from 'lucide-react';
import { ThemeToggle } from './ThemeToggle';
import { Footer } from './Footer';
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
    <div className="flex flex-col h-screen overflow-hidden bg-surface">
      {/* ─── Top Navbar (desktop) ─── */}
      <header className="hidden lg:flex items-center justify-between h-16 shrink-0 border-b border-divider bg-card px-6">
        {/* Left: Logo */}
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-primary to-primary-light shadow-md shadow-primary/25">
            <MapPin className="h-4.5 w-4.5 text-white" />
          </div>
          <div>
            <span className="text-base font-bold text-text-primary">Retak.id</span>
            <span className="text-xs text-text-secondary ml-2">Crowdsourcing Dashboard</span>
          </div>
        </div>

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
                    : 'text-text-secondary hover:text-text-primary hover:bg-divider/40',
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
            className="flex items-center gap-1.5 rounded-lg border border-divider/60 px-3 py-1.5 text-[11px] text-text-secondary/70 hover:text-text-secondary hover:border-divider transition-all"
          >
            <LogIn className="h-3 w-3" />
            Admin
          </NavLink>
          <ThemeToggle />
        </div>
      </header>

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
              <p className="text-[10px] text-text-secondary">Monitoring</p>
            </div>
          </div>
          <div className="flex items-center gap-1">
            <ThemeToggle />
          </div>
        </header>

        {/* Page content */}
        <div className="flex-1 overflow-auto scroll-smooth" data-main-content>
          <Outlet />
          <Footer />
        </div>

        {/* ─── Bottom nav (mobile) ─── */}
        <nav className="lg:hidden flex items-center justify-around border-t border-divider bg-card py-1.5 shrink-0 safe-bottom">
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
      </main>
    </div>
  );
}
