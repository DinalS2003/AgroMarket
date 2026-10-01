import React, { useEffect, useState } from 'react';
import { supabase } from '../lib/supabase';
import { Search, Eye, X, Filter } from 'lucide-react';

export const OrdersPage: React.FC = () => {
  const [orders, setOrders] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState('all');
  const [search, setSearch] = useState('');
  const [selectedOrder, setSelectedOrder] = useState<any | null>(null);

  const loadOrders = async () => {
    setLoading(true);
    let query = supabase
      .from('orders')
      .select(`
        *,
        buyer:profiles!orders_buyer_id_fkey(full_name),
        farmer:profiles!orders_farmer_id_fkey(full_name),
        order_private_details(*)
      `)
      .order('requested_at', { ascending: false });

    if (statusFilter !== 'all') {
      query = query.eq('status', statusFilter);
    }

    const { data } = await query;
    setOrders(data || []);
    setLoading(false);
  };

  useEffect(() => {
    loadOrders();
  }, [statusFilter]);

  const filtered = orders.filter((o) => {
    const q = search.toLowerCase();
    return (
      o.order_number?.toLowerCase().includes(q) ||
      o.crop_name?.toLowerCase().includes(q) ||
      o.buyer?.full_name?.toLowerCase().includes(q) ||
      o.farmer?.full_name?.toLowerCase().includes(q)
    );
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Order Lifecycle Registry</h1>
          <p className="text-sm text-slate-500">Monitor order transactions, escrow status, and deliveries</p>
        </div>

        <div className="flex items-center gap-3">
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="bg-white border border-slate-300 rounded-lg px-3 py-2 text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-green-600"
          >
            <option value="all">All Statuses</option>
            <option value="requested">Requested</option>
            <option value="accepted">Accepted</option>
            <option value="paid">Paid (In Escrow)</option>
            <option value="ready">Harvest Ready</option>
            <option value="dispatched">Dispatched</option>
            <option value="delivered">Delivered</option>
            <option value="completed">Completed</option>
            <option value="disputed">Disputed</option>
            <option value="cancelled">Cancelled</option>
            <option value="refunded">Refunded</option>
            <option value="expired">Expired</option>
          </select>

          <div className="relative w-64">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-3" />
            <input
              type="text"
              placeholder="Search order #, crop, user..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full bg-white border border-slate-300 rounded-lg pl-9 pr-4 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-green-600"
            />
          </div>
        </div>
      </div>

      <div className="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-sm">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                <th className="py-3 px-4">Order #</th>
                <th className="py-3 px-4">Harvest & Qty</th>
                <th className="py-3 px-4">Buyer / Farmer</th>
                <th className="py-3 px-4">Total Amount</th>
                <th className="py-3 px-4">Status</th>
                <th className="py-3 px-4">Payout / Refund</th>
                <th className="py-3 px-4 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200">
              {loading ? (
                <tr>
                  <td colSpan={7} className="py-8 text-center text-slate-500">
                    Loading orders...
                  </td>
                </tr>
              ) : filtered.length === 0 ? (
                <tr>
                  <td colSpan={7} className="py-8 text-center text-slate-500">
                    No orders match the selected filter.
                  </td>
                </tr>
              ) : (
                filtered.map((o) => (
                  <tr key={o.id} className="hover:bg-slate-50 transition-colors">
                    <td className="py-3 px-4 font-mono font-bold text-slate-900">{o.order_number}</td>
                    <td className="py-3 px-4">
                      <div className="font-semibold text-slate-800">{o.crop_name}</div>
                      <div className="text-xs text-slate-500">{o.quantity_kg} kg @ Rs. {o.price_per_kg}</div>
                    </td>
                    <td className="py-3 px-4 text-xs">
                      <div>Buyer: <span className="font-medium text-slate-800">{o.buyer?.full_name}</span></div>
                      <div>Farmer: <span className="font-medium text-slate-800">{o.farmer?.full_name}</span></div>
                    </td>
                    <td className="py-3 px-4 font-bold text-slate-900">
                      Rs. {Number(o.total_amount).toFixed(2)}
                    </td>
                    <td className="py-3 px-4">
                      <span className={`px-2 py-0.5 rounded-full text-xs font-bold uppercase tracking-wider ${
                        o.status === 'completed' ? 'bg-green-100 text-green-800' :
                        o.status === 'paid' ? 'bg-blue-100 text-blue-800' :
                        o.status === 'disputed' ? 'bg-amber-100 text-amber-800' :
                        o.status === 'cancelled' || o.status === 'rejected' ? 'bg-red-100 text-red-800' :
                        'bg-slate-100 text-slate-800'
                      }`}>
                        {o.status}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-xs">
                      {o.payout_status !== 'none' && (
                        <div>Payout: <span className="font-semibold uppercase">{o.payout_status}</span></div>
                      )}
                      {o.refund_status !== 'none' && (
                        <div className="text-red-600 font-bold">Refund: {o.refund_status.toUpperCase()}</div>
                      )}
                    </td>
                    <td className="py-3 px-4 text-right">
                      <button
                        onClick={() => setSelectedOrder(o)}
                        className="px-2.5 py-1.5 text-xs font-medium text-slate-600 hover:text-green-700 hover:bg-green-50 rounded transition-colors inline-flex items-center gap-1"
                      >
                        <Eye className="w-3.5 h-3.5" />
                        <span>Inspect</span>
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Order Detail Modal */}
      {selectedOrder && (
        <div className="fixed inset-0 z-50 bg-black/50 flex justify-end">
          <div className="bg-white w-full max-w-xl h-full shadow-2xl overflow-y-auto p-6 space-y-6">
            <div className="flex items-center justify-between border-b pb-4">
              <div>
                <h2 className="text-xl font-bold text-slate-900">{selectedOrder.order_number}</h2>
                <span className="text-xs text-slate-400">Order ID: {selectedOrder.id}</span>
              </div>
              <button
                onClick={() => setSelectedOrder(null)}
                className="p-1.5 text-slate-400 hover:text-slate-600 rounded-lg hover:bg-slate-100"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Financials Breakdown */}
            <div className="bg-slate-50 p-4 rounded-xl border border-slate-200 space-y-2 text-sm">
              <h3 className="font-bold text-slate-800 uppercase tracking-wider text-xs">Financial Audit</h3>
              <div className="flex justify-between">
                <span>Crop Subtotal ({selectedOrder.quantity_kg} kg x Rs. {selectedOrder.price_per_kg})</span>
                <span className="font-medium">Rs. {Number(selectedOrder.subtotal).toFixed(2)}</span>
              </div>
              <div className="flex justify-between">
                <span>Delivery Fee ({selectedOrder.delivery_method})</span>
                <span className="font-medium">Rs. {Number(selectedOrder.delivery_fee).toFixed(2)}</span>
              </div>
              <div className="flex justify-between text-green-700">
                <span>Platform Commission ({selectedOrder.commission_rate * 100}%)</span>
                <span>- Rs. {Number(selectedOrder.commission_amount).toFixed(2)}</span>
              </div>
              <div className="border-t pt-2 flex justify-between font-bold text-slate-900">
                <span>Buyer Total</span>
                <span>Rs. {Number(selectedOrder.total_amount).toFixed(2)}</span>
              </div>
              <div className="flex justify-between font-bold text-emerald-700">
                <span>Farmer Payout Amount</span>
                <span>Rs. {Number(selectedOrder.farmer_payout_amount).toFixed(2)}</span>
              </div>
            </div>

            {/* Private Coordination Details (Admin viewable) */}
            <div className="bg-slate-50 p-4 rounded-xl border border-slate-200 space-y-2 text-sm">
              <h3 className="font-bold text-slate-800 uppercase tracking-wider text-xs">Admin Unmasked Private Details</h3>
              <div>
                <span className="text-xs text-slate-500 block">Buyer Delivery Address:</span>
                <span className="font-medium text-slate-800">{selectedOrder.order_private_details?.delivery_address || 'N/A (Buyer Arranged)'}</span>
              </div>
              <div>
                <span className="text-xs text-slate-500 block">Farmer Pickup Landmark:</span>
                <span className="font-medium text-slate-800">{selectedOrder.order_private_details?.pickup_landmark || 'N/A (Farmer Delivery)'}</span>
              </div>
            </div>

            {/* Timestamps */}
            <div className="bg-slate-50 p-4 rounded-xl border border-slate-200 space-y-1.5 text-xs text-slate-600">
              <h3 className="font-bold text-slate-800 uppercase tracking-wider text-xs mb-2">Timeline</h3>
              <div>Requested: {selectedOrder.requested_at || '—'}</div>
              <div>Accepted: {selectedOrder.accepted_at || '—'}</div>
              <div>Paid: {selectedOrder.paid_at || '—'}</div>
              <div>Ready: {selectedOrder.ready_at || '—'}</div>
              <div>Dispatched: {selectedOrder.dispatched_at || '—'}</div>
              <div>Delivered: {selectedOrder.delivered_at || '—'}</div>
              <div>Completed: {selectedOrder.completed_at || '—'}</div>
              {selectedOrder.ended_at && (
                <div className="text-red-600 font-bold">
                  Ended by {selectedOrder.ended_by}: {selectedOrder.end_reason} ({selectedOrder.ended_at})
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
