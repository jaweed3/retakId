import { createContext, useContext, useEffect, useState, useCallback, type ReactNode } from 'react';
import type { User, Session } from '@supabase/supabase-js';
import { supabase, requireSupabase } from '../lib/supabase';

interface AuthState {
  user: User | null;
  session: Session | null;
  isLoading: boolean;
  isAdmin: boolean;
  adminError: string | null;
  signIn: (email: string, password: string) => Promise<{ error: string | null }>;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthState>({
  user: null,
  session: null,
  isLoading: true,
  isAdmin: false,
  adminError: null,
  signIn: async () => ({ error: 'AuthContext not initialized' }),
  signOut: async () => {},
});

async function checkIsAdmin(userId: string): Promise<boolean> {
  if (!supabase) return false;
  const client = requireSupabase();
  try {
    const { data, error } = await client
      .from('admin_users')
      .select('user_id')
      .eq('user_id', userId)
      .maybeSingle();

    if (error) {
      // Tabel admin_users belum ada — allow login (first-time setup)
      if (error.code === '42P01' || error.message.includes('does not exist')) {
        console.warn('⚠️  Tabel admin_users belum ada. Buat tabel untuk mengamankan akses admin.');
        return true; // Allow — no admin table means first-time setup
      }
      return false;
    }
    if (data) return true;

    // No admin entry — check if table is empty (first-time setup)
    const { count, error: countError } = await client
      .from('admin_users')
      .select('*', { count: 'exact', head: true });
    if (!countError && count === 0) {
      // First admin — auto-register (best effort, RLS might block)
      try {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        await (client as any).from('admin_users').insert({ user_id: userId, nama: 'Admin', role: 'admin' });
      } catch { /* RLS might block, admin can be added via SQL */ }
      return true;
    }
    return false;
  } catch {
    // Tabel mungkin belum ada — allow login
    return true;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [session, setSession] = useState<Session | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isAdmin, setIsAdmin] = useState(false);

  // Check admin status when user changes
  useEffect(() => {
    if (!user) {
      setIsAdmin(false);
      return;
    }
    let cancelled = false;
    checkIsAdmin(user.id).then((admin) => {
      if (!cancelled) setIsAdmin(admin);
    });
    return () => { cancelled = true; };
  }, [user]);

  // Auth session handling
  useEffect(() => {
    if (!supabase) {
      setIsLoading(false);
      return;
    }

    supabase.auth.getSession().then(({ data: { session } }) => {
      setSession(session);
      setUser(session?.user ?? null);
      setIsLoading(false);
    });

    const { data: { subscription } } = supabase.auth.onAuthStateChange((_event, session) => {
      setSession(session);
      setUser(session?.user ?? null);
    });

    return () => subscription.unsubscribe();
  }, []);

  const signIn = useCallback(async (email: string, password: string): Promise<{ error: string | null }> => {
    if (!supabase) return { error: 'Supabase belum dikonfigurasi.' };

    const { error } = await supabase.auth.signInWithPassword({ email, password });
    if (error) {
      if (error.message.includes('Invalid login')) return { error: 'Email atau password salah.' };
      return { error: error.message };
    }

    // Cek admin setelah login
    const currentUser = (await supabase.auth.getSession()).data.session?.user;
    if (currentUser) {
      const admin = await checkIsAdmin(currentUser.id);
      setIsAdmin(admin);
      if (!admin) {
        // Not an admin and not first-time setup
        await supabase.auth.signOut();
        setUser(null);
        setSession(null);
        return { error: 'Akun Anda tidak memiliki akses admin. Hubungi administrator.' };
      }
      // Admin atau first-time setup berhasil
      setIsAdmin(true);
    }

    return { error: null };
  }, []);

  const signOut = async () => {
    if (!supabase) return;
    await supabase.auth.signOut();
    setUser(null);
    setSession(null);
    setIsAdmin(false);
  };

  return (
    <AuthContext.Provider value={{ user, session, isLoading, isAdmin, adminError: null, signIn, signOut }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthState {
  return useContext(AuthContext);
}
