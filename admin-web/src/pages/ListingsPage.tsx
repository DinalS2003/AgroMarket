import React, { useEffect, useState } from 'react';
import { supabase } from '../lib/supabase';
import { Search, Eye, EyeOff, Image as ImageIcon, X } from 'lucide-react';

export const ListingsPage: React.FC = () => {
  const [listings, setListings] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [previewPhotos, setPreviewPhotos] = useState<{ title: string; photos: string[] } | null>(null);

  const loadListings = async () => {
    setLoading(true);
    const { data } = await supabase
      .from('listings')
      .select(`
        *,
        farmer:profiles!listings_farmer_id_fkey(full_name)
      `)
      .order('created_at', { ascending: false });

    setListings(data || []);
    setLoading(false);
  };

  useEffect(() => {
    loadListings();
  }, []);

  const toggleActive = async (id: string, current: boolean) => {
    await supabase.from('listings').update({ is_active: !current }).eq('id', id);
    loadListings();
  };

  const filtered = listings.filter((l) => {
    const q = search.toLowerCase();
    return l.crop_name?.toLowerCase().includes(q) || l.farmer?.full_name?.toLowerCase().includes(q);
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Harvest Listings Management</h1>
          <p className="text-sm text-slate-500">Monitor active crops, inspect crop photos, stock levels, and moderate listings</p>
        </div>

        <div className="relative w-72">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 top-3" />
          <input
            type="text"
            placeholder="Search crop or farmer..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full bg-white border border-slate-300 rounded-lg pl-9 pr-4 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-green-600"
          />
        </div>
      </div>

      <div className="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-sm">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                <th className="py-3 px-4">Photo</th>
                <th className="py-3 px-4">Crop Name</th>
                <th className="py-3 px-4">Farmer</th>
                <th className="py-3 px-4">Price (LKR/kg)</th>
                <th className="py-3 px-4">Available Qty</th>
                <th className="py-3 px-4">Harvest Date</th>
                <th className="py-3 px-4">Status</th>
                <th className="py-3 px-4 text-right">Moderation</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200">
              {loading ? (
                <tr>
                  <td colSpan={8} className="py-8 text-center text-slate-500">
                    Loading harvest listings...
                  </td>
                </tr>
              ) : filtered.length === 0 ? (
                <tr>
                  <td colSpan={8} className="py-8 text-center text-slate-500">
                    No listings found.
                  </td>
                </tr>
              ) : (
                filtered.map((l) => {
                  const hasPhotos = Array.isArray(l.photos) && l.photos.length > 0;
                  const firstPhoto = hasPhotos ? l.photos[0] : null;

                  return (
                    <tr key={l.id} className="hover:bg-slate-50 transition-colors">
                      <td className="py-3 px-4">
                        {firstPhoto ? (
                          <div
                            onClick={() => setPreviewPhotos({ title: l.crop_name, photos: l.photos })}
                            className="relative w-12 h-12 rounded-lg overflow-hidden cursor-pointer border border-slate-200 hover:opacity-80 transition-opacity"
                          >
                            <img src={firstPhoto} alt={l.crop_name} className="w-full h-full object-cover" />
                            {l.photos.length > 1 && (
                              <span className="absolute bottom-0 right-0 bg-black/70 text-white text-[9px] font-bold px-1 rounded-tl">
                                +{l.photos.length - 1}
                              </span>
                            )}
                          </div>
                        ) : (
                          <div className="w-12 h-12 rounded-lg bg-emerald-50 border border-emerald-100 flex items-center justify-center text-emerald-600">
                            <ImageIcon className="w-5 h-5 opacity-60" />
                          </div>
                        )}
                      </td>
                      <td className="py-3 px-4 font-bold text-slate-900">{l.crop_name}</td>
                      <td className="py-3 px-4 text-slate-700">{l.farmer?.full_name}</td>
                      <td className="py-3 px-4 font-semibold text-slate-900">Rs. {Number(l.price_per_kg).toFixed(2)}</td>
                      <td className="py-3 px-4">{l.quantity_available} kg</td>
                      <td className="py-3 px-4 text-xs text-slate-600">{l.harvest_date}</td>
                      <td className="py-3 px-4">
                        {l.is_active && l.quantity_available > 0 ? (
                          <span className="px-2 py-0.5 rounded-full text-xs font-bold bg-green-100 text-green-800">
                            Active
                          </span>
                        ) : (
                          <span className="px-2 py-0.5 rounded-full text-xs font-bold bg-slate-200 text-slate-700">
                            Hidden / Inactive
                          </span>
                        )}
                      </td>
                      <td className="py-3 px-4 text-right">
                        <button
                          onClick={() => toggleActive(l.id, l.is_active)}
                          className={`px-3 py-1.5 rounded text-xs font-semibold inline-flex items-center gap-1.5 ${
                            l.is_active
                              ? 'bg-amber-100 text-amber-800 hover:bg-amber-200'
                              : 'bg-green-100 text-green-800 hover:bg-green-200'
                          }`}
                        >
                          {l.is_active ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                          <span>{l.is_active ? 'Hide Listing' : 'Unhide'}</span>
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

      {/* Image Preview Modal */}
      {previewPhotos && (
        <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-2xl w-full p-6 space-y-4 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between">
              <h3 className="text-lg font-bold text-slate-900">{previewPhotos.title} - Harvest Photos</h3>
              <button
                onClick={() => setPreviewPhotos(null)}
                className="text-slate-400 hover:text-slate-600 p-1 rounded-lg"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              {previewPhotos.photos.map((src, idx) => (
                <div key={idx} className="relative rounded-xl overflow-hidden border border-slate-200 bg-slate-100 aspect-video">
                  <img src={src} alt={`Crop photo ${idx + 1}`} className="w-full h-full object-cover" />
                  <span className="absolute top-2 left-2 bg-black/60 text-white text-xs px-2 py-0.5 rounded font-medium">
                    {idx === 0 ? 'Cover Photo' : `Photo ${idx + 1}`}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
