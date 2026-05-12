import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { LayoutDashboard, FileText, Menu, X } from 'lucide-react';
import { useState } from 'react';
import { ThemeToggle } from './ThemeToggle';
import { cn } from '../utils/cn';

const NAV_ITEMS = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/reports', label: 'Laporan', icon: FileText },
];

export function Layout() {
  const [mobileOpen, setMobileOpen] = useState(false);
  const location = useLocation();

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    cn('flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
      isActive ? 'bg-primary-surface text-primary' : 'text-text-secondary hover:text-text-primary hover:bg-divider/30');

  const mobileActive = (to: string) => to === '/' ? location.pathname === '/' : location.pathname.startsWith(to);

  return (
    <div className="flex h-screen overflow-hidden bg-surface">
      {/* Sidebar (desktop) */}
      <aside className="hidden lg:flex w-60 shrink-0 flex-col border-r border-divider bg-card">
        <div className="flex items-center gap-2.5 px-5 py-4 border-b border-divider">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary">
            <span className="text-white font-bold text-sm">R</span>
          </div>
          <div>
            <h1 className="text-sm font-bold text-text-primary">Retak.id</h1>
            <p className="text-[10px] text-text-secondary">Crowdsourcing Dashboard</p>
          </div>
        </div>
        <nav className="flex flex-col gap-1 px-3 py-4 flex-1">
          {NAV_ITEMS.map((item) => (
            <NavLink key={item.to} to={item.to} className={linkClass} end={item.to === '/'}>
              <item.icon className="h-5 w-5" />{item.label}
            </NavLink>
          ))}
        </nav>
        <div className="px-3 py-3 border-t border-divider flex items-center justify-between">
          <span className="text-xs text-text-secondary">Tampilan</span>
          <ThemeToggle />
        </div>
      </aside>

      {/* Main */}
      <main className="flex-1 flex flex-col overflow-hidden">
        {/* Mobile header */}
        <header className="lg:hidden flex items-center justify-between px-4 py-3 border-b border-divider bg-card shrink-0">
          <div className="flex items-center gap-2">
            <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-primary">
              <span className="text-white font-bold text-xs">R</span>
            </div>
            <h1 className="text-sm font-bold text-text-primary">Retak.id</h1>
          </div>
          <div className="flex items-center gap-1">
            <ThemeToggle />
            <button onClick={() => setMobileOpen(!mobileOpen)} className="flex h-9 w-9 items-center justify-center rounded-lg text-text-secondary hover:text-text-primary hover:bg-divider/40 lg:hidden" aria-label="Toggle menu">
              {mobileOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
            </button>
          </div>
        </header>
        <div className="flex-1 overflow-auto"><Outlet /></div>
        {/* Bottom nav (mobile) */}
        <nav className="lg:hidden flex items-center justify-around border-t border-divider bg-card py-1.5 shrink-0">
          {NAV_ITEMS.map((item) => (
            <NavLink key={item.to} to={item.to} className={cn('flex flex-col items-center gap-1 py-1 px-3 text-xs font-medium min-w-0', mobileActive(item.to) ? 'text-primary' : 'text-text-secondary/60')} end={item.to === '/'}>
              <item.icon className={cn('h-5 w-5', mobileActive(item.to) ? 'text-primary' : 'text-text-secondary/50')} />{item.label}
            </NavLink>
          ))}
        </nav>
      </main>
    </div>
  );
}
