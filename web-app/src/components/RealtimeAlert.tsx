import { useCallback, useEffect, useRef, useState } from 'react';
import { Wifi, WifiOff } from 'lucide-react';
import { supabase } from '../lib/supabase';
import { useToast } from '../context/ToastContext';

export function RealtimeAlert() {
  const { toast } = useToast();
  const channelId = useRef(`bahaya-${Math.random().toString(36).slice(2, 8)}`);
  const [connected, setConnected] = useState(false);
  const [showBanner, setShowBanner] = useState(false);
  const bannerTimer = useRef<ReturnType<typeof setTimeout>>();

  const updateConnection = useCallback((status: string) => {
    if (status === 'SUBSCRIBED') {
      setConnected(true);
      setShowBanner(false);
      clearTimeout(bannerTimer.current);
    } else if (status === 'CHANNEL_ERROR' || status === 'CLOSED') {
      setConnected(false);
      setShowBanner(true);
      clearTimeout(bannerTimer.current);
      bannerTimer.current = setTimeout(() => setShowBanner(false), 8000);
    }
  }, []);

  useEffect(() => {
    if (!supabase) return;

    const channel = supabase
      .channel(channelId.current)
      .on(
        'postgres_changes',
        { event: 'INSERT', schema: 'public', table: 'laporan', filter: 'status=eq.BAHAYA' },
        (payload) => {
          const record = payload.new as Record<string, unknown>;
          toast(
            'error',
            `Laporan BAHAYA baru di ${record.nama_lokasi}! Segera cek dashboard.`,
          );
        },
      )
      .subscribe((status) => updateConnection(status));

    return () => {
      clearTimeout(bannerTimer.current);
      supabase.removeChannel(channel);
    };
  }, [toast, updateConnection]);

  return (
    <>
      {showBanner && !connected && (
        <div className="fixed top-4 left-1/2 -translate-x-1/2 z-[9999] animate-slide-up">
          <div className="flex items-center gap-2 rounded-xl border border-waspada/30 bg-waspada-bg px-4 py-2.5 shadow-lg">
            <WifiOff className="h-4 w-4 text-waspada shrink-0" />
            <span className="text-xs font-medium text-waspada">
              Koneksi realtime terputus. Mencoba menghubungkan kembali...
            </span>
          </div>
        </div>
      )}
      {connected && (
        <div className="fixed bottom-20 right-4 z-[9999]">
          <div className="flex items-center gap-1.5 rounded-full bg-aman-bg border border-aman/20 px-3 py-1.5 shadow-sm">
            <Wifi className="h-3 w-3 text-aman" />
            <span className="text-[10px] font-medium text-aman">Realtime aktif</span>
          </div>
        </div>
      )}
    </>
  );
}
