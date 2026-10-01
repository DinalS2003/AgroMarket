import React, { useEffect, useState } from 'react';
import { supabase } from '../lib/supabase';
import { Check, ShieldAlert } from 'lucide-react';

export const FlaggedMessagesPage: React.FC = () => {
  const [flagged, setFlagged] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [filterReviewed, setFilterReviewed] = useState(false);

  const loadFlagged = async () => {
    setLoading(true);
    const { data } = await supabase
      .from('flagged_messages')
      .select(`
        *,
        sender:profiles!flagged_messages_sender_id_fkey(full_name),
        order:orders(order_number)
      `)
      .order('created_at', { ascending: false });

    setFlagged(data || []);
    setLoading(false);
  };

  useEffect(() => {
    loadFlagged();
  }, []);

  const markReviewed = async (id: string) => {
    await supabase.from('flagged_messages').update({ reviewed: true }).eq('id', id);
    loadFlagged();
  };

  const displayed = flagged.filter((f) => (filterReviewed ? f.reviewed : !f.reviewed));

  // Compute 24-hour violation count per sender
  const now = Date.now();
  const ONE_DAY_MS = 24 * 60 * 60 * 1000;
  const user24hCounts: Record<string, number> = {};
  flagged.forEach((f) => {
    const sender = f.sender?.full_name || f.sender_id;
    const createdAtMs = f.created_at ? new Date(f.created_at).getTime() : 0;
    if (now - createdAtMs <= ONE_DAY_MS) {
      user24hCounts[sender] = (user24hCounts[sender] || 0) + 1;
    }
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Flagged Chat Interceptions</h1>
          <p className="text-sm text-slate-500">Messages blocked by server-side regex filters attempting to leak phones, social handles, or addresses</p>
        </div>

        <div className="flex items-center gap-2 bg-white border border-slate-300 rounded-lg p-1 text-xs font-semibold">
          <button
            onClick={() => setFilterReviewed(false)}
            className={`px-3 py-1.5 rounded-md ${!filterReviewed ? 'bg-red-600 text-white' : 'text-slate-600'}`}
          >
            Pending Review ({flagged.filter((f) => !f.reviewed).length})
          </button>
          <button
            onClick={() => setFilterReviewed(true)}
            className={`px-3 py-1.5 rounded-md ${filterReviewed ? 'bg-slate-800 text-white' : 'text-slate-600'}`}
          >
            Reviewed ({flagged.filter((f) => f.reviewed).length})
          </button>
        </div>
      </div>

      <div className="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-sm">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                <th className="py-3 px-4">Offender & Violations</th>
                <th className="py-3 px-4">Order #</th>
                <th className="py-3 px-4">Blocked Message Body</th>
                <th className="py-3 px-4">Violation Triggers</th>
                <th className="py-3 px-4">Intercepted At</th>
                <th className="py-3 px-4 text-right">Review</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200">
              {loading ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-slate-500">
                    Loading flagged communications...
                  </td>
                </tr>
              ) : displayed.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-slate-500">
                    No flagged messages in this queue.
                  </td>
                </tr>
              ) : (
                displayed.map((f) => {
                  const senderName = f.sender?.full_name || 'User';
                  const violations24h = user24hCounts[senderName] || 1;

                  return (
                    <tr key={f.id} className="hover:bg-slate-50 transition-colors">
                      <td className="py-3 px-4">
                        <div className="font-bold text-slate-900">{senderName}</div>
                        <div className="text-xs text-red-600 font-semibold flex items-center gap-1 mt-0.5">
                          <ShieldAlert className="w-3.5 h-3.5" />
                          <span>{violations24h} violation{violations24h > 1 ? 's' : ''} in 24h</span>
                        </div>
                      </td>
                      <td className="py-3 px-4 font-mono font-semibold text-slate-700">
                        {f.order?.order_number || '—'}
                      </td>
                      <td className="py-3 px-4 text-xs font-mono bg-slate-50/60 text-slate-800 max-w-md break-words rounded p-2">
                        {f.original_body}
                      </td>
                      <td className="py-3 px-4">
                        <div className="flex flex-wrap gap-1">
                          {f.reasons?.map((r: string, idx: number) => (
                            <span key={idx} className="px-2 py-0.5 bg-red-100 text-red-700 text-xs rounded-full font-medium">
                              {r}
                            </span>
                          ))}
                        </div>
                      </td>
                      <td className="py-3 px-4 text-xs text-slate-500">
                        {f.created_at ? f.created_at.substring(0, 16).replace('T', ' ') : '—'}
                      </td>
                      <td className="py-3 px-4 text-right">
                        {!f.reviewed && (
                          <button
                            onClick={() => markReviewed(f.id)}
                            className="px-2.5 py-1.5 bg-slate-800 hover:bg-slate-700 text-white rounded text-xs font-semibold inline-flex items-center gap-1"
                          >
                            <Check className="w-3.5 h-3.5" />
                            <span>Mark Reviewed</span>
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
