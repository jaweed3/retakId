import { MapContainer, TileLayer, Marker, Popup } from 'react-leaflet';
import L from 'leaflet';
import type { Laporan } from '../types/laporan';
import { LaporanMapPopup } from './LaporanMapPopup';
import { LoadingSpinner } from './LoadingSpinner';
import { ErrorState } from './ErrorState';
import { EmptyState } from './EmptyState';

const DEFAULT_CENTER: [number, number] = [-7.876, 111.470];

function createIcon(status: string): L.DivIcon {
  const colors: Record<string, string> = { AMAN: '#388E3C', WASPADA: '#F57C00', BAHAYA: '#D32F2F' };
  return L.divIcon({
    className: 'custom-marker',
    html: `<div style="width:22px;height:22px;background:${colors[status] || '#999'};border-radius:50%;border:3px solid white;box-shadow:0 1px 4px rgba(0,0,0,0.3);"></div>`,
    iconSize: [22, 22],
    iconAnchor: [11, 11],
    popupAnchor: [0, -14],
  });
}

export function MapView({
  reports, isLoading, error, onRetry, center = DEFAULT_CENTER, zoom = 13, className = 'h-[60vh] lg:h-full',
}: {
  reports: Laporan[];
  isLoading?: boolean;
  error?: string | null;
  onRetry?: () => void;
  center?: [number, number];
  zoom?: number;
  className?: string;
}) {
  if (isLoading) {
    return <div className={`flex items-center justify-center rounded-xl bg-divider/20 ${className}`}><LoadingSpinner text="Memuat peta..." /></div>;
  }
  if (error) {
    return <div className={`flex items-center justify-center rounded-xl bg-divider/20 ${className}`}><ErrorState message={error} onRetry={onRetry} /></div>;
  }
  if (reports.length === 0) {
    return <div className={`flex items-center justify-center rounded-xl bg-divider/20 ${className}`}><EmptyState title="Belum ada titik laporan" description="Laporan dari aplikasi mobile akan muncul di peta ini." /></div>;
  }

  return (
    <div className={`rounded-xl overflow-hidden ${className}`}>
      <MapContainer center={center} zoom={zoom} className="h-full w-full" scrollWheelZoom>
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        {reports.map((r) => (
          <Marker key={r.id} position={[r.latitude, r.longitude]} icon={createIcon(r.status)}>
            <Popup><LaporanMapPopup report={r} /></Popup>
          </Marker>
        ))}
      </MapContainer>
    </div>
  );
}
