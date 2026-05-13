import { useEffect, useRef } from 'react';
import { supabase } from '../lib/supabase';
import { useToast } from '../context/ToastContext';

export function RealtimeAlert() {
  const { toast } = useToast();
  const channelId = useRef(`bahaya-${Math.random().toString(36).slice(2, 8)}`);

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
      .subscribe();

    return () => {
      supabase?.removeChannel(channel);
    };
  }, [toast]);

  return null;
}
