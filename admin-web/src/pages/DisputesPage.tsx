import React, { useEffect, useState } from 'react';
import { supabase } from '../lib/supabase';
import { AlertTriangle, Eye, X, MessageSquare } from 'lucide-react';

export const DisputesPage: React.FC = () => {
  const [disputes, setDisputes] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedDispute, setSelectedDispute] = useState<any | null>(null);
  const [chatMessages, setChatMessages] = useState<any[]>([]);
  const [loadingChat, setLoadingChat] = useState(false);

  // Resolution Form
  const [resolution, setResolution] = useState<'full_refund' | 'partial_refund' | 'release_to_farmer'>('full_refund');
  const [partialRefundAmt, setPartialRefundAmt] = useState('');
  const [adminNote, setAdminNote] = useState('');
  const [resolving, setResolving] = useState(false);

  const loadDisputes = async () => {
    setLoading(true);
    const { data } = await supabase
      .from('disputes')
      .select(`
        *,
        raised_by_user:profiles!disputes_raised_by_fkey(full_name),
        order:orders(
          id,
          order_number,
          crop_name,
          total_amount,
          farmer_payout_amount,
          delivery_method,
          requested_date,
          status,
          buyer:profiles!orders_buyer_id_fkey(full_name),
          farmer:profiles!orders_farmer_id_fkey(full_name)
        )
      `)
      .order('created_at', { ascending: false });

    setDisputes(data || []);
    setLoading(false);
  };

  useEffect(() => {
    loadDisputes();
  }, []);

  const openDisputeDetail = async (dispute: any) => {
    setSelectedDispute(dispute);
    setAdminNote('');
    setPartialRefundAmt('');
    setResolution('full_refund');

    // Admins may read chats ONLY for disputed orders via admin_get_dispute_chat RPC (which records audit_log)
    setLoadingChat(true);
    const { data: messages, error: chatError } = await supabase
      .rpc('admin_get_dispute_chat', { p_order_id: dispute.order_id });

    if (chatError) {
      console.error('Failed to load dispute chat:', chatError);
      setChatMessages([]);
    } else {
      setChatMessages(messages || []);
    }
    setLoadingChat(false);
  };

  const handleResolve = async () => {
    if (!selectedDispute || !adminNote.trim()) {
      alert('An official admin mediation note is required.');
      return;
    }

    if (resolution === 'partial_refund' && (!partialRefundAmt || Number(partialRefundAmt) <= 0)) {
      alert('Please enter a valid partial refund amount.');
      return;
    }

    setResolving(true);
    try {
      const { error } = await supabase.rpc('admin_resolve_dispute', {
        p_dispute_id: selectedDispute.id,
        p_resolution: resolution,
        p_refund_amount: resolution === 'partial_refund' ? Number(partialRefundAmt) : 0,
        p_note: adminNote.trim(),
      });

      if (error) throw error;

      alert('Dispute resolved successfully.');
      setSelectedDispute(null);
      await loadDisputes();
    } catch (err: any) {
      alert(`Error resolving dispute: ${err.message}`);
    } finally {
      setResolving(false);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Mediation & Dispute Resolution</h1>
        <p className="text-sm text-slate-500">Adjudicate quality or delivery disputes with full chat transcripts and photo evidence</p>
      </div>

      <div className="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-sm">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                <th className="py-3 px-4">Order #</th>
                <th className="py-3 px-4">Raised By</th>
                <th className="py-3 px-4">Reason Summary</th>
                <th className="py-3 px-4">Status</th>
                <th className="py-3 px-4">Order Amount</th>
                <th className="py-3 px-4 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200">
              {loading ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-slate-500">
                    Loading disputes...
                  </td>
                </tr>
              ) : disputes.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-slate-500">
                    No active disputes. Marketplace transactions are operating smoothly.
                  </td>
                </tr>
              ) : (
                disputes.map((d) => (
                  <tr key={d.id} className="hover:bg-slate-50 transition-colors">
                    <td className="py-3 px-4 font-mono font-bold text-slate-900">{d.order?.order_number}</td>
                    <td className="py-3 px-4 font-semibold text-slate-800">{d.raised_by_user?.full_name}</td>
                    <td className="py-3 px-4 text-xs text-slate-600 max-w-sm truncate">{d.reason}</td>
                    <td className="py-3 px-4">
                      <span className={`px-2 py-0.5 rounded-full text-xs font-bold uppercase tracking-wider ${
                        d.status === 'open' ? 'bg-amber-100 text-amber-800' : 'bg-slate-100 text-slate-700'
                      }`}>
                        {d.status}
                      </span>
                    </td>
                    <td className="py-3 px-4 font-bold text-slate-900">
                      Rs. {Number(d.order?.total_amount).toFixed(2)}
                    </td>
                    <td className="py-3 px-4 text-right">
                      <button
                        onClick={() => openDisputeDetail(d)}
                        className="px-3 py-1.5 bg-slate-900 hover:bg-slate-800 text-white rounded-lg text-xs font-semibold inline-flex items-center gap-1.5"
                      >
                        <Eye className="w-3.5 h-3.5" />
                        <span>Adjudicate</span>
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Adjudication Drawer / Modal */}
      {selectedDispute && (
        <div className="fixed inset-0 z-50 bg-black/50 flex justify-end">
          <div className="bg-white w-full max-w-2xl h-full shadow-2xl overflow-y-auto p-6 space-y-6">
            <div className="flex items-center justify-between border-b pb-4">
              <div>
                <h2 className="text-xl font-bold text-slate-900">Dispute for {selectedDispute.order?.order_number}</h2>
                <span className="text-xs text-slate-500">Crop: {selectedDispute.order?.crop_name}</span>
              </div>
              <button
                onClick={() => setSelectedDispute(null)}
                className="p-1.5 text-slate-400 hover:text-slate-600 rounded-lg hover:bg-slate-100"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Buyer Complaint */}
            <div className="bg-amber-50 border border-amber-200 rounded-xl p-4 space-y-2">
              <div className="flex items-center gap-2 text-amber-800 font-bold text-sm">
                <AlertTriangle className="w-4 h-4" />
                <span>Buyer Grievance</span>
              </div>
              <p className="text-sm text-amber-950 whitespace-pre-wrap">{selectedDispute.reason}</p>
            </div>

            {/* Order Parties & Amounts */}
            <div className="grid grid-cols-2 gap-3 text-sm bg-slate-50 p-4 rounded-xl border border-slate-200">
              <div>
                <span className="text-xs text-slate-500 block">Buyer:</span>
                <span className="font-semibold text-slate-900">{selectedDispute.order?.buyer?.full_name}</span>
              </div>
              <div>
                <span className="text-xs text-slate-500 block">Farmer:</span>
                <span className="font-semibold text-slate-900">{selectedDispute.order?.farmer?.full_name}</span>
              </div>
              <div>
                <span className="text-xs text-slate-500 block">Total In Escrow:</span>
                <span className="font-bold text-slate-900">Rs. {Number(selectedDispute.order?.total_amount).toFixed(2)}</span>
              </div>
              <div>
                <span className="text-xs text-slate-500 block">Farmer Quoted Payout:</span>
                <span className="font-bold text-emerald-700">Rs. {Number(selectedDispute.order?.farmer_payout_amount).toFixed(2)}</span>
              </div>
            </div>

            {/* Order Chat Transcript (Exclusively accessible during disputes) */}
            <div className="space-y-3">
              <div className="flex items-center gap-2 text-slate-800 font-bold text-sm">
                <MessageSquare className="w-4 h-4 text-green-600" />
                <span>Verified In-App Chat Transcript</span>
              </div>

              <div className="bg-slate-50 border border-slate-200 rounded-xl p-4 max-h-60 overflow-y-auto space-y-3">
                {loadingChat ? (
                  <div className="text-xs text-slate-500 text-center">Loading conversation transcript...</div>
                ) : chatMessages.length === 0 ? (
                  <div className="text-xs text-slate-400 text-center">No messages were exchanged in this order.</div>
                ) : (
                  chatMessages.map((m) => (
                    <div
                      key={m.id}
                      className={`text-xs p-2.5 rounded-lg max-w-sm ${
                        m.sender_id === null
                          ? 'bg-slate-200 text-slate-700 mx-auto text-center'
                          : m.sender_id === selectedDispute.order?.buyer_id
                          ? 'bg-blue-100 text-blue-900 ml-auto'
                          : 'bg-green-100 text-green-900 mr-auto'
                      }`}
                    >
                      <div className="font-semibold mb-1 opacity-70">
                        {m.sender_id === null ? 'System' : m.sender_id === selectedDispute.order?.buyer_id ? 'Buyer' : 'Farmer'}
                      </div>
                      <div className="text-sm">{m.body}</div>
                    </div>
                  ))
                )}
              </div>
            </div>

            {/* Resolution Form (if open) */}
            {selectedDispute.status === 'open' ? (
              <div className="space-y-4 pt-2 border-t">
                <h3 className="text-sm font-bold text-slate-900 uppercase tracking-wider">Arbitration Decision</h3>

                <div className="space-y-2">
                  <label className="flex items-center gap-2 text-sm text-slate-800 cursor-pointer">
                    <input
                      type="radio"
                      name="resolution"
                      value="full_refund"
                      checked={resolution === 'full_refund'}
                      onChange={() => setResolution('full_refund')}
                      className="text-green-600"
                    />
                    <span>Full Refund to Buyer (Rs. {Number(selectedDispute.order?.total_amount).toFixed(2)}, Farmer payout void)</span>
                  </label>

                  <label className="flex items-center gap-2 text-sm text-slate-800 cursor-pointer">
                    <input
                      type="radio"
                      name="resolution"
                      value="partial_refund"
                      checked={resolution === 'partial_refund'}
                      onChange={() => setResolution('partial_refund')}
                      className="text-green-600"
                    />
                    <span>Partial Refund (Specify amount to return to buyer)</span>
                  </label>

                  <label className="flex items-center gap-2 text-sm text-slate-800 cursor-pointer">
                    <input
                      type="radio"
                      name="resolution"
                      value="release_to_farmer"
                      checked={resolution === 'release_to_farmer'}
                      onChange={() => setResolution('release_to_farmer')}
                      className="text-green-600"
                    />
                    <span>Release Full Payment to Farmer (No refund to buyer)</span>
                  </label>
                </div>

                {resolution === 'partial_refund' && (
                  <div>
                    <label className="block text-xs font-semibold text-slate-600 mb-1">
                      Partial Refund Amount (LKR)
                    </label>
                    <input
                      type="number"
                      step="0.01"
                      placeholder="e.g. 5000.00"
                      value={partialRefundAmt}
                      onChange={(e) => setPartialRefundAmt(e.target.value)}
                      className="w-full border border-slate-300 rounded-lg p-2 text-sm focus:ring-2 focus:ring-green-600 focus:outline-none"
                    />
                  </div>
                )}

                <div>
                  <label className="block text-xs font-semibold text-slate-600 mb-1">
                    Official Mediation Finding (Required)
                  </label>
                  <textarea
                    rows={3}
                    placeholder="Enter factual reasoning based on evidence..."
                    value={adminNote}
                    onChange={(e) => setAdminNote(e.target.value)}
                    className="w-full border border-slate-300 rounded-lg p-2 text-sm focus:ring-2 focus:ring-green-600 focus:outline-none"
                  />
                </div>

                <button
                  disabled={resolving || !adminNote.trim()}
                  onClick={handleResolve}
                  className="w-full py-2.5 bg-green-700 hover:bg-green-600 text-white rounded-lg text-sm font-bold shadow-md disabled:opacity-50"
                >
                  {resolving ? 'Executing Arbitration...' : 'Execute Resolution'}
                </button>
              </div>
            ) : (
              <div className="bg-slate-100 p-4 rounded-xl space-y-2 text-sm">
                <div className="font-bold text-slate-800">
                  Resolved: {selectedDispute.resolution?.replace('_', ' ').toUpperCase()}
                </div>
                <div className="text-xs text-slate-600">Admin Finding: {selectedDispute.admin_note}</div>
                <div className="text-xs text-slate-400">Date: {selectedDispute.resolved_at}</div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
