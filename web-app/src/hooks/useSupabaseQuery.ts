import { useCallback, useEffect, useRef, useState } from 'react';
import { supabase, requireSupabase } from '../lib/supabase';

type QueryFn<T> = (client: ReturnType<typeof requireSupabase>) => Promise<{ data: T | null; error: unknown }>;

interface UseSupabaseQueryOptions {
  enabled?: boolean;
  retries?: number;
  onError?: (err: unknown) => void;
}

interface UseSupabaseQueryReturn<T> {
  data: T | null;
  isLoading: boolean;
  error: string | null;
  refetch: () => void;
}

export function useSupabaseQuery<T>(
  queryFn: QueryFn<T>,
  deps: unknown[] = [],
  options: UseSupabaseQueryOptions = {},
): UseSupabaseQueryReturn<T> {
  const { enabled = true, retries = 3 } = options;
  const [data, setData] = useState<T | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const queryFnRef = useRef(queryFn);
  queryFnRef.current = queryFn;

  const fetchData = useCallback(async () => {
    if (!supabase) {
      setIsLoading(false);
      return;
    }

    const client = requireSupabase();
    setIsLoading(true);
    setError(null);

    let lastError: unknown;
    for (let attempt = 0; attempt <= retries; attempt++) {
      try {
        const result = await queryFnRef.current(client);
        if (result.error) throw result.error;
        setData(result.data);
        setIsLoading(false);
        return;
      } catch (err: unknown) {
        lastError = err;
        const isRetryable =
          err instanceof TypeError ||
          (err && typeof err === 'object' && 'status' in err &&
            ((err as { status: number }).status === 429 || (err as { status: number }).status >= 500));

        if (attempt < retries && isRetryable) {
          await new Promise((r) => setTimeout(r, 500 * Math.pow(2, attempt)));
          continue;
        }

        const message = err instanceof Error ? err.message : 'Gagal memuat data';
        setError(message);
        setIsLoading(false);
        if (options.onError) options.onError(err);
        return;
      }
    }
  }, [retries, options.onError]);

  const fetchRef = useRef(fetchData);
  fetchRef.current = fetchData;

  useEffect(() => {
    if (enabled) fetchData();
    else setIsLoading(false);
  }, [enabled, fetchData, ...deps]);

  return { data, isLoading, error, refetch: fetchData };
}
