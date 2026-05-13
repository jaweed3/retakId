import { useState } from 'react';
import { MapContainer, TileLayer, Marker, useMapEvents } from 'react-leaflet';
import L from 'leaflet';
import { MapPin } from 'lucide-react';

const DEFAULT_CENTER: [number, number] = [-7.876, 111.470];

const markerIcon = L.divIcon({
  className: 'location-picker-marker',
  html: `<div style="
    width:24px;height:24px;
    background:#2E7D32;
    border-radius:50% 50% 50% 0;
    transform:rotate(-45deg);
    border:3px solid white;
    box-shadow:0 2px 8px rgba(0,0,0,0.35);
  "></div>`,
  iconSize: [24, 28],
  iconAnchor: [12, 24],
});

interface LocationPickerProps {
  onLocationSelect: (lat: number, lng: number) => void;
  selected?: { lat: number; lng: number } | null;
}

function MapClickHandler({ onClick }: { onClick: (lat: number, lng: number) => void }) {
  useMapEvents({
    click(e) {
      onClick(e.latlng.lat, e.latlng.lng);
    },
  });
  return null;
}

export function LocationPicker({ onLocationSelect, selected }: LocationPickerProps) {
  const [position, setPosition] = useState<[number, number] | null>(
    selected ? [selected.lat, selected.lng] : null,
  );

  const handleClick = (lat: number, lng: number) => {
    setPosition([lat, lng]);
    onLocationSelect(lat, lng);
  };

  return (
    <div>
      <label className="block text-xs font-medium text-text-secondary mb-2">
        Pilih Lokasi di Peta <span className="text-bahaya">*</span>
      </label>
      <p className="text-[11px] text-text-secondary/60 mb-2">
        Klik pada peta untuk menandai lokasi retakan.
      </p>
      <div className="rounded-xl overflow-hidden border border-divider h-[250px] sm:h-[300px]">
        <MapContainer
          center={DEFAULT_CENTER}
          zoom={13}
          className="h-full w-full z-0"
          scrollWheelZoom
        >
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />
          <MapClickHandler onClick={handleClick} />
          {position && <Marker position={position} icon={markerIcon} />}
        </MapContainer>
      </div>
      {position && (
        <p className="text-[11px] text-primary mt-2 flex items-center gap-1">
          <MapPin className="h-3 w-3" />
          {position[0].toFixed(5)}, {position[1].toFixed(5)}
        </p>
      )}
    </div>
  );
}
