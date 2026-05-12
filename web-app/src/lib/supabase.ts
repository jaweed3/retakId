import { createClient } from '@supabase/supabase-js';
import type { Database } from '../types/laporan';

const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;
const supabaseAnonKey = import.meta.env.VITE_SUPABASE_ANON_KEY;

let _supabase: ReturnType<typeof createClient<Database>> | null = null;

function getClient() {
  if (_supabase) return _supabase;

  if (!supabaseUrl || !supabaseAnonKey) {
    if (import.meta.env.DEV) {
      // eslint-disable-next-line no-console
      console.warn(
        '⚠️  Supabase credentials tidak ditemukan. ' +
        'Copy .env.example ke .env.local dan isi VITE_SUPABASE_URL dan VITE_SUPABASE_ANON_KEY.\n' +
        'Dashboard akan menampilkan state kosong sampai credentials tersedia.'
      );
    }
    return null;
  }

  _supabase = createClient<Database>(supabaseUrl, supabaseAnonKey);
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
