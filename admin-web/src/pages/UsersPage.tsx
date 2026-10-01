import React, { useEffect, useState } from 'react';
import { supabase } from '../lib/supabase';
import { Search, ShieldAlert, ShieldCheck, Eye, X } from 'lucide-react';

export const UsersPage: React.FC = () => {
  const [users, setUsers] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [selectedUser, setSelectedUser] = useState<any | null>(null);
  const [suspendModalOpen, setSuspendModalOpen] = useState(false);
  const [suspendReason, setSuspendReason] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

  const loadUsers = async () => {
    setLoading(true);
    // Fetch profiles, private details, farmers, farmer_private
    const { data: profiles } = await supabase
      .from('profiles')
      .select(`
        id,
        full_name,
        district_id,
        city_id,
        is_suspended,
        suspended_reason,
        created_at,
        districts(name),
        cities(name),
        user_private(nic, phone_e164),
        farmers(
          cultivation_district_id,
          cultivation_city_id,
          main_crops,
          land_size,
          land_unit,
          default_pickup_landmark
        ),
        farmer_private(
          cultivation_address,
          bank_name,
          bank_branch,
          account_holder_name,
          account_number
        ),
        farmer_stats(*)
      `);

    setUsers(profiles || []);
    setLoading(false);
  };

  useEffect(() => {
    loadUsers();
  }, []);

  const filteredUsers = users.filter((u) => {
    const q = search.toLowerCase();
    const name = u.full_name?.toLowerCase() || '';
    const phone = u.user_private?.phone_e164 || '';
    const nic = u.user_private?.nic || '';
    return name.includes(q) || phone.includes(q) || nic.toLowerCase().includes(q);
  });

  const handleSuspend = async () => {
    if (!suspendReason.trim() || !selectedUser) return;
    setActionLoading(true);
    try {
      const { error } = await supabase.rpc('admin_suspend_user', {
        p_user_id: selectedUser.id,
        p_reason: suspendReason.trim(),
      });
      if (error) throw error;
      setSuspendModalOpen(false);
      setSuspendReason('');
      await loadUsers();
      if (selectedUser) {
        setSelectedUser({ ...selectedUser, is_suspended: true, suspended_reason: suspendReason.trim() });
      }
    } catch (err: any) {
      alert(`Error suspending user: ${err.message}`);
    } finally {
      setActionLoading(false);
    }
  };

  const handleUnsuspend = async (userId: string) => {
    if (!confirm('Reactivate this user account?')) return;
    setActionLoading(true);
    try {
      const { error } = await supabase.rpc('admin_unsuspend_user', {
        p_user_id: userId,
      });
      if (error) throw error;
      await loadUsers();
      if (selectedUser) {
        setSelectedUser({ ...selectedUser, is_suspended: false, suspended_reason: null });
      }
    } catch (err: any) {
      alert(`Error reactivating user: ${err.message}`);
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">User & Farmer Registry</h1>
          <p className="text-sm text-slate-500">Authorized oversight of phone numbers, NICs, farm addresses, and bank accounts</p>
        </div>

        <div className="relative w-full sm:w-80">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 top-3" />
          <input
            type="text"
            placeholder="Search by Name, Phone, or NIC..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full bg-white border border-slate-300 rounded-lg pl-9 pr-4 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-green-600 focus:border-transparent"
          />
        </div>
      </div>

      {/* Users Table */}
      <div className="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-sm">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                <th className="py-3 px-4">User</th>
                <th className="py-3 px-4">Mobile & NIC</th>
                <th className="py-3 px-4">Location</th>
                <th className="py-3 px-4">Roles</th>
                <th className="py-3 px-4">Status</th>
                <th className="py-3 px-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200">
              {loading ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-slate-500">
                    Loading registered accounts...
                  </td>
                </tr>
              ) : filteredUsers.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-slate-500">
                    No accounts matching your search query.
                  </td>
                </tr>
              ) : (
                filteredUsers.map((u) => {
                  const isFarmer = !!u.farmers;
                  return (
                    <tr key={u.id} className="hover:bg-slate-50 transition-colors">
                      <td className="py-3 px-4">
                        <div className="font-semibold text-slate-900">{u.full_name}</div>
                        <div className="text-xs text-slate-400">ID: {u.id.substring(0, 8)}...</div>
                      </td>
                      <td className="py-3 px-4">
                        <div className="text-slate-800 font-mono text-xs">{u.user_private?.phone_e164 || '—'}</div>
                        <div className="text-slate-500 text-xs font-mono">{u.user_private?.nic || '—'}</div>
                      </td>
                      <td className="py-3 px-4 text-slate-600 text-xs">
                        {u.cities?.name}, {u.districts?.name}
                      </td>
                      <td className="py-3 px-4">
                        <div className="flex gap-1.5 flex-wrap">
                          <span className="px-2 py-0.5 rounded text-xs font-medium bg-blue-100 text-blue-800">
                            Buyer
                          </span>
                          {isFarmer && (
                            <span className="px-2 py-0.5 rounded text-xs font-medium bg-green-100 text-green-800">
                              Farmer
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="py-3 px-4">
                        {u.is_suspended ? (
                          <span className="px-2 py-1 rounded-full text-xs font-bold bg-red-100 text-red-700">
                            Suspended
                          </span>
                        ) : (
                          <span className="px-2 py-1 rounded-full text-xs font-medium bg-emerald-100 text-emerald-700">
                            Active
                          </span>
                        )}
                      </td>
                      <td className="py-3 px-4 text-right">
                        <button
                          onClick={() => setSelectedUser(u)}
                          className="px-2.5 py-1.5 text-xs font-medium text-slate-600 hover:text-green-700 hover:bg-green-50 rounded transition-colors inline-flex items-center gap-1"
                        >
                          <Eye className="w-3.5 h-3.5" />
                          <span>View Detail</span>
                        </button>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* User Detail Slide-over / Modal */}
      {selectedUser && (
        <div className="fixed inset-0 z-50 bg-black/50 flex justify-end">
          <div className="bg-white w-full max-w-xl h-full shadow-2xl overflow-y-auto p-6 space-y-6">
            <div className="flex items-center justify-between border-b pb-4">
              <div>
                <h2 className="text-xl font-bold text-slate-900">{selectedUser.full_name}</h2>
                <span className="text-xs text-slate-400">Account ID: {selectedUser.id}</span>
              </div>
              <button
                onClick={() => setSelectedUser(null)}
                className="p-1.5 text-slate-400 hover:text-slate-600 rounded-lg hover:bg-slate-100"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Account Status / Suspension */}
            <div className={`p-4 rounded-xl border ${selectedUser.is_suspended ? 'bg-red-50 border-red-200' : 'bg-green-50 border-green-200'}`}>
              <div className="flex items-center justify-between">
                <div>
                  <div className="font-bold text-sm text-slate-800">
                    Status: {selectedUser.is_suspended ? 'Suspended' : 'Good Standing (Active)'}
                  </div>
                  {selectedUser.is_suspended && (
                    <div className="text-xs text-red-600 mt-1">Reason: {selectedUser.suspended_reason}</div>
                  )}
                </div>
                {selectedUser.is_suspended ? (
                  <button
                    disabled={actionLoading}
                    onClick={() => handleUnsuspend(selectedUser.id)}
                    className="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white rounded text-xs font-semibold"
                  >
                    Unsuspend Account
                  </button>
                ) : (
                  <button
                    onClick={() => setSuspendModalOpen(true)}
                    className="px-3 py-1.5 bg-red-600 hover:bg-red-500 text-white rounded text-xs font-semibold"
                  >
                    Suspend User
                  </button>
                )}
              </div>
            </div>

            {/* Identity & Contact Details */}
            <div className="space-y-3">
              <h3 className="text-sm font-bold text-slate-800 uppercase tracking-wider">Confidential Identity</h3>
              <div className="grid grid-cols-2 gap-3 text-sm">
                <div className="p-3 bg-slate-50 rounded-lg border border-slate-200">
                  <div className="text-xs text-slate-500">Phone (E.164)</div>
                  <div className="font-mono font-bold text-slate-800">{selectedUser.user_private?.phone_e164 || '—'}</div>
                </div>
                <div className="p-3 bg-slate-50 rounded-lg border border-slate-200">
                  <div className="text-xs text-slate-500">NIC Number</div>
                  <div className="font-mono font-bold text-slate-800">{selectedUser.user_private?.nic || '—'}</div>
                </div>
                <div className="p-3 bg-slate-50 rounded-lg border border-slate-200">
                  <div className="text-xs text-slate-500">Home District</div>
                  <div className="font-medium text-slate-800">{selectedUser.districts?.name || '—'}</div>
                </div>
                <div className="p-3 bg-slate-50 rounded-lg border border-slate-200">
                  <div className="text-xs text-slate-500">Home Town / DS</div>
                  <div className="font-medium text-slate-800">{selectedUser.cities?.name || '—'}</div>
                </div>
              </div>
            </div>

            {/* Farmer Profile & Private Bank Data */}
            {selectedUser.farmers && (
              <div className="space-y-3">
                <h3 className="text-sm font-bold text-slate-800 uppercase tracking-wider">Farmer Profile & Bank Payout Details</h3>
                <div className="space-y-2 text-sm bg-slate-50 p-4 rounded-xl border border-slate-200">
                  <div>
                    <span className="text-xs text-slate-500 block">Cultivation Address (Private):</span>
                    <span className="font-medium text-slate-800">{selectedUser.farmer_private?.cultivation_address || '—'}</span>
                  </div>
                  <div>
                    <span className="text-xs text-slate-500 block">Main Crops:</span>
                    <span className="font-medium text-slate-800">{selectedUser.farmers.main_crops?.join(', ') || '—'}</span>
                  </div>
                  <div className="pt-2 border-t border-slate-200 grid grid-cols-2 gap-2">
                    <div>
                      <span className="text-xs text-slate-500 block">Bank Name:</span>
                      <span className="font-bold text-slate-800">{selectedUser.farmer_private?.bank_name || '—'}</span>
                    </div>
                    <div>
                      <span className="text-xs text-slate-500 block">Branch:</span>
                      <span className="font-bold text-slate-800">{selectedUser.farmer_private?.bank_branch || '—'}</span>
                    </div>
                    <div>
                      <span className="text-xs text-slate-500 block">Account Holder:</span>
                      <span className="font-bold text-slate-800">{selectedUser.farmer_private?.account_holder_name || '—'}</span>
                    </div>
                    <div>
                      <span className="text-xs text-slate-500 block">Account Number:</span>
                      <span className="font-mono font-bold text-slate-800">{selectedUser.farmer_private?.account_number || '—'}</span>
                    </div>
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Suspend Confirmation Modal */}
      {suspendModalOpen && (
        <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-2xl max-w-md w-full p-6 space-y-4">
            <div className="flex items-center gap-3 text-red-600">
              <ShieldAlert className="w-6 h-6" />
              <h3 className="text-lg font-bold text-slate-900">Suspend User Account</h3>
            </div>
            <p className="text-sm text-slate-600">
              Suspended users cannot place orders, list crops, or communicate in chats. An official reason is required for the audit log.
            </p>
            <textarea
              required
              rows={3}
              placeholder="Enter official suspension reason..."
              value={suspendReason}
              onChange={(e) => setSuspendReason(e.target.value)}
              className="w-full border border-slate-300 rounded-lg p-2.5 text-sm focus:ring-2 focus:ring-red-500 focus:outline-none"
            />
            <div className="flex justify-end gap-3 pt-2">
              <button
                onClick={() => setSuspendModalOpen(false)}
                className="px-4 py-2 border border-slate-300 rounded-lg text-sm text-slate-600 hover:bg-slate-50"
              >
                Cancel
              </button>
              <button
                disabled={actionLoading || !suspendReason.trim()}
                onClick={handleSuspend}
                className="px-4 py-2 bg-red-600 hover:bg-red-500 text-white rounded-lg text-sm font-semibold disabled:opacity-50"
              >
                {actionLoading ? 'Suspending...' : 'Confirm Suspension'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
