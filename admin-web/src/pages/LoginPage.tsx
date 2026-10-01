import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { supabase, checkIsAdmin } from '../lib/supabase';
import { Sprout, Lock, Mail, AlertCircle } from 'lucide-react';

export const LoginPage: React.FC = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      const { data, error: authError } = await supabase.auth.signInWithPassword({
        email,
        password,
      });

      if (authError || !data.user) {
        throw new Error(authError?.message || 'Invalid credentials');
      }

      const isAdmin = await checkIsAdmin(data.user.id);
      if (!isAdmin) {
        await supabase.auth.signOut();
        throw new Error('Access denied: User is not an authorized administrator.');
      }

      navigate('/');
    } catch (err: any) {
      setError(err.message || 'Login failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-900 flex items-center justify-center p-4">
      <div className="w-full max-w-md bg-slate-800 rounded-2xl shadow-2xl p-8 border border-slate-700">
        <div className="flex flex-col items-center mb-8">
          <div className="w-12 h-12 bg-green-600 rounded-xl flex items-center justify-center text-white mb-3 shadow-lg shadow-green-600/30">
            <Sprout className="w-7 h-7" />
          </div>
          <h1 className="text-2xl font-bold text-white tracking-tight">AgroMarket Portal</h1>
          <p className="text-slate-400 text-sm mt-1">Platform Administration & Escrow Oversight</p>
        </div>

        {error && (
          <div className="mb-6 bg-red-500/10 border border-red-500/30 rounded-lg p-3 flex items-start gap-3 text-red-400 text-sm">
            <AlertCircle className="w-5 h-5 flex-shrink-0 mt-0.5" />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleLogin} className="space-y-5">
          <div>
            <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">
              Administrator Email
            </label>
            <div className="relative">
              <Mail className="w-5 h-5 text-slate-400 absolute left-3 top-3" />
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="admin@agromarket.lk"
                className="w-full bg-slate-900 border border-slate-700 rounded-lg pl-10 pr-4 py-2.5 text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-green-500 focus:border-transparent text-sm"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">
              Password
            </label>
            <div className="relative">
              <Lock className="w-5 h-5 text-slate-400 absolute left-3 top-3" />
              <input
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••••••"
                className="w-full bg-slate-900 border border-slate-700 rounded-lg pl-10 pr-4 py-2.5 text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-green-500 focus:border-transparent text-sm"
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-green-600 hover:bg-green-500 text-white font-semibold py-2.5 rounded-lg transition-colors shadow-lg shadow-green-600/30 disabled:opacity-50 text-sm mt-2"
          >
            {loading ? 'Authenticating...' : 'Sign In to Dashboard'}
          </button>
        </form>

        <div className="mt-8 pt-6 border-t border-slate-700/50 text-center">
          <p className="text-xs text-slate-500">
            Internal system for authorized officers of AgroMarket Sri Lanka.
          </p>
        </div>
      </div>
    </div>
  );
};
