import React, { useEffect, useState } from 'react';
import { supabase } from '../lib/supabase';
import { Download, Calendar, DollarSign, TrendingUp } from 'lucide-react';

export const CommissionReportPage: React.FC = () => {
  const [orders, setOrders] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');

  const loadData = async () => {
    setLoading(true);
    let query = supabase
      .from('orders')
      .select('order_number, subtotal, delivery_fee, commission_amount, total_amount, paid_at, status')
      .in('status', ['paid', 'ready', 'dispatched', 'delivered', 'completed']);

    if (startDate) {
      query = query.gte('paid_at', startDate);
    }
    if (endDate) {
      query = query.lte('paid_at', endDate + 'T23:59:59Z');
    }

    const { data } = await query;
    setOrders(data || []);
    setLoading(false);
  };

  useEffect(() => {
    loadData();
  }, [startDate, endDate]);

  const totalSubtotal = orders.reduce((sum, o) => sum + (Number(o.subtotal) || 0), 0);
  const totalCommission = orders.reduce((sum, o) => sum + (Number(o.commission_amount) || 0), 0);
  const totalDeliveryFees = orders.reduce((sum, o) => sum + (Number(o.delivery_fee) || 0), 0);
  const totalGross = orders.reduce((sum, o) => sum + (Number(o.total_amount) || 0), 0);

  const exportCsv = () => {
    const headers = ['Order Number', 'Subtotal (LKR)', 'Delivery Fee (LKR)', 'Commission (3%) (LKR)', 'Total (LKR)', 'Paid Date'];
    const rows = orders.map((o) => [
      o.order_number,
      Number(o.subtotal).toFixed(2),
      Number(o.delivery_fee).toFixed(2),
      Number(o.commission_amount).toFixed(2),
      Number(o.total_amount).toFixed(2),
      o.paid_at || '',
    ]);

    const csvContent = [headers.join(','), ...rows.map((r) => r.join(','))].join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `agromarket_commission_report_${new Date().toISOString().split('T')[0]}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Commission & Revenue Statement</h1>
          <p className="text-sm text-slate-500">Official accounting breakdown of 3% platform commission and 100% pass-through delivery fees</p>
        </div>

        <div className="flex items-center gap-3">
          <input
            type="date"
            value={startDate}
            onChange={(e) => setStartDate(e.target.value)}
            className="bg-white border border-slate-300 rounded-lg px-3 py-1.5 text-xs text-slate-700"
          />
          <span className="text-slate-400 text-xs">to</span>
          <input
            type="date"
            value={endDate}
            onChange={(e) => setEndDate(e.target.value)}
            className="bg-white border border-slate-300 rounded-lg px-3 py-1.5 text-xs text-slate-700"
          />
          <button
            onClick={exportCsv}
            disabled={orders.length === 0}
            className="flex items-center gap-2 px-3 py-2 bg-green-700 hover:bg-green-600 text-white rounded-lg text-sm font-semibold transition-colors disabled:opacity-50"
          >
            <Download className="w-4 h-4" />
            <span>Export CSV</span>
          </button>
        </div>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
          <div className="text-xs font-semibold text-slate-500 uppercase">Crop Sales Volume</div>
          <div className="text-xl font-bold text-slate-900 mt-1">
            Rs. {totalSubtotal.toLocaleString(undefined, { minimumFractionDigits: 2 })}
          </div>
          <div className="text-xs text-slate-400 mt-1">Base harvest value</div>
        </div>

        <div className="bg-emerald-50 p-5 rounded-xl border border-emerald-200 shadow-sm">
          <div className="text-xs font-bold text-emerald-800 uppercase">Platform Commission (3%)</div>
          <div className="text-xl font-extrabold text-emerald-700 mt-1">
            Rs. {totalCommission.toLocaleString(undefined, { minimumFractionDigits: 2 })}
          </div>
          <div className="text-xs text-emerald-600 mt-1">Calculated solely on crop subtotal</div>
        </div>

        <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
          <div className="text-xs font-semibold text-slate-500 uppercase">Delivery Fees Collected</div>
          <div className="text-xl font-bold text-slate-900 mt-1">
            Rs. {totalDeliveryFees.toLocaleString(undefined, { minimumFractionDigits: 2 })}
          </div>
          <div className="text-xs text-slate-400 mt-1">Transferred 100% to farmers</div>
        </div>

        <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
          <div className="text-xs font-semibold text-slate-500 uppercase">Gross Platform Turnover</div>
          <div className="text-xl font-bold text-slate-900 mt-1">
            Rs. {totalGross.toLocaleString(undefined, { minimumFractionDigits: 2 })}
          </div>
          <div className="text-xs text-slate-400 mt-1">Total buyer payments processed</div>
        </div>
      </div>

      {/* Breakdown Table */}
      <div className="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-sm">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                <th className="py-3 px-4">Order #</th>
                <th className="py-3 px-4">Paid Date</th>
                <th className="py-3 px-4">Crop Subtotal</th>
                <th className="py-3 px-4">Delivery Fee</th>
                <th className="py-3 px-4">Commission (3%)</th>
                <th className="py-3 px-4 text-right">Total Amount</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200">
              {loading ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-slate-500">
                    Loading commission transactions...
                  </td>
                </tr>
              ) : orders.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-slate-500">
                    No transactions found in the chosen date range.
                  </td>
                </tr>
              ) : (
                orders.map((o, idx) => (
                  <tr key={idx} className="hover:bg-slate-50 transition-colors">
                    <td className="py-3 px-4 font-mono font-bold text-slate-900">{o.order_number}</td>
                    <td className="py-3 px-4 text-xs text-slate-600">{o.paid_at ? o.paid_at.take(10) : '—'}</td>
                    <td className="py-3 px-4 font-medium text-slate-800">Rs. {Number(o.subtotal).toFixed(2)}</td>
                    <td className="py-3 px-4 text-slate-600">Rs. {Number(o.delivery_fee).toFixed(2)}</td>
                    <td className="py-3 px-4 font-bold text-emerald-700">Rs. {Number(o.commission_amount).toFixed(2)}</td>
                    <td className="py-3 px-4 text-right font-extrabold text-slate-900">
                      Rs. {Number(o.total_amount).toFixed(2)}
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
