import React, { useEffect, useState } from 'react';
import { supabase } from '../lib/supabase';
import { Save, Plus } from 'lucide-react';

export const SettingsPage: React.FC = () => {
  const [settings, setSettings] = useState<any>(null);
  const [districts, setDistricts] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  // New city form
  const [selectedDistrictId, setSelectedDistrictId] = useState(1);
  const [newCityName, setNewCityName] = useState('');
  const [newPostalCode, setNewPostalCode] = useState('');
  const [addingCity, setAddingCity] = useState(false);

  const loadData = async () => {
    setLoading(true);
    const { data: appSettings } = await supabase.from('app_settings').select('*').single();
    const { data: dists } = await supabase.from('districts').select('*').order('id');
    setSettings(appSettings || {
      commission_rate: 0.03,
      farmer_response_hours: 12,
      buyer_payment_hours: 2,
      dispute_window_hours: 24,
    });
    setDistricts(dists || []);
    setLoading(false);
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleSaveSettings = async () => {
    setSaving(true);
    try {
      const { error } = await supabase
        .from('app_settings')
        .update({
          commission_rate: Number(settings.commission_rate),
          farmer_response_hours: Number(settings.farmer_response_hours),
          buyer_payment_hours: Number(settings.buyer_payment_hours),
          dispute_window_hours: Number(settings.dispute_window_hours),
          updated_at: new Date().toISOString(),
        })
        .eq('id', 1);

      if (error) throw error;
      alert('Application business settings updated successfully.');
    } catch (err: any) {
      alert(`Error updating settings: ${err.message}`);
    } finally {
      setSaving(false);
    }
  };

  const handleAddCity = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newCityName.trim()) return;

    setAddingCity(true);
    try {
      const { error } = await supabase.from('cities').insert({
        district_id: selectedDistrictId,
        name: newCityName.trim(),
        postal_code: newPostalCode.trim() || null,
      });

      if (error) throw error;
      alert(`City '${newCityName.trim()}' added successfully.`);
      setNewCityName('');
      setNewPostalCode('');
    } catch (err: any) {
      alert(`Failed to add city: ${err.message}`);
    } finally {
      setAddingCity(false);
    }
  };

  if (loading) {
    return <div className="text-slate-500">Loading settings...</div>;
  }

  return (
    <div className="space-y-8 max-w-4xl">
      <div>
        <h1 className="text-2xl font-bold text-slate-900 tracking-tight">System Parameters & Master Data</h1>
        <p className="text-sm text-slate-500">Configure escrow time limits, platform commission rates, and administrative cities</p>
      </div>

      {/* App Settings Form */}
      <div className="bg-white rounded-xl shadow-sm border border-slate-200 p-6 space-y-6">
        <h2 className="text-base font-bold text-slate-800 border-b pb-3">Core Marketplace Business Rules</h2>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div>
            <label className="block text-xs font-semibold text-slate-600 uppercase mb-1">
              Platform Commission Rate
            </label>
            <input
              type="number"
              step="0.001"
              value={settings.commission_rate}
              onChange={(e) => setSettings({ ...settings, commission_rate: e.target.value })}
              className="w-full border border-slate-300 rounded-lg p-2.5 text-sm focus:ring-2 focus:ring-green-600 focus:outline-none"
            />
            <p className="text-xs text-slate-400 mt-1">Default 0.030 (3% of crop subtotal only)</p>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-600 uppercase mb-1">
              Farmer Response Window (Hours)
            </label>
            <input
              type="number"
              value={settings.farmer_response_hours}
              onChange={(e) => setSettings({ ...settings, farmer_response_hours: e.target.value })}
              className="w-full border border-slate-300 rounded-lg p-2.5 text-sm focus:ring-2 focus:ring-green-600 focus:outline-none"
            />
            <p className="text-xs text-slate-400 mt-1">Time allowed for farmer to accept/reject before auto-expiry (Default: 12 hrs)</p>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-600 uppercase mb-1">
              Buyer Payment Window (Hours)
            </label>
            <input
              type="number"
              value={settings.buyer_payment_hours}
              onChange={(e) => setSettings({ ...settings, buyer_payment_hours: e.target.value })}
              className="w-full border border-slate-300 rounded-lg p-2.5 text-sm focus:ring-2 focus:ring-green-600 focus:outline-none"
            />
            <p className="text-xs text-slate-400 mt-1">Time allowed for buyer to complete payment before stock release (Default: 2 hrs)</p>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-600 uppercase mb-1">
              Dispute Resolution Window (Hours)
            </label>
            <input
              type="number"
              value={settings.dispute_window_hours}
              onChange={(e) => setSettings({ ...settings, dispute_window_hours: e.target.value })}
              className="w-full border border-slate-300 rounded-lg p-2.5 text-sm focus:ring-2 focus:ring-green-600 focus:outline-none"
            />
            <p className="text-xs text-slate-400 mt-1">Grace period after delivery before automatic order completion (Default: 24 hrs)</p>
          </div>
        </div>

        <button
          disabled={saving}
          onClick={handleSaveSettings}
          className="px-5 py-2.5 bg-green-700 hover:bg-green-600 text-white rounded-lg text-sm font-bold inline-flex items-center gap-2 shadow-sm disabled:opacity-50"
        >
          <Save className="w-4 h-4" />
          <span>{saving ? 'Updating Parameters...' : 'Save Configuration'}</span>
        </button>
      </div>

      {/* Manage Cities */}
      <div className="bg-white rounded-xl shadow-sm border border-slate-200 p-6 space-y-6">
        <h2 className="text-base font-bold text-slate-800 border-b pb-3">Add Administrative City / DS Town</h2>

        <form onSubmit={handleAddCity} className="grid grid-cols-1 md:grid-cols-3 gap-4 items-end">
          <div>
            <label className="block text-xs font-semibold text-slate-600 uppercase mb-1">District</label>
            <select
              value={selectedDistrictId}
              onChange={(e) => setSelectedDistrictId(Number(e.target.value))}
              className="w-full border border-slate-300 rounded-lg p-2.5 text-sm focus:ring-2 focus:ring-green-600 focus:outline-none bg-white"
            >
              {districts.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name} ({d.province})
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-600 uppercase mb-1">Town / Division Name</label>
            <input
              type="text"
              required
              placeholder="e.g. Kadawatha"
              value={newCityName}
              onChange={(e) => setNewCityName(e.target.value)}
              className="w-full border border-slate-300 rounded-lg p-2.5 text-sm focus:ring-2 focus:ring-green-600 focus:outline-none"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-600 uppercase mb-1">Postal Code (Optional)</label>
            <input
              type="text"
              placeholder="e.g. 11850"
              value={newPostalCode}
              onChange={(e) => setNewPostalCode(e.target.value)}
              className="w-full border border-slate-300 rounded-lg p-2.5 text-sm focus:ring-2 focus:ring-green-600 focus:outline-none"
            />
          </div>

          <div className="md:col-span-3">
            <button
              type="submit"
              disabled={addingCity || !newCityName.trim()}
              className="px-4 py-2 bg-slate-900 hover:bg-slate-800 text-white rounded-lg text-sm font-semibold inline-flex items-center gap-2 disabled:opacity-50"
            >
              <Plus className="w-4 h-4" />
              <span>{addingCity ? 'Adding...' : 'Add Town to District'}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
