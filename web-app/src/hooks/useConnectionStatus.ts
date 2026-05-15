import { useCallback, useEffect, useRef, useState } from 'react';
import { supabase } from '../lib/supabase';

export type ConnectionStatus = 'connected' | 'disconnected' | 'connecting' | 'unavailable';

interface UseConnectionStatusReturn {
  status: ConnectionStatus;
  isOnline: boolean;
  isRealtimeConnected: boolean;
}

export function useConnectionStatus(): UseConnectionStatusReturn {
  const [isOnline, setIsOnline] = useState(navigator.onLine);
  const [status, setStatus] = useState<ConnectionStatus>(
    supabase ? 'connecting' : 'unavailable',
  );

  const statusRef = useRef(status);
  statusRef.current = status;

  const updateFromChannel = useCallback((state: string) => {
    switch (state) {
      case 'SUBSCRIBED':
        setStatus('connected');
        break;
      case 'CONNECTING':
      case 'CHANNEL_ERROR':
        setStatus((prev) => (prev === 'connected' ? 'disconnected' : prev));
        break;
      case 'CLOSED':
        setStatus('disconnected');
        break;
      default:
        break;
    }
  }, []);

  useEffect(() => {
    const handleOnline = () => setIsOnline(true);
    const handleOffline = () => {
      setIsOnline(false);
      setStatus('disconnected');
    };

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);
    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  useEffect(() => {
    if (!supabase) {
      setStatus('unavailable');
      return;
    }
    const client = supabase;

    const healthChannelId = `health-${Math.random().toString(36).slice(2, 8)}`;
    const channel = client
      .channel(healthChannelId)
      .subscribe((state) => updateFromChannel(state));

    return () => {
      client.removeChannel(channel);
    };
  }, [updateFromChannel]);

  return {
    status,
    isOnline,
    isRealtimeConnected: status === 'connected',
  };
}
