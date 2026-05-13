import { Link } from 'react-router-dom';
import { MapPin, Github, Mail, Phone } from 'lucide-react';

const FOOTER_LINKS = [
  { label: 'Navigasi', items: [
    { to: '/', label: 'Beranda' },
    { to: '/dashboard', label: 'Dashboard' },
    { to: '/reports', label: 'Laporan' },
    { to: '/about', label: 'Tentang' },
  ]},
  { label: 'Sumber Daya', items: [
    { to: '/edukasi', label: 'Edukasi Bencana' },
    { to: 'https://github.com/jaweed3/retakId', label: 'GitHub Repository', external: true },
  ]},
];

export function Footer() {
  return (
    <footer className="bg-card">
      <div className="max-w-6xl mx-auto px-4 sm:px-6 py-8 sm:py-12">
        <div className="grid sm:grid-cols-3 gap-8 sm:gap-10">
          {/* Brand */}
          <div>
            <div className="flex items-center gap-2.5 mb-3">
              <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-gradient-to-br from-primary to-primary-light shadow-sm">
                <MapPin className="h-4.5 w-4.5 text-white" />
              </div>
              <div>
                <span className="text-base font-bold text-text-primary">Retak.id</span>
                <span className="text-[10px] text-text-secondary block -mt-0.5">Crowdsourcing Dashboard</span>
              </div>
            </div>
            <p className="text-xs text-text-secondary leading-relaxed mb-3">
              Platform deteksi dini retakan tanah berbasis partisipasi warga dan
              kecerdasan buatan. Dibangun untuk IYREF 2026 Semi-Final.
            </p>
            <p className="text-[10px] text-text-secondary/60">
              &copy; {new Date().getFullYear()} Tim Retak.id &mdash; IYREF
            </p>
          </div>

          {/* Links */}
          {FOOTER_LINKS.map((group) => (
            <div key={group.label}>
              <h4 className="text-xs font-semibold text-text-primary uppercase tracking-wider mb-3">
                {group.label}
              </h4>
              <ul className="space-y-2">
                {group.items.map((item) =>
                  'external' in item ? (
                    <li key={item.label}>
                      <a
                        href={item.to}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-xs text-text-secondary hover:text-text-primary transition-colors inline-flex items-center gap-1.5"
                      >
                        <Github className="h-3 w-3" />
                        {item.label}
                      </a>
                    </li>
                  ) : (
                    <li key={item.label}>
                      <Link
                        to={item.to}
                        className="text-xs text-text-secondary hover:text-text-primary transition-colors"
                      >
                        {item.label}
                      </Link>
                    </li>
                  ),
                )}
              </ul>
            </div>
          ))}
        </div>

        {/* Bottom bar */}
        <div className="border-t border-divider/60 mt-8 pt-5 flex flex-col sm:flex-row items-center justify-between gap-3">
          <div className="flex items-center gap-4 text-[10px] text-text-secondary/60">
            <span className="inline-flex items-center gap-1">
              <MapPin className="h-3 w-3" />
              Jenangan, Ponorogo
            </span>
            <span className="inline-flex items-center gap-1">
              <Mail className="h-3 w-3" />
              retak.id@email.com
            </span>
          </div>
          <span className="text-[10px] text-text-secondary/40">
            Dibangun dengan React, Vite, Leaflet &amp; Supabase
          </span>
        </div>
      </div>
    </footer>
  );
}
