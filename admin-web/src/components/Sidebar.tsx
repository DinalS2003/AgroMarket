import React from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import {
  LayoutDashboard,
  Users,
  Store,
  ShoppingBag,
  DollarSign,
  RotateCcw,
  AlertTriangle,
  MessageSquareWarning,
  PieChart,
  Settings,
  ShieldCheck,
  LogOut,
  Sprout
} from 'lucide-react';
import { supabase } from '../lib/supabase';

const navItems = [
  { to: '/', label: 'Overview', icon: LayoutDashboard },
  { to: '/users', label: 'Users & Farmers', icon: Users },
  { to: '/listings', label: 'Harvest Listings', icon: Store },
  { to: '/orders', label: 'Orders Registry', icon: ShoppingBag },
  { to: '/payouts', label: 'Farmer Payouts', icon: DollarSign },
  { to: '/refunds', label: 'Refunds Required', icon: RotateCcw },
  { to: '/disputes', label: 'Mediation & Disputes', icon: AlertTriangle },
  { to: '/flagged-messages', label: 'Flagged Messages', icon: MessageSquareWarning },
  { to: '/commission', label: 'Commission Report', icon: PieChart },
  { to: '/settings', label: 'Marketplace Settings', icon: Settings },
  { to: '/audit-log', label: 'Audit Trail', icon: ShieldCheck },
];

export const Sidebar: React.FC = () => {
  const navigate = useNavigate();

  const handleLogout = async () => {
    await supabase.auth.signOut();
    navigate('/login');
  };

  return (
    <aside className="w-64 bg-slate-900 text-slate-200 flex flex-col h-screen fixed left-0 top-0 z-20 select-none">
      {/* Brand Header */}
      <div className="h-16 flex items-center px-6 border-b border-slate-800 gap-3">
        <div className="w-8 h-8 rounded-lg bg-green-600 flex items-center justify-center text-white">
          <Sprout className="w-5 h-5" />
        </div>
        <div>
          <h1 className="font-bold text-white text-base leading-tight">AgroMarket</h1>
          <span className="text-xs text-green-400 font-medium tracking-wide">ADMIN PORTAL</span>
        </div>
      </div>

      {/* Nav Menu */}
      <nav className="flex-1 overflow-y-auto py-4 px-3 space-y-1">
        {navItems.map((item) => {
          const Icon = item.icon;
          return (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) =>
                `flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-green-700 text-white shadow-sm'
                    : 'text-slate-300 hover:bg-slate-800 hover:text-white'
                }`
              }
            >
              <Icon className="w-4 h-4 flex-shrink-0" />
              <span>{item.label}</span>
            </NavLink>
          );
        })}
      </nav>

      {/* Footer / Logout */}
      <div className="p-4 border-t border-slate-800">
        <button
          onClick={handleLogout}
          className="w-full flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium text-red-400 hover:bg-red-500/10 hover:text-red-300 transition-colors"
        >
          <LogOut className="w-4 h-4" />
          <span>Sign Out</span>
        </button>
      </div>
    </aside>
  );
};
