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

const DEFAULT_CENTER: [number, number] = [-7.876, 111.470]; // Jenangan, Ponorogo

function createIcon(status: string): L.DivIcon {
  const colors: Record<string, string> = {
    AMAN: '#388E3C',
    WASPADA: '#F57C00',
    BAHAYA: '#D32F2F',
  };
  const color = colors[status] || '#999';
  return L.divIcon({
    className: 'custom-marker',
    html: `<div style="
      width:24px;height:24px;
      background:${color};
      border-radius:50%;
      border:3px solid white;
      box-shadow:0 2px 6px rgba(0,0,0,0.35);
    "></div>`,
    iconSize: [24, 24],
    iconAnchor: [12, 12],
    popupAnchor: [0, -14],
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
    <div className={`relative rounded-xl overflow-hidden ${className}`}>
      {/* Map selalu render — biar peta Ponorogo selalu kelihatan */}
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
            icon={createIcon(report.status)}
          >
            <Popup>
              <LaporanMapPopup report={report} />
            </Popup>
          </Marker>
        ))}
      </MapContainer>

      {/* Overlay: loading / error / empty */}
      {isLoading && (
        <div className="absolute inset-0 z-[999] flex items-center justify-center bg-surface/70 backdrop-blur-sm">
          <LoadingSpinner text="Memuat peta..." />
        </div>
      )}

      {!isLoading && error && (
        <div className="absolute inset-0 z-[999] flex items-center justify-center bg-surface/80 backdrop-blur-sm">
          <ErrorState message={error} onRetry={onRetry ?? undefined} />
        </div>
      )}

      {!isLoading && !error && reports.length === 0 && (
        <div className="absolute inset-0 z-[999] flex items-center justify-center bg-surface/40 backdrop-blur-[2px] pointer-events-none">
          <div className="bg-card/90 backdrop-blur rounded-2xl shadow-lg px-6 py-5 text-center max-w-xs border border-divider/50">
            <div className="flex h-12 w-12 items-center justify-center rounded-full bg-primary-surface mx-auto mb-3">
              <div className="h-6 w-6 rounded-full bg-primary/20 flex items-center justify-center">
                <div className="h-2.5 w-2.5 rounded-full bg-primary" />
              </div>
            </div>
            <p className="text-sm font-semibold text-text-primary mb-1">Belum ada titik laporan</p>
            <p className="text-xs text-text-secondary">
              Laporan retakan tanah dari aplikasi mobile akan muncul di peta ini secara real-time.
            </p>
          </div>
        </div>
      )}
    </div>
  );
}
