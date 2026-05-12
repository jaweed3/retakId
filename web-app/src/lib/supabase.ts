import { createClient } from '@supabase/supabase-js';
import type { Database } from '../types/laporan';

const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;
const supabaseAnonKey = import.meta.env.VITE_SUPABASE_ANON_KEY;

if (!supabaseUrl || !supabaseAnonKey) {
  throw new Error(
    'Supabase credentials tidak ditemukan. ' +
    'Copy .env.example ke .env.local dan isi VITE_SUPABASE_URL dan VITE_SUPABASE_ANON_KEY.'
  );
}

export const supabase = createClient<Database>(supabaseUrl, supabaseAnonKey);
