import React, { useEffect, useMemo, useState } from 'react';
import { supabase } from '../../config/supabaseClient';

export default function CopyPricesModal({ open, onClose, sourceStoreId, sourceStoreName }) {
  const [step, setStep] = useState('select'); // select -> code -> review
  const [code, setCode] = useState('');
  const [validDest, setValidDest] = useState(null); // { id, name }
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [toast, setToast] = useState({ visible: false, message: '', kind: 'success' });

  const [sourceProducts, setSourceProducts] = useState([]);
  const [search, setSearch] = useState('');
  const [selected, setSelected] = useState(new Set());
  const [collapsedCategories, setCollapsedCategories] = useState(new Set());

  const [previewRows, setPreviewRows] = useState([]); // { product_id, name, source_price, dest_price, action }
  const [applyLoading, setApplyLoading] = useState(false);

  useEffect(() => {
    if (!open) {
      setStep('select');
      setCode('');
      setValidDest(null);
      setLoading(false);
      setError('');
      setSourceProducts([]);
      setSearch('');
      setSelected(new Set());
      setPreviewRows([]);
      setApplyLoading(false);
      setToast({ visible: false, message: '', kind: 'success' });
    }
  }, [open]);

  const grouped = useMemo(() => {
    const map = new Map();
    for (const p of sourceProducts) {
      const cat = p.category || 'Uncategorized';
      if (!map.has(cat)) map.set(cat, []);
      map.get(cat).push(p);
    }
    return Array.from(map.entries()).map(([name, products]) => ({ name, count: products.length, products }));
  }, [sourceProducts]);

  const filteredGrouped = useMemo(() => {
    if (!search) return grouped;
    const q = search.toLowerCase();
    return grouped.map(g => ({
      ...g,
      products: g.products.filter(p => (p.name || '').toLowerCase().includes(q))
    })).filter(g => g.products.length > 0);
  }, [grouped, search]);

  const toggleProduct = (id) => {
    const next = new Set(selected);
    if (next.has(id)) next.delete(id); else next.add(id);
    setSelected(next);
  };

  const totalProducts = useMemo(() => sourceProducts.length, [sourceProducts]);
  const allSelected = selected.size > 0 && selected.size === totalProducts;
  const toggleSelectAll = () => {
    if (allSelected) {
      setSelected(new Set());
    } else {
      setSelected(new Set(sourceProducts.map(p => p.product_id)));
    }
  };

  const isCategoryAllSelected = (g) => {
    if (!g.products.length) return false;
    for (const p of g.products) {
      if (!selected.has(p.product_id)) return false;
    }
    return true;
  };

  const toggleCategory = (g, checked) => {
    const next = new Set(selected);
    for (const p of g.products) {
      if (checked) next.add(p.product_id); else next.delete(p.product_id);
    }
    setSelected(next);
  };

  const toggleCollapse = (name) => {
    const next = new Set(collapsedCategories);
    if (next.has(name)) next.delete(name); else next.add(name);
    setCollapsedCategories(next);
  };

  // Load source products when opened
  useEffect(() => {
    const load = async () => {
      if (!open || !sourceStoreId) return;
      try {
        const { data, error } = await supabase.rpc('get_store_products', { p_store_id: sourceStoreId });
        if (error) throw error;
        setSourceProducts(Array.isArray(data) ? data : []);
      } catch (e) {
        setError(e.message || 'Failed to load products from source store.');
      }
    };
    load();
  }, [open, sourceStoreId]);

  // Real-time validation of destination paste-code: when 6 digits entered
  useEffect(() => {
    const run = async () => {
      if (code.length !== 6) return;
      setError('');
      setLoading(true);
      try {
        const { data, error: rpcErr } = await supabase.rpc('validate_paste_code', { p_code: code });
        if (rpcErr) throw rpcErr;
        const row = Array.isArray(data) && data.length ? data[0] : null;
        if (!row) {
          setError('Invalid or expired code. Ask the destination store owner to regenerate.');
          setValidDest(null);
        } else {
          setValidDest({ id: row.store_id, name: row.store_name });
        }
      } catch (e) {
        setError(e.message || 'Failed to validate.');
      } finally {
        setLoading(false);
      }
    };
    run();
  }, [code]);

  const proceedToReview = async () => {
    if (!validDest) return;
    await runDryRun();
  };

  const runDryRun = async () => {
    setError('');
    setLoading(true);
    try {
      const items = Array.from(selected);
      const { data, error: rpcErr } = await supabase.rpc('copy_prices', {
        p_source_store_id: sourceStoreId,
        p_dest_paste_code: code,
        p_items: items,
        p_dry_run: true
      });
      if (rpcErr) throw rpcErr;
      setPreviewRows(Array.isArray(data) ? data : []);
      setStep('review');
    } catch (e) {
      setError(e.message || 'Failed to compute dry-run.');
    } finally {
      setLoading(false);
    }
  };

  const applyCopy = async () => {
    setError('');
    setApplyLoading(true);
    try {
      const items = Array.from(selected);
      const { data, error: rpcErr } = await supabase.rpc('copy_prices', {
        p_source_store_id: sourceStoreId,
        p_dest_paste_code: code,
        p_items: items,
        p_dry_run: false
      });
      if (rpcErr) throw rpcErr;
      // Compute counts
      const created = (data || []).filter(r => r.action === 'create').length;
      const updated = (data || []).filter(r => r.action === 'update').length;
      setToast({ visible: true, message: `Copied: ${updated} updated, ${created} created`, kind: 'success' });
      setTimeout(() => {
        setToast({ visible: false, message: '', kind: 'success' });
        onClose?.();
      }, 1600);
    } catch (e) {
      setError(e.message || 'Copy failed. No changes were applied.');
    } finally {
      setApplyLoading(false);
    }
  };

  if (!open) return null;

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.35)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 2000 }}>
      <div style={{ width: 'min(900px, 96vw)', background: 'white', borderRadius: '16px', boxShadow: '0 10px 30px rgba(0,0,0,0.2)', overflow: 'hidden', position: 'relative' }}>
        {/* Toast Popup */}
        {toast.visible && (
          <div style={{ position: 'absolute', bottom: 12, left: '50%', transform: 'translateX(-50%)', background: '#2f6312', color: 'white', padding: '8px 10px', borderRadius: 6, fontWeight: 600 }}>
            {toast.message}
          </div>
        )}
        {/* Header */}
        <div style={{ padding: '16px 20px', borderBottom: '1px solid #eee', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div>
            <div style={{ fontSize: '1.1rem', fontWeight: 700 }}>Copy Prices</div>
            <div style={{ color: '#666', fontSize: '0.9rem' }}>Source: {sourceStoreName || 'Store'}{validDest ? `  ·  Destination: ${validDest.name}` : ''}</div>
          </div>
          <button onClick={onClose} style={{ border: 'none', background: 'transparent', fontSize: '1rem', color: '#333', cursor: 'pointer' }}>✕</button>
        </div>

        {/* Stepper indicator */}
        <div style={{ display: 'flex', gap: 8, padding: '12px 20px', borderBottom: '1px solid #f2f2f2' }}>
          {[
            { key: 'select', label: 'Select Items' },
            { key: 'code', label: 'Enter Paste-Code' },
            { key: 'review', label: 'Review & Execute' }
          ].map((s, i) => (
            <div key={s.key} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <div style={{ width: 26, height: 26, borderRadius: 13, background: step === s.key ? '#ff8c00' : '#ffd8ae', color: step === s.key ? 'white' : '#7a4a12', display: 'grid', placeItems: 'center', fontWeight: 700 }}>{i+1}</div>
              <div style={{ color: step === s.key ? '#ff8c00' : '#999', fontWeight: 600, fontSize: '0.9rem' }}>{s.label}</div>
            </div>
          ))}
        </div>

        {/* Body */}
        <div style={{ padding: 20 }}>
          {error && (
            <div style={{ marginBottom: 12, background: '#fff4f4', color: '#b00020', padding: '10px 12px', borderRadius: 8, border: '1px solid #ffd6d6' }}>{error}</div>
          )}

          {step === 'code' && (
            <div style={{ display: 'grid', gap: 12 }}>
              <div style={{ color: '#555' }}>Enter the 6-digit paste-code.</div>
              <input
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                placeholder="Paste-code"
                style={{ padding: '12px 14px', borderRadius: 10, border: '1px solid #ddd', fontSize: '1.2rem', letterSpacing: 2, width: 160 }}
              />
              <div style={{ display: 'flex', gap: 10 }}>
                <button disabled={!validDest || loading} onClick={proceedToReview} style={{ padding: '10px 14px', borderRadius: 10, border: 'none', background: !validDest || loading ? '#ffd8ae' : '#ff8c00', color: 'white', fontWeight: 700, cursor: !validDest || loading ? 'not-allowed' : 'pointer' }}>Next</button>
                <button onClick={onClose} style={{ padding: '10px 14px', borderRadius: 10, border: '1px solid #eee', background: 'white', color: '#333', fontWeight: 600, cursor: 'pointer' }}>Cancel</button>
              </div>
              {validDest && (
                <div style={{ marginTop: 8, background: '#f7fff2', border: '1px solid #cde3b0', color: '#2f6312', padding: '8px 10px', borderRadius: 8 }}>
                  Destination store: {validDest.name} (verified)
                </div>
              )}
            </div>
          )}

          {step === 'select' && (
            <div style={{ display: 'grid', gap: 12 }}>
              <div style={{ marginBottom: 8, background: '#f7fbff', color: '#0b4f6c', padding: '8px 10px', borderRadius: 8, border: '1px solid #dbeafe' }}>
                Select items to copy from your store. Enter the destination paste-code next.
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search products" style={{ flex: 1, padding: '10px 12px', borderRadius: 10, border: '1px solid #ddd' }} />
                <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', userSelect: 'none' }}>
                  <input type="checkbox" checked={allSelected} onChange={toggleSelectAll} />
                  <span style={{ color: '#555' }}>Select all Pricelist ({totalProducts})</span>
                </label>
              </div>
              <div style={{ maxHeight: 360, overflow: 'auto', border: '1px solid #eee', borderRadius: 10 }}>
                {filteredGrouped.map(g => (
                  <div key={g.name} style={{ borderBottom: '1px solid #f6f6f6' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '10px 12px' }}>
                      <button onClick={() => toggleCollapse(g.name)} style={{ border: 'none', background: 'transparent', cursor: 'pointer', color: '#7a4a12' }}>{collapsedCategories.has(g.name) ? '▸' : '▾'}</button>
                      <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', userSelect: 'none', flex: 1 }}>
                        <input type="checkbox" checked={isCategoryAllSelected(g)} onChange={(e) => toggleCategory(g, e.target.checked)} />
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                          <span style={{ fontWeight: 700 }}>{g.name}</span>
                          <span style={{ color: '#777' }}>({g.count})</span>
                        </div>
                      </label>
                    </div>
                    {!collapsedCategories.has(g.name) && (
                      <div>
                        {g.products.map(p => (
                          <label key={p.product_id} style={{ display: 'grid', gridTemplateColumns: '24px 1fr 100px', alignItems: 'center', padding: '8px 44px', borderTop: '1px dashed #f0f0f0', gap: 12 }}>
                            <input type="checkbox" checked={selected.has(p.product_id)} onChange={() => toggleProduct(p.product_id)} />
                            <div>
                              <div style={{ fontWeight: 700 }}>{p.name}</div>
                              <div style={{ color: '#777', fontSize: '0.85rem' }}>{g.name} · {p.units || p.unit || ''}</div>
                            </div>
                            <div style={{ textAlign: 'right', color: '#333', fontWeight: 700 }}>₱{Number(p.price || 0).toFixed(2)}</div>
                          </label>
                        ))}
                        {g.products.length === 0 && (
                          <div style={{ padding: '8px 44px', color: '#777' }}>No products matching search.</div>
                        )}
                      </div>
                    )}
                  </div>
                ))}
                {filteredGrouped.length === 0 && (
                  <div style={{ padding: 20, color: '#777', textAlign: 'center' }}>No products found.</div>
                )}
              </div>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', color: '#555' }}>
                <div>Selected: {Array.from(selected).length} items · Categories fully selected: {filteredGrouped.filter(g => isCategoryAllSelected(g)).length}</div>
                <div style={{ display: 'flex', gap: 10 }}>
                  <button
                    onClick={() => setStep('code')}
                    disabled={selected.size === 0 || loading}
                    style={{
                      padding: '10px 14px',
                      borderRadius: 10,
                      border: 'none',
                      background: selected.size === 0 || loading ? '#ffd8ae' : '#ff8c00',
                      color: 'white',
                      fontWeight: 700,
                      cursor: selected.size === 0 || loading ? 'not-allowed' : 'pointer'
                    }}
                  >Next</button>
                </div>
              </div>
            </div>
          )}

          {step === 'review' && (
            <div style={{ display: 'grid', gap: 12 }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', background: '#fafafa', border: '1px solid #eee', padding: '10px 12px', borderRadius: 10 }}>
                <div>
                  To create: {(previewRows || []).filter(r => r.action === 'create').length} · To update: {(previewRows || []).filter(r => r.action === 'update').length} · Total selected: {Array.from(selected).length}
                </div>
                <div title="Existing destination prices are overwritten (forced overwrite)." style={{ color: '#7a4a12' }}>Overwrite existing prices (forced)</div>
              </div>
              <div style={{ border: '1px solid #eee', borderRadius: 10, overflow: 'hidden' }}>
                <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr 1fr 100px', padding: '10px 12px', background: '#fafafa', borderBottom: '1px solid #eee', fontWeight: 700 }}>
                  <div>Product</div>
                  <div style={{ textAlign: 'right' }}>Source Price</div>
                  <div style={{ textAlign: 'right' }}>Destination Price</div>
                  <div style={{ textAlign: 'right' }}>Result</div>
                </div>
                <div style={{ maxHeight: 360, overflow: 'auto' }}>
                  {previewRows.map(r => (
                    <div key={r.product_id} style={{ display: 'grid', gridTemplateColumns: '2fr 1fr 1fr 100px', padding: '10px 12px', borderBottom: '1px solid #f6f6f6' }}>
                      <div style={{ fontWeight: 700 }}>{r.name}</div>
                      <div style={{ textAlign: 'right' }}>₱{Number(r.source_price || 0).toFixed(2)}</div>
                      <div style={{ textAlign: 'right' }}>{r.dest_price == null ? '—' : `₱${Number(r.dest_price).toFixed(2)}`}</div>
                      <div style={{ textAlign: 'right', color: r.action === 'create' ? '#2f6312' : '#7a4a12', fontWeight: 700 }}>{r.action === 'create' ? 'Create' : 'Update'}</div>
                    </div>
                  ))}
                  {previewRows.length === 0 && (
                    <div style={{ padding: 20, color: '#777', textAlign: 'center' }}>No items selected.</div>
                  )}
                </div>
              </div>
              <div style={{ display: 'flex', gap: 10, justifyContent: 'space-between' }}>
                <button onClick={() => setStep('select')} style={{ padding: '10px 14px', borderRadius: 10, border: '1px solid #eee', background: 'white', color: '#333', fontWeight: 600, cursor: 'pointer' }}>Back</button>
                <button disabled={applyLoading || previewRows.length === 0} onClick={applyCopy} style={{ padding: '10px 14px', borderRadius: 10, border: 'none', background: previewRows.length === 0 ? '#ffd8ae' : '#ff8c00', color: 'white', fontWeight: 700, cursor: previewRows.length === 0 ? 'not-allowed' : 'pointer' }}>Confirm & Copy</button>
              </div>
            </div>
          )}
        </div>

        {/* Footer hint */}
        <div style={{ padding: '12px 20px', borderTop: '1px solid #f2f2f2', color: '#777', fontSize: '0.85rem' }}>
          Dry-run shows changes before applying. Copy operation overwrites destination prices.
        </div>
      </div>
    </div>
  );
}