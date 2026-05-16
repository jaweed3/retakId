import { createClient } from '@supabase/supabase-js';
import type { Database } from '../types/laporan';

const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;
const supabaseAnonKey = import.meta.env.VITE_SUPABASE_ANON_KEY;
const REQUEST_TIMEOUT = 30000;
const MAX_RETRIES = 3;

export class SupabaseConnectionError extends Error {
  constructor(message: string, public readonly cause?: unknown) {
    super(message);
    this.name = 'SupabaseConnectionError';
  }
}

export class SupabaseTimeoutError extends SupabaseConnectionError {
  constructor(timeout: number) {
    super(`Request timed out after ${timeout}ms`);
    this.name = 'SupabaseTimeoutError';
  }
}

function delay(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function fetchWithTimeout(
  input: RequestInfo | URL,
  init?: RequestInit,
  timeout = REQUEST_TIMEOUT,
): Promise<Response> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeout);

  try {
    const response = await fetch(input, { ...init, signal: controller.signal });
    return response;
  } catch (err: unknown) {
    if (err instanceof DOMException && err.name === 'AbortError') {
      throw new SupabaseTimeoutError(timeout);
    }
    throw new SupabaseConnectionError(
      err instanceof Error ? err.message : 'Network request failed',
      err,
    );
  } finally {
    clearTimeout(timer);
  }
}

async function retry<T>(
  fn: () => Promise<T>,
  retries = MAX_RETRIES,
  backoffMs = 500,
): Promise<T> {
  let lastError: unknown;
  for (let attempt = 0; attempt <= retries; attempt++) {
    try {
      return await fn();
    } catch (err: unknown) {
      lastError = err;
      if (attempt < retries && isRetryable(err)) {
        await delay(backoffMs * Math.pow(2, attempt));
      }
    }
  }
  throw lastError;
}

function isRetryable(err: unknown): boolean {
  if (err instanceof SupabaseTimeoutError) return true;
  if (err instanceof SupabaseConnectionError) return true;
  if (err instanceof TypeError && err.message === 'Failed to fetch') return true;
  if (err && typeof err === 'object' && 'status' in err) {
    const status = (err as { status: number }).status;
    return status === 429 || status >= 500;
  }
  return false;
}

const customFetch = (input: RequestInfo | URL, init?: RequestInit) =>
  fetchWithTimeout(input, init);

let _supabase: ReturnType<typeof createClient<Database>> | null = null;

function getClient() {
  if (_supabase) return _supabase;

  if (!supabaseUrl || !supabaseAnonKey) {
    if (import.meta.env.DEV) {
      console.warn(
        '⚠️  Supabase credentials tidak ditemukan. ' +
        'Copy .env.example ke .env.local dan isi VITE_SUPABASE_URL dan VITE_SUPABASE_ANON_KEY.\n' +
        'Dashboard akan menampilkan state kosong sampai credentials tersedia.',
      );
    }
    return null;
  }

  _supabase = createClient<Database>(supabaseUrl, supabaseAnonKey, {
    global: { fetch: customFetch },
    auth: { autoRefreshToken: true, persistSession: true },
    realtime: { timeout: REQUEST_TIMEOUT },
  });
  return _supabase;
}

export const supabase = getClient();

export function requireSupabase(): NonNullable<typeof _supabase> {
  const client = getClient();
  if (!client) {
    throw new Error('Supabase belum dikonfigurasi.');
  }
  return client;
}

export async function queryWithRetry<T>(
  queryFn: () => Promise<{ data: T | null; error: unknown }>,
  options?: { retries?: number; onError?: (err: unknown) => void },
): Promise<T> {
  const result = await retry(
    async () => {
      const res = await queryFn();
      if (res.error) {
        if (isRetryable(res.error)) {
          throw res.error;
        }
        if (options?.onError) options.onError(res.error);
        throw res.error;
      }
      return res;
    },
    options?.retries ?? MAX_RETRIES,
  );
  return result.data as T;
}
