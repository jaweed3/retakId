import { useCallback, useState } from 'react';
import { Upload, AlertTriangle, MapPin } from 'lucide-react';

interface GpsData {
  latitude: number;
  longitude: number;
}

interface ImageUploadPreviewProps {
  onImageSelect: (file: File, preview: string, gps: GpsData | null) => void;
}

export function ImageUploadPreview({ onImageSelect }: ImageUploadPreviewProps) {
  const [preview, setPreview] = useState<string | null>(null);
  const [gps, setGps] = useState<GpsData | null>(null);
  const [gpsLoading, setGpsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleFile = useCallback(async (file: File) => {
    setError(null);
    if (!file.type.startsWith('image/')) {
      setError('File harus berupa gambar (JPG/PNG).');
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      setError('Ukuran foto maksimal 10MB.');
      return;
    }

    const previewUrl = URL.createObjectURL(file);
    setPreview(previewUrl);
    setGps(null);
    setGpsLoading(true);

    // Extract EXIF GPS
    try {
      const exifr = (await import('exifr')).default;
      const exif = await exifr.parse(file, ['latitude', 'longitude']);
      if (exif && exif.latitude != null && exif.longitude != null) {
        const gpsData = { latitude: exif.latitude, longitude: exif.longitude };
        setGps(gpsData);
        onImageSelect(file, previewUrl, gpsData);
      } else {
        onImageSelect(file, previewUrl, null);
      }
    } catch {
      onImageSelect(file, previewUrl, null);
    } finally {
      setGpsLoading(false);
    }
  }, [onImageSelect]);

  return (
    <div>
      <label className="block text-xs font-medium text-text-secondary mb-2">
        Foto Retakan <span className="text-bahaya">*</span>
      </label>

      {preview ? (
        <div className="relative rounded-xl overflow-hidden border border-divider">
          <img src={preview} alt="Preview" className="w-full max-h-64 object-cover" />
          <button
            type="button"
            onClick={() => { setPreview(null); setGps(null); }}
            className="absolute top-2 right-2 rounded-lg bg-black/50 px-2.5 py-1 text-xs text-white hover:bg-black/70 transition-colors"
          >
            Ganti foto
          </button>

          {/* GPS info */}
          <div className="absolute bottom-2 left-2 right-2">
            {gpsLoading ? (
              <span className="inline-flex items-center gap-1.5 rounded-lg bg-black/50 px-2.5 py-1 text-[11px] text-white/80">
                Membaca lokasi dari foto...
              </span>
            ) : gps ? (
              <span className="inline-flex items-center gap-1.5 rounded-lg bg-primary/80 px-2.5 py-1 text-[11px] text-white">
                <MapPin className="h-3 w-3" />
                {gps.latitude.toFixed(5)}, {gps.longitude.toFixed(5)}
              </span>
            ) : (
              <span className="inline-flex items-center gap-1.5 rounded-lg bg-waspada/80 px-2.5 py-1 text-[11px] text-white">
                <AlertTriangle className="h-3 w-3" />
                Foto tidak memiliki data GPS
              </span>
            )}
          </div>
        </div>
      ) : (
        <label className="flex flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed border-divider bg-card hover:border-primary/30 hover:bg-primary-surface/20 transition-colors cursor-pointer py-10 px-4">
          <Upload className="h-8 w-8 text-text-secondary/40" />
          <span className="text-sm font-medium text-text-secondary">Klik untuk upload foto</span>
          <span className="text-[10px] text-text-secondary/50">JPG atau PNG, maks 10MB</span>
          <input
            type="file"
            accept="image/jpeg,image/png"
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (file) handleFile(file);
            }}
            className="hidden"
          />
        </label>
      )}

      {error && (
        <p className="text-xs text-bahaya mt-2 flex items-center gap-1">
          <AlertTriangle className="h-3 w-3" /> {error}
        </p>
      )}
    </div>
  );
}
