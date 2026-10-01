import React, { useEffect, useState } from 'react';
import { supabase } from '../lib/supabase';
import { RotateCcw, CheckCircle2 } from 'lucide-react';

export const RefundsPage: React.FC = () => {
  const [orders, setOrders] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedOrder, setSelectedOrder] = useState<any | null>(null);
  const [refundRef, setRefundRef] = useState('');
  const [refundAmt, setRefundAmt] = useState('');
  const [processing, setProcessing] = useState(false);

  const loadRefunds = async () => {
    setLoading(true);
    const { data } = await supabase
      .from('orders')
      .select(`
        id,
        order_number,
        buyer_id,
        farmer_id,
        total_amount,
        refund_amount,
        refund_status,
        end_reason,
        buyer:profiles!orders_buyer_id_fkey(full_name),
        farmer:profiles!orders_farmer_id_fkey(full_name)
      `)
      .eq('refund_status', 'required')
      .order('updated_at', { ascending: false });

    setOrders(data || []);
    setLoading(false);
  };

  useEffect(() => {
    loadRefunds();
  }, []);

  const openProcessModal = (order: any) => {
    setSelectedOrder(order);
    setRefundAmt(order.refund_amount ? Number(order.refund_amount).toFixed(2) : Number(order.total_amount).toFixed(2));
    setRefundRef('');
  };

  const handleMarkRefunded = async () => {
    if (!selectedOrder || !refundRef.trim() || !refundAmt) return;

    setProcessing(true);
    try {
      const { error } = await supabase.rpc('admin_mark_refunded', {
        p_order_id: selectedOrder.id,
        p_reference: refundRef.trim(),
        p_amount: Number(refundAmt),
      });

      if (error) throw error;

      alert(`Order ${selectedOrder.order_number} marked refunded successfully.`);
      setSelectedOrder(null);
      await loadRefunds();
    } catch (err: any) {
      alert(`Error processing refund: ${err.message}`);
    } finally {
      setProcessing(false);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Required Buyer Refunds</h1>
        <p className="text-sm text-slate-500">Orders requiring manual reimbursement in PayHere (e.g. farmer cancellation after payment, dispute outcomes)</p>
      </div>

      <div className="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-sm">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                <th className="py-3 px-4">Order #</th>
                <th className="py-3 px-4">Buyer Name</th>
                <th className="py-3 px-4">Cause / Reason</th>
                <th className="py-3 px-4">Total Paid</th>
                <th className="py-3 px-4">Refund Due</th>
                <th className="py-3 px-4 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200">
              {loading ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-slate-500">
                    Loading required refunds...
                  </td>
                </tr>
              ) : orders.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-slate-500">
                    No refunds required. All buyer payments are accounted for.
                  </td>
                </tr>
              ) : (
                orders.map((o) => (
                  <tr key={o.id} className="hover:bg-slate-50 transition-colors">
                    <td className="py-3 px-4 font-mono font-bold text-slate-900">{o.order_number}</td>
                    <td className="py-3 px-4 font-semibold text-slate-800">{o.buyer?.full_name}</td>
                    <td className="py-3 px-4 text-xs text-slate-600 max-w-xs truncate">{o.end_reason || 'Farmer cancelled after payment / Dispute resolution'}</td>
                    <td className="py-3 px-4 text-slate-700">Rs. {Number(o.total_amount).toFixed(2)}</td>
                    <td className="py-3 px-4 font-bold text-red-600">
                      Rs. {Number(o.refund_amount || o.total_amount).toFixed(2)}
                    </td>
                    <td className="py-3 px-4 text-right">
                      <button
                        onClick={() => openProcessModal(o)}
                        className="px-3 py-1.5 bg-red-600 hover:bg-red-500 text-white rounded-lg text-xs font-semibold inline-flex items-center gap-1.5"
                      >
                        <RotateCcw className="w-3.5 h-3.5" />
                        <span>Record Refund</span>
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Record Refund Modal */}
      {selectedOrder && (
        <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-2xl max-w-md w-full p-6 space-y-4">
            <h3 className="text-lg font-bold text-slate-900">Record PayHere Refund</h3>
            <p className="text-sm text-slate-600">
              Please process the reversal in the PayHere Merchant Portal, then record the transaction reference below to close the refund requirement.
            </p>

            <div className="space-y-3">
              <div>
                <label className="block text-xs font-semibold text-slate-600 mb-1">Refund Amount (LKR)</label>
                <input
                  type="number"
                  step="0.01"
                  value={refundAmt}
                  onChange={(e) => setRefundAmt(e.target.value)}
                  className="w-full border border-slate-300 rounded-lg p-2 text-sm focus:ring-2 focus:ring-red-600 focus:outline-none"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-600 mb-1">PayHere Refund Reference / ARN</label>
                <input
                  type="text"
                  placeholder="e.g. PH-REF-902184"
                  value={refundRef}
                  onChange={(e) => setRefundRef(e.target.value)}
                  className="w-full border border-slate-300 rounded-lg p-2 text-sm focus:ring-2 focus:ring-red-600 focus:outline-none"
                />
              </div>
            </div>

            <div className="flex justify-end gap-3 pt-2">
              <button
                onClick={() => setSelectedOrder(null)}
                className="px-4 py-2 border border-slate-300 rounded-lg text-sm text-slate-600 hover:bg-slate-50"
              >
                Cancel
              </button>
              <button
                disabled={processing || !refundRef.trim() || !refundAmt}
                onClick={handleMarkRefunded}
                className="px-4 py-2 bg-red-600 hover:bg-red-500 text-white rounded-lg text-sm font-semibold disabled:opacity-50"
              >
                {processing ? 'Processing...' : 'Confirm Refund Dispatched'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
