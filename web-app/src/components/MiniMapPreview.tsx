import { MapContainer, TileLayer, Marker } from 'react-leaflet';
import L from 'leaflet';
import { Link } from 'react-router-dom';
import { ArrowRight, MapPin } from 'lucide-react';
import { useLaporan } from '../hooks/useLaporan';
import type { Laporan } from '../types/laporan';

const JENANGAN_CENTER: [number, number] = [-7.876, 111.470];

const FALLBACK_POINTS: { pos: [number, number]; status: string; label: string }[] = [
  { pos: [-7.872, 111.467], status: 'AMAN', label: 'Desa Jenangan' },
  { pos: [-7.880, 111.474], status: 'WASPADA', label: 'Desa Ngebel' },
  { pos: [-7.875, 111.465], status: 'BAHAYA', label: 'Desa Paringan' },
  { pos: [-7.869, 111.472], status: 'AMAN', label: 'Desa Setono' },
  { pos: [-7.878, 111.478], status: 'WASPADA', label: 'Desa Pintu' },
];

const MARKER_COLORS: Record<string, string> = {
  AMAN: '#388E3C',
  WASPADA: '#F57C00',
  BAHAYA: '#D32F2F',
};

function createMarkerIcon(status: string): L.DivIcon {
  const color = MARKER_COLORS[status] || '#999';
  return L.divIcon({
    className: 'mini-map-marker',
    html: `<div style="
      width:16px;height:16px;
      background:${color};
      border-radius:50%;
      border:2.5px solid white;
      box-shadow:0 2px 6px rgba(0,0,0,0.3);
    "></div>`,
    iconSize: [16, 16],
    iconAnchor: [8, 8],
  });
}

function Legend() {
  return (
    <div className="flex items-center gap-5 justify-center mt-4">
      {[
        { status: 'AMAN', label: 'Aman', color: '#388E3C' },
        { status: 'WASPADA', label: 'Waspada', color: '#F57C00' },
        { status: 'BAHAYA', label: 'Bahaya', color: '#D32F2F' },
      ].map((item) => (
        <div key={item.status} className="flex items-center gap-1.5">
          <span
            className="h-3 w-3 rounded-full border-2 border-white shadow-sm shrink-0"
            style={{ backgroundColor: item.color }}
          />
          <span className="text-xs text-text-secondary">{item.label}</span>
        </div>
      ))}
    </div>
  );
}

export function MiniMapPreview() {
  const { data, isLoading } = useLaporan({ limit: 30 });

  const displayPoints: { pos: [number, number]; status: string }[] =
    data && data.length > 0
      ? data.map((r: Laporan) => ({
          pos: [r.latitude, r.longitude] as [number, number],
          status: r.status,
        }))
      : FALLBACK_POINTS;

  const count = displayPoints.length;

  return (
    <section id="peta" className="bg-surface scroll-mt-20">
      <div className="max-w-5xl mx-auto px-6 sm:px-8 lg:px-10 py-20 sm:py-28">
        <div className="text-center mb-6 sm:mb-8">
          <span className="text-xs font-semibold text-primary uppercase tracking-widest">
            Peta Interaktif
          </span>
          <h2 className="text-xl sm:text-2xl lg:text-3xl font-bold text-text-primary mt-2">
            Pantau Area Jenangan
          </h2>
          <p className="text-sm text-text-secondary mt-2 max-w-lg mx-auto">
            {isLoading || data.length === 0
              ? 'Sebaran titik laporan retakan tanah di Kecamatan Jenangan, Ponorogo.'
              : `${count} titik laporan retakan tanah dari warga.`}
          </p>
        </div>

        <div className="rounded-2xl shadow-lg">
          <div className="rounded-2xl overflow-hidden border border-divider">
            <MapContainer
            center={JENANGAN_CENTER}
            zoom={13}
            className="h-[300px] sm:h-[380px] w-full z-0"
            scrollWheelZoom={false}
            zoomControl={false}
            dragging={false}
          >
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            {displayPoints.map((p, i) => (
              <Marker key={i} position={p.pos} icon={createMarkerIcon(p.status)} />
            ))}
          </MapContainer>
          </div>
        </div>

        <Legend />

        <div className="text-center mt-5">
          <Link
            to="/dashboard"
            className="inline-flex items-center gap-2 text-sm font-semibold text-primary hover:underline"
          >
            <MapPin className="h-4 w-4" />
            Buka Peta Interaktif Lengkap
            <ArrowRight className="h-4 w-4" />
          </Link>
        </div>
      </div>
    </section>
  );
}
