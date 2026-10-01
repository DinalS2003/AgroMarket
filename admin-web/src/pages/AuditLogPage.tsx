import React, { useEffect, useState } from 'react';
import { supabase } from '../lib/supabase';
import { ShieldCheck } from 'lucide-react';

export const AuditLogPage: React.FC = () => {
  const [logs, setLogs] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  const loadAuditLogs = async () => {
    setLoading(true);
    const { data } = await supabase
      .from('audit_log')
      .select(`
        *,
        admin:profiles!audit_log_admin_id_fkey(full_name)
      `)
      .order('created_at', { ascending: false })
      .limit(100);

    setLogs(data || []);
    setLoading(false);
  };

  useEffect(() => {
    loadAuditLogs();
  }, []);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Security & Governance Audit Trail</h1>
        <p className="text-sm text-slate-500">Immutable ledger of administrative interventions, dispute adjudications, payouts, and suspensions</p>
      </div>

      <div className="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-sm">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                <th className="py-3 px-4">Timestamp</th>
                <th className="py-3 px-4">Admin Officer</th>
                <th className="py-3 px-4">Action</th>
                <th className="py-3 px-4">Target Entity</th>
                <th className="py-3 px-4">Details JSON</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200">
              {loading ? (
                <tr>
                  <td colSpan={5} className="py-8 text-center text-slate-500">
                    Loading audit trail...
                  </td>
                </tr>
              ) : logs.length === 0 ? (
                <tr>
                  <td colSpan={5} className="py-8 text-center text-slate-500">
                    No administrative actions recorded in the audit ledger.
                  </td>
                </tr>
              ) : (
                logs.map((log) => (
                  <tr key={log.id} className="hover:bg-slate-50 transition-colors">
                    <td className="py-3 px-4 text-xs font-mono text-slate-600">
                      {log.created_at ? log.created_at.take(19).replace('T', ' ') : '—'}
                    </td>
                    <td className="py-3 px-4 font-semibold text-slate-900">
                      {log.admin?.full_name || 'System Admin'}
                    </td>
                    <td className="py-3 px-4">
                      <span className="px-2 py-0.5 rounded-full text-xs font-bold uppercase tracking-wider bg-slate-100 text-slate-800 font-mono">
                        {log.action}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-xs font-mono text-slate-700">
                      {log.entity} #{log.entity_id?.substring(0, 8)}
                    </td>
                    <td className="py-3 px-4 text-xs font-mono text-slate-600 bg-slate-50/50 max-w-sm truncate">
                      {JSON.stringify(log.details)}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
