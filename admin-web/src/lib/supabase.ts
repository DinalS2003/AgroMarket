import { createClient } from '@supabase/supabase-js';

const supabaseUrl = import.meta.env.VITE_SUPABASE_URL || 'http://127.0.0.1:54321';
const supabaseAnonKey = import.meta.env.VITE_SUPABASE_ANON_KEY || 'dummy-anon-key';

export const supabase = createClient(supabaseUrl, supabaseAnonKey, {
  auth: {
    persistSession: true,
    autoRefreshToken: true,
    detectSessionInUrl: true,
  },
});

export function isJwtExpiredError(error: any): boolean {
  if (!error) return false;
  const msg = typeof error === 'string' ? error : (error.message || error.error_description || '');
  return (
    msg.toLowerCase().includes('jwt expired') ||
    msg.toLowerCase().includes('token expired') ||
    error.code === 'PGRST301' ||
    error.status === 401
  );
}

export async function handleAuthExpiration(error: any) {
  if (isJwtExpiredError(error)) {
    try {
      await supabase.auth.signOut();
    } catch (_) {
      // Ignore signOut errors on expired tokens
    }
    window.location.href = '/login';
    return true;
  }
  return false;
}

export async function checkIsAdmin(userId: string): Promise<boolean> {
  const { data, error } = await supabase
    .from('admin_users')
    .select('user_id')
    .eq('user_id', userId)
    .single();

  return !error && !!data;
}
