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
      // Tabel admin_users belum ada — log warning, deny access
      if (error.code === '42P01' || error.message.includes('does not exist')) {
        console.warn(
          '⚠️  Tabel admin_users belum ada di Supabase. ' +
          'Jalankan SQL berikut di Supabase SQL Editor:\n' +
          'CREATE TABLE admin_users (user_id UUID PRIMARY KEY REFERENCES auth.users(id), nama TEXT NOT NULL, created_at TIMESTAMPTZ DEFAULT now());'
        );
      }
      return false;
    }
    return !!data;
  } catch {
    return false;
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
        // Logout user non-admin
        await supabase.auth.signOut();
        setUser(null);
        setSession(null);
        return { error: 'Akun Anda tidak memiliki akses admin. Hubungi administrator.' };
      }
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
