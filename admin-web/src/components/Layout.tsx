import React, { useEffect, useState } from 'react';
import { Outlet, useNavigate } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { supabase, checkIsAdmin } from '../lib/supabase';

export const Layout: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [isAdmin, setIsAdmin] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    async function checkAuth() {
      const { data: { session } } = await supabase.auth.getSession();
      if (!session) {
        navigate('/login');
        return;
      }

      const adminOk = await checkIsAdmin(session.user.id);
      if (!adminOk) {
        alert('Access denied: You are not an authorized administrator.');
        await supabase.auth.signOut();
        navigate('/login');
        return;
      }

      setIsAdmin(true);
      setLoading(false);
    }

    checkAuth();

    const { data: { subscription } } = supabase.auth.onAuthStateChange((event, session) => {
      if (event === 'SIGNED_OUT' || (!session && event !== 'INITIAL_SESSION')) {
        setIsAdmin(false);
        navigate('/login');
      }
    });

    return () => {
      subscription.unsubscribe();
    };
  }, [navigate]);

  if (loading) {
    return (
      <div className="h-screen flex items-center justify-center bg-gray-50 text-gray-600">
        <div className="flex flex-col items-center gap-3">
          <div className="w-10 h-10 border-4 border-green-600 border-t-transparent rounded-full animate-spin"></div>
          <span className="text-sm font-medium">Verifying administrator authorization...</span>
        </div>
      </div>
    );
  }

  if (!isAdmin) return null;

  return (
    <div className="min-h-screen bg-slate-50 flex">
      <Sidebar />
      <main className="flex-1 ml-64 p-8 min-h-screen">
        <Outlet />
      </main>
    </div>
  );
};
