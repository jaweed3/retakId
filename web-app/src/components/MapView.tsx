import { MapContainer, TileLayer, Marker, Popup } from 'react-leaflet';
import L from 'leaflet';
import type { Laporan } from '../types/laporan';
import { LaporanMapPopup } from './LaporanMapPopup';
import { LoadingSpinner } from './LoadingSpinner';
import { ErrorState } from './ErrorState';

interface MapViewProps {
  reports: Laporan[];
  isLoading?: boolean;
  error?: string | null;
  onRetry?: () => void;
  center?: [number, number];
  zoom?: number;
  className?: string;
}

const DEFAULT_CENTER: [number, number] = [-7.876, 111.470];

const ACTIVE_COLORS: Record<string, string> = { AMAN: '#388E3C', WASPADA: '#F57C00', BAHAYA: '#D32F2F' };

function createIcon(status: string, isResolved?: boolean): L.DivIcon {
  const color = isResolved ? '#999' : (ACTIVE_COLORS[status] || '#999');
  const opacity = isResolved ? '0.5' : '1';
  const size = isResolved ? '16' : '24';
  const border = isResolved ? '2' : '3';
  return L.divIcon({
    className: 'custom-marker',
    html: `<div style="
      width:${size}px;height:${size}px;
      background:${color};
      border-radius:50%;
      border:${border}px solid white;
      box-shadow:0 2px 6px rgba(0,0,0,0.35);
      opacity:${opacity};
    "></div>`,
    iconSize: [Number(size), Number(size)],
    iconAnchor: [Number(size)/2, Number(size)/2],
    popupAnchor: [0, -Number(size)/2],
  });
}

export function MapView({
  reports,
  isLoading,
  error,
  onRetry,
  center = DEFAULT_CENTER,
  zoom = 13,
  className = 'h-[60vh] lg:h-full',
}: MapViewProps) {
  return (
    <div className={`relative ${className}`}>
      <MapContainer
        center={center}
        zoom={zoom}
        className="h-full w-full z-0"
        scrollWheelZoom
        zoomControl={false}
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        {reports.map((report) => (
          <Marker
            key={report.id}
            position={[report.latitude, report.longitude]}
            icon={createIcon(report.status, report.is_resolved)}
          >
            <Popup>
              <LaporanMapPopup report={report} />
            </Popup>
          </Marker>
        ))}
      </MapContainer>

      {/* Overlay states — hanya saat loading/error */}
      {isLoading && (
        <div className="absolute inset-0 z-[999] flex items-center justify-center bg-surface/80">
          <LoadingSpinner text="Memuat peta..." />
        </div>
      )}

      {!isLoading && error && (
        <div className="absolute inset-0 z-[999] flex items-center justify-center bg-surface/90">
          <ErrorState message={error} onRetry={onRetry ?? undefined} />
        </div>
      )}

      {!isLoading && !error && reports.length === 0 && (
        <div className="absolute bottom-6 left-1/2 -translate-x-1/2 z-[999] pointer-events-none">
          <div className="bg-card shadow-lg rounded-xl px-5 py-4 text-center border border-divider max-w-xs">
            <p className="text-sm font-semibold text-text-primary mb-1">Belum ada titik laporan</p>
            <p className="text-xs text-text-secondary">
              Laporan dari aplikasi mobile akan muncul di peta ini.
            </p>
          </div>
        </div>
      )}
    </div>
  );
}
