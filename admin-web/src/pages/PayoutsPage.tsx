import React, { useEffect, useState } from 'react';
import { supabase } from '../lib/supabase';
import { Download, CheckCircle2, CheckSquare, Square } from 'lucide-react';

export const PayoutsPage: React.FC = () => {
  const [orders, setOrders] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [reference, setReference] = useState('');
  const [processing, setProcessing] = useState(false);

  const loadPendingPayouts = async () => {
    setLoading(true);
    const { data } = await supabase
      .from('orders')
      .select(`
        id,
        order_number,
        farmer_id,
        farmer_payout_amount,
        completed_at,
        farmer:profiles!orders_farmer_id_fkey(full_name),
        farmer_private!orders_farmer_id_fkey(
          bank_name,
          bank_branch,
          account_holder_name,
          account_number
        )
      `)
      .eq('payout_status', 'pending')
      .order('completed_at', { ascending: true });

    setOrders(data || []);
    setSelectedIds([]);
    setLoading(false);
  };

  useEffect(() => {
    loadPendingPayouts();
  }, []);

  const toggleSelect = (id: string) => {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]
    );
  };

  const toggleSelectAll = () => {
    if (selectedIds.length === orders.length) {
      setSelectedIds([]);
    } else {
      setSelectedIds(orders.map((o) => o.id));
    }
  };

  const exportCsv = () => {
    const toExport = orders.filter((o) => selectedIds.length === 0 || selectedIds.includes(o.id));
    const headers = ['Order Number', 'Farmer Name', 'Bank Name', 'Branch', 'Account Name', 'Account Number', 'Amount (LKR)'];
    const rows = toExport.map((o) => [
      o.order_number,
      `"${o.farmer?.full_name || ''}"`,
      `"${o.farmer_private?.bank_name || ''}"`,
      `"${o.farmer_private?.bank_branch || ''}"`,
      `"${o.farmer_private?.account_holder_name || ''}"`,
      `"${o.farmer_private?.account_number || ''}"`,
      Number(o.farmer_payout_amount).toFixed(2),
    ]);

    const csvContent = [headers.join(','), ...rows.map((r) => r.join(','))].join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `farmer_payouts_${new Date().toISOString().split('T')[0]}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleMarkPaid = async () => {
    if (selectedIds.length === 0) {
      alert('Please select at least one order to mark as paid.');
      return;
    }
    if (!reference.trim()) {
      alert('A bank transaction reference / slip ID is required.');
      return;
    }

    setProcessing(true);
    try {
      for (const orderId of selectedIds) {
        const { error } = await supabase.rpc('admin_mark_payout_paid', {
          p_order_id: orderId,
          p_reference: reference.trim(),
        });
        if (error) throw error;
      }

      alert(`Successfully marked ${selectedIds.length} orders as paid out.`);
      setReference('');
      await loadPendingPayouts();
    } catch (err: any) {
      alert(`Error processing payout: ${err.message}`);
    } finally {
      setProcessing(false);
    }
  };

  const totalSelectedAmount = orders
    .filter((o) => selectedIds.includes(o.id))
    .reduce((sum, o) => sum + (Number(o.farmer_payout_amount) || 0), 0);

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Farmer Payout Management</h1>
          <p className="text-sm text-slate-500">Orders completed with pending bank transfers to farmers</p>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={exportCsv}
            disabled={orders.length === 0}
            className="flex items-center gap-2 px-3 py-2 bg-white border border-slate-300 rounded-lg text-sm font-semibold text-slate-700 hover:bg-slate-50 transition-colors disabled:opacity-50"
          >
            <Download className="w-4 h-4" />
            <span>Export CSV</span>
          </button>
        </div>
      </div>

      {/* Action Bar */}
      {selectedIds.length > 0 && (
        <div className="bg-emerald-50 border border-emerald-200 rounded-xl p-4 flex flex-col sm:flex-row items-center justify-between gap-4">
          <div>
            <div className="text-sm font-bold text-emerald-900">
              {selectedIds.length} orders selected ({orders.length} total pending)
            </div>
            <div className="text-xs text-emerald-700">
              Total payout sum: <span className="font-bold">Rs. {totalSelectedAmount.toLocaleString(undefined, { minimumFractionDigits: 2 })}</span>
            </div>
          </div>

          <div className="flex items-center gap-3 w-full sm:w-auto">
            <input
              type="text"
              placeholder="Bank Transfer Ref / Cheque #"
              value={reference}
              onChange={(e) => setReference(e.target.value)}
              className="bg-white border border-slate-300 rounded-lg px-3 py-1.5 text-sm w-full sm:w-64 focus:ring-2 focus:ring-emerald-600 focus:outline-none"
            />
            <button
              onClick={handleMarkPaid}
              disabled={processing || !reference.trim()}
              className="flex items-center gap-1.5 px-4 py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg text-sm font-semibold whitespace-nowrap disabled:opacity-50"
            >
              <CheckCircle2 className="w-4 h-4" />
              <span>{processing ? 'Processing...' : 'Mark Paid Out'}</span>
            </button>
          </div>
        </div>
      )}

      {/* Payouts Table */}
      <div className="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-sm">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                <th className="py-3 px-4 w-10">
                  <button onClick={toggleSelectAll} className="text-slate-500 hover:text-slate-800">
                    {selectedIds.length === orders.length && orders.length > 0 ? (
                      <CheckSquare className="w-4 h-4 text-emerald-600" />
                    ) : (
                      <Square className="w-4 h-4" />
                    )}
                  </button>
                </th>
                <th className="py-3 px-4">Order #</th>
                <th className="py-3 px-4">Farmer Name</th>
                <th className="py-3 px-4">Bank Details</th>
                <th className="py-3 px-4">Completed Date</th>
                <th className="py-3 px-4 text-right">Payout Amount</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200">
              {loading ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-slate-500">
                    Loading pending payouts...
                  </td>
                </tr>
              ) : orders.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-slate-500">
                    No pending payouts. All completed orders have been settled.
                  </td>
                </tr>
              ) : (
                orders.map((o) => {
                  const isChecked = selectedIds.includes(o.id);
                  return (
                    <tr
                      key={o.id}
                      className={`hover:bg-slate-50 transition-colors ${isChecked ? 'bg-emerald-50/40' : ''}`}
                    >
                      <td className="py-3 px-4">
                        <button onClick={() => toggleSelect(o.id)}>
                          {isChecked ? (
                            <CheckSquare className="w-4 h-4 text-emerald-600" />
                          ) : (
                            <Square className="w-4 h-4 text-slate-400" />
                          )}
                        </button>
                      </td>
                      <td className="py-3 px-4 font-mono font-bold text-slate-900">{o.order_number}</td>
                      <td className="py-3 px-4 font-semibold text-slate-800">{o.farmer?.full_name}</td>
                      <td className="py-3 px-4 text-xs">
                        <div className="font-semibold text-slate-800">{o.farmer_private?.bank_name} ({o.farmer_private?.bank_branch})</div>
                        <div className="font-mono text-slate-600">Acc: {o.farmer_private?.account_number}</div>
                        <div className="text-slate-500">Holder: {o.farmer_private?.account_holder_name}</div>
                      </td>
                      <td className="py-3 px-4 text-xs text-slate-600">
                        {o.completed_at ? o.completed_at.take(16).replace('T', ' ') : '—'}
                      </td>
                      <td className="py-3 px-4 text-right font-extrabold text-slate-900">
                        Rs. {Number(o.farmer_payout_amount).toFixed(2)}
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
