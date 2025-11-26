import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { supabase } from '../config/supabaseClient';
import StoreHeader from '../components/layout/StoreHeader';
import iconStore from '../assets/icon_store.png';
import addStaffIcon from '../assets/icon_add_staff.png';
import InviteStaffModal from '../components/store/InviteStaffModal';
import ConfirmModal from '../components/store/ConfirmModal';
import ExportPricelistModal from '../components/store/ExportPricelistModal';
import CopyPricesModal from '../components/store/CopyPricesModal';
import ImportPricesModal from '../components/store/ImportPricesModal';

// Store Settings Page
// Uses the provided layout, replaces dummy data with real store/member data,
// avoids alerts, and wires the Manage Items tab to the ManageItemsPage.
export default function StoreSettingsPage() {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState('store');
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
  const [isMobile, setIsMobile] = useState(() => window.innerWidth <= 768);
  const [stores, setStores] = useState([]);

  const [storeId, setStoreId] = useState(null);
  const [storeName, setStoreName] = useState('');
  const [branch, setBranch] = useState('');
  const [storeType, setStoreType] = useState('');
  const [role, setRole] = useState(null);

  const [members, setMembers] = useState([]);
  const [actionStatus, setActionStatus] = useState({ kind: null, text: '' });
  const [inviteOpen, setInviteOpen] = useState(false);
  const [memberOptions, setMemberOptions] = useState(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [confirmConfig, setConfirmConfig] = useState({ title: '', message: '', action: null, confirmLabel: 'Confirm' });
  const [exportOpen, setExportOpen] = useState(false);
  const [copyOpen, setCopyOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [pasteCode, setPasteCode] = useState(null);
  const [pasteCodeExpiresAt, setPasteCodeExpiresAt] = useState(null);
  const [codeCountdown, setCodeCountdown] = useState('');

  // Resolve storeId from query and load data
  useEffect(() => {
    const id = new URLSearchParams(window.location.search).get('id');
    setStoreId(id);

    const init = async () => {
      const { data: { session } } = await supabase.auth.getSession();
      if (!session) {
        navigate('/login', { replace: true });
        return;
      }
      // If no store id in query, return to Stores list
      if (!id) {
        navigate('/stores', { replace: true });
        return;
      }

      try {
        // Load stores for StoreHeader side-menu
        try {
          const { data, error } = await supabase.rpc('get_user_stores');
          if (!error) {
            const rows = Array.isArray(data) ? data : [];
            const mapped = rows.map(r => ({ id: r.store_id, name: r.name, branch: r.branch || '' }));
            setStores(mapped);
          }
        } catch (e) {
          console.warn('Failed to load stores for header:', e);
        }

        if (id) {
          // Get store core info
          const { data: storeRow, error: storeErr } = await supabase
            .from('stores')
            .select('id, name, branch, type, paste_code, paste_code_expires_at')
            .eq('id', id)
            .maybeSingle();
          if (!storeErr && storeRow) {
            setStoreName(storeRow.name || '');
            setBranch(storeRow.branch || '');
            setStoreType(storeRow.type || '');
            setPasteCode(storeRow.paste_code || null);
            setPasteCodeExpiresAt(storeRow.paste_code_expires_at || null);
          }

          // Get current user's role for this store
          const { data: myMembership } = await supabase
            .from('store_members')
            .select('role')
            .eq('store_id', id)
            .eq('user_id', session.user.id)
            .maybeSingle();
          setRole(myMembership?.role || null);

          // Fetch members via schema-aware RPC
          try {
            const { data: memRows, error: memErr } = await supabase.rpc('get_store_members', { p_store_id: id });
            if (!memErr) {
              const enriched = (memRows || []).map(m => ({
                id: m.user_id,
                role: m.role,
                name: m.name || (m.email ? m.email.split('@')[0] : 'User'),
                email: m.email || ''
              }));
              setMembers(enriched);
            }
          } catch (e) {
            console.warn('Failed to load members via RPC:', e);
          }
        }
      } catch (e) {
        console.warn('Failed to initialize StoreSettingsPage', e);
      }
    };
    init();
  }, [navigate]);

  // Track viewport size to show hamburger only on mobile
  useEffect(() => {
    const handleResize = () => setIsMobile(window.innerWidth <= 768);
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  const handleSave = async () => {
    if (!storeId) return;
    setActionStatus({ kind: 'info', text: 'Saving changes…' });
    try {
      const { error } = await supabase
        .from('stores')
        .update({ name: storeName, branch, type: storeType })
        .eq('id', storeId);
      if (error) throw error;
      setActionStatus({ kind: 'success', text: 'Store settings updated.' });
      setTimeout(() => setActionStatus({ kind: null, text: '' }), 3000);
    } catch (e) {
      setActionStatus({ kind: 'error', text: e.message || 'Failed to save store settings.' });
      setTimeout(() => setActionStatus({ kind: null, text: '' }), 4000);
    }
  };

  // Paste-Code countdown updater
  useEffect(() => {
    let timer;
    const updateCountdown = () => {
      if (!pasteCodeExpiresAt) {
        setCodeCountdown('');
        return;
      }
      const now = Date.now();
      const exp = new Date(pasteCodeExpiresAt).getTime();
      const diff = Math.max(0, exp - now);
      const hh = Math.floor(diff / 3600000);
      const mm = Math.floor((diff % 3600000) / 60000);
      const ss = Math.floor((diff % 60000) / 1000);
      const pad = (n) => String(n).padStart(2, '0');
      setCodeCountdown(`${pad(hh)}:${pad(mm)}:${pad(ss)}`);
    };
    updateCountdown();
    if (pasteCodeExpiresAt) {
      timer = setInterval(updateCountdown, 1000);
    }
    return () => timer && clearInterval(timer);
  }, [pasteCodeExpiresAt]);

  const generatePasteCode = async () => {
    if (!storeId) return;
    if (role !== 'owner') {
      setActionStatus({ kind: 'error', text: 'Only store owners can generate codes.' });
      setTimeout(() => setActionStatus({ kind: null, text: '' }), 3000);
      return;
    }
    setActionStatus({ kind: 'info', text: 'Generating code…' });
    const { data, error } = await supabase.rpc('generate_paste_code', { p_store_id: storeId });
    if (error) {
      setActionStatus({ kind: 'error', text: error.message || 'Failed to generate code.' });
    } else {
      const row = Array.isArray(data) && data.length ? data[0] : null;
      // Use correct field names returned by RPC: code, expires_at
      setPasteCode(row?.code || null);
      setPasteCodeExpiresAt(row?.expires_at || null);
      setActionStatus({ kind: 'success', text: 'Paste-code generated.' });
      setTimeout(() => setActionStatus({ kind: null, text: '' }), 2500);
    }
  };

  const copyPasteCode = async () => {
    if (!pasteCode) return;
    try {
      await navigator.clipboard?.writeText(String(pasteCode));
      setActionStatus({ kind: 'success', text: 'Code copied.' });
      setTimeout(() => setActionStatus({ kind: null, text: '' }), 1500);
    } catch (e) {
      setActionStatus({ kind: 'error', text: 'Failed to copy.' });
      setTimeout(() => setActionStatus({ kind: null, text: '' }), 2000);
    }
  };

  const revokePasteCode = async () => {
    if (!storeId) return;
    if (role !== 'owner') {
      setActionStatus({ kind: 'error', text: 'Only store owners can revoke codes.' });
      setTimeout(() => setActionStatus({ kind: null, text: '' }), 3000);
      return;
    }
    setActionStatus({ kind: 'info', text: 'Revoking code…' });
    const { error } = await supabase.rpc('revoke_paste_code', { p_store_id: storeId });
    if (error) {
      setActionStatus({ kind: 'error', text: error.message || 'Failed to revoke code.' });
    } else {
      setPasteCode(null);
      setPasteCodeExpiresAt(null);
      setActionStatus({ kind: 'success', text: 'Paste-code revoked.' });
      setTimeout(() => setActionStatus({ kind: null, text: '' }), 2500);
    }
  };

  const tabs = useMemo(() => ([
    { id: 'store', label: 'Manage store' },
    { id: 'members', label: 'Manage members' },
    { id: 'items', label: 'Manage Items' }
  ]), []);

  const goBack = () => {
    if (storeId) navigate(`/store?id=${encodeURIComponent(storeId)}`);
    else navigate('/stores');
  };

  const manageItems = () => {
    if (!storeId) return;
    navigate(`/manage-items?id=${encodeURIComponent(storeId)}`);
  };

  return (
    <div style={{ 
      minHeight: '100vh', 
      background: '#f5f5f5', 
      fontFamily: 'system-ui, -apple-system, sans-serif',
      display: 'flex',
      flexDirection: 'column'
    }}>
      {/* Top Header: sticky to top */}
      <div style={{ position: 'sticky', top: 0, zIndex: 1000 }}>
        <StoreHeader stores={stores} includeAllStoresLink={false} />
      </div>

      {/* Store Settings subheader with back button (do not remove) */}
      <div style={{
        background: 'white',
        padding: '15px 20px',
        borderBottom: '1px solid #e0e0e0',
        position: 'sticky',
        top: '60px',
        zIndex: 900
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '15px' }}>
          <button onClick={goBack} style={{
            background: 'transparent',
            border: 'none',
            cursor: 'pointer',
            padding: '5px',
            color: '#ff8c00'
          }}>
            <svg width="24" height="24" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 19l-7-7 7-7"/>
            </svg>
          </button>
          <h1 style={{ fontSize: '1.3rem', fontWeight: '700', margin: 0, color: '#ff8c00' }}>
            Store Settings
          </h1>
          {/* StoreHeader handles hamburger and notifications */}
        </div>
      </div>

      {/* Mobile Sidebar Overlay */}
      {/* Side-menu overlay provided by StoreHeader */}

      {/* Main Content - fullscreen, responsive */}
      <div style={{ 
        display: 'flex',
        gap: 0,
        minHeight: 'calc(100vh - 140px)',
        flex: 1
      }}>
        {/* Side Navigation */}
        <div 
          className="sidebar"
          style={{ 
            width: '280px',
            background: 'white',
            borderRight: '1px solid #e0e0e0',
            padding: '20px',
            position: 'sticky',
            top: '140px',
            height: 'fit-content',
            maxHeight: 'calc(100vh - 140px)',
            overflowY: 'auto'
          }}>
          <h3 style={{ 
            fontSize: '0.75rem', 
            fontWeight: '700', 
            color: '#999', 
            letterSpacing: '1px',
            marginBottom: '15px',
            paddingLeft: '5px'
          }}>
            NAVIGATION
          </h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            {tabs.map((tab) => {
              const isSelected = activeTab === tab.id;
              return (
                <button
                  key={tab.id}
                  onClick={() => {
                    if (tab.id === 'items') {
                      manageItems();
                      return;
                    }
                    setActiveTab(tab.id);
                    setMobileSidebarOpen(false);
                  }}
                  onMouseEnter={(e) => {
                    if (!isSelected) {
                      e.currentTarget.style.background = 'linear-gradient(135deg, #e0f7fa 0%, #b2ebf2 100%)';
                    }
                  }}
                  onMouseLeave={(e) => {
                    if (!isSelected) {
                      e.currentTarget.style.background = 'white';
                    }
                  }}
                  style={{
                    padding: '14px 18px',
                    border: 'none',
                    borderRadius: '25px',
                    background: isSelected ? '#00bcd4' : 'white',
                    color: isSelected ? 'white' : '#555',
                    fontSize: '0.9rem',
                    fontWeight: isSelected ? '600' : '500',
                    cursor: 'pointer',
                    textAlign: 'left',
                    transition: 'all 0.3s ease',
                    boxShadow: isSelected ? 
                      '0 4px 12px rgba(0, 188, 212, 0.3)' : 
                      '0 1px 3px rgba(0,0,0,0.05)',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '10px'
                  }}
                >
                  {tab.label}
                </button>
              );
            })}
          </div>
        </div>

        {/* Content Area */}
        <div style={{ flex: 1, padding: '20px' }}>
          <div style={{
            background: 'white',
            borderRadius: '20px',
            padding: '30px',
            boxShadow: '0 4px 15px rgba(0, 0, 0, 0.1)'
          }}>
            {/* Manage Store Tab */}
            {activeTab === 'store' && (
              <div>
                {/* Store Icon (orange, no background) */}
                <div style={{ display: 'flex', justifyContent: 'center', marginBottom: '20px' }}>
                  <img src={iconStore} alt="Store" style={{ width: '72px', height: '72px', filter: 'none' }} />
                </div>
                {/* 4 Action Buttons */}
                <div style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))',
                  gap: '15px',
                  marginBottom: '30px'
                }}>
                  <button
                    onMouseEnter={(e) => {
                      if (e.currentTarget.disabled) return;
                      e.currentTarget.style.transform = 'translateY(-1px)';
                      e.currentTarget.style.boxShadow = '0 6px 12px rgba(255,140,0,0.18)';
                    }}
                    onMouseLeave={(e) => {
                      e.currentTarget.style.transform = 'none';
                      e.currentTarget.style.boxShadow = '0 1px 3px rgba(0,0,0,0.06)';
                    }}
                    onMouseDown={(e) => {
                      if (e.currentTarget.disabled) return;
                      e.currentTarget.style.transform = 'translateY(0) scale(0.98)';
                      e.currentTarget.style.boxShadow = 'inset 0 2px 6px rgba(0,0,0,0.08)';
                    }}
                    onMouseUp={(e) => {
                      if (e.currentTarget.disabled) return;
                      e.currentTarget.style.transform = 'translateY(-1px)';
                      e.currentTarget.style.boxShadow = '0 6px 12px rgba(255,140,0,0.18)';
                    }}
                    style={{
                    padding: '15px',
                    border: '2px solid #ff8c00',
                    borderRadius: '12px',
                    background: 'white',
                    color: '#ff8c00',
                    fontSize: '0.9rem',
                    fontWeight: '600',
                    cursor: 'pointer',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    gap: '8px',
                    transition: 'all 0.2s ease',
                    boxShadow: '0 1px 3px rgba(0,0,0,0.06)'
                  }} onClick={() => setCopyOpen(true)} disabled={role !== 'owner'}>
                    <svg width="28" height="28" fill="#ff8c00" viewBox="0 0 24 24">
                      <path d="M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z"/>
                    </svg>
                    Copy Prices
                  </button>
                  <button
                    onMouseEnter={(e) => {
                      if (e.currentTarget.disabled) return;
                      e.currentTarget.style.transform = 'translateY(-1px)';
                      e.currentTarget.style.boxShadow = '0 6px 12px rgba(255,140,0,0.18)';
                    }}
                    onMouseLeave={(e) => {
                      e.currentTarget.style.transform = 'none';
                      e.currentTarget.style.boxShadow = '0 1px 3px rgba(0,0,0,0.06)';
                    }}
                    onMouseDown={(e) => {
                      if (e.currentTarget.disabled) return;
                      e.currentTarget.style.transform = 'translateY(0) scale(0.98)';
                      e.currentTarget.style.boxShadow = 'inset 0 2px 6px rgba(0,0,0,0.08)';
                    }}
                    onMouseUp={(e) => {
                      if (e.currentTarget.disabled) return;
                      e.currentTarget.style.transform = 'translateY(-1px)';
                      e.currentTarget.style.boxShadow = '0 6px 12px rgba(255,140,0,0.18)';
                    }}
                    style={{
                    padding: '15px',
                    border: '2px solid #ff8c00',
                    borderRadius: '12px',
                    background: 'white',
                    color: '#ff8c00',
                    fontSize: '0.9rem',
                    fontWeight: '600',
                    cursor: 'pointer',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    gap: '8px',
                    transition: 'all 0.2s ease',
                    boxShadow: '0 1px 3px rgba(0,0,0,0.06)'
                  }} onClick={() => setInviteOpen(true)}>
                    <img src={addStaffIcon} alt="Invite staff" style={{ width: '28px', height: '28px' }} />
                    Invite staff
                  </button>
                  <button
                    onMouseEnter={(e) => {
                      if (e.currentTarget.disabled) return;
                      e.currentTarget.style.transform = 'translateY(-1px)';
                      e.currentTarget.style.boxShadow = '0 6px 12px rgba(255,140,0,0.18)';
                    }}
                    onMouseLeave={(e) => {
                      e.currentTarget.style.transform = 'none';
                      e.currentTarget.style.boxShadow = '0 1px 3px rgba(0,0,0,0.06)';
                    }}
                    onMouseDown={(e) => {
                      if (e.currentTarget.disabled) return;
                      e.currentTarget.style.transform = 'translateY(0) scale(0.98)';
                      e.currentTarget.style.boxShadow = 'inset 0 2px 6px rgba(0,0,0,0.08)';
                    }}
                    onMouseUp={(e) => {
                      if (e.currentTarget.disabled) return;
                      e.currentTarget.style.transform = 'translateY(-1px)';
                      e.currentTarget.style.boxShadow = '0 6px 12px rgba(255,140,0,0.18)';
                    }}
                    style={{
                    padding: '15px',
                    border: '2px solid #ff8c00',
                    borderRadius: '12px',
                    background: 'white',
                    color: '#ff8c00',
                    fontSize: '0.9rem',
                    fontWeight: '600',
                    cursor: 'pointer',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    gap: '8px',
                    transition: 'all 0.2s ease',
                    boxShadow: '0 1px 3px rgba(0,0,0,0.06)'
                  }} onClick={() => setExportOpen(true)}>
                    <svg width="28" height="28" fill="#ff8c00" viewBox="0 0 24 24">
                      <path d="M9 11H7v2h2v-2zm4 0h-2v2h2v-2zm4 0h-2v2h2v-2zm2-7h-1V2h-2v2H8V2H6v2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 16H5V9h14v11z"/>
                    </svg>
                    Convert
                  </button>
                  <button
                    onMouseEnter={(e) => {
                      if (e.currentTarget.disabled) return;
                      e.currentTarget.style.transform = 'translateY(-1px)';
                      e.currentTarget.style.boxShadow = '0 6px 12px rgba(255,140,0,0.18)';
                    }}
                    onMouseLeave={(e) => {
                      e.currentTarget.style.transform = 'none';
                      e.currentTarget.style.boxShadow = '0 1px 3px rgba(0,0,0,0.06)';
                    }}
                    onMouseDown={(e) => {
                      if (e.currentTarget.disabled) return;
                      e.currentTarget.style.transform = 'translateY(0) scale(0.98)';
                      e.currentTarget.style.boxShadow = 'inset 0 2px 6px rgba(0,0,0,0.08)';
                    }}
                    onMouseUp={(e) => {
                      if (e.currentTarget.disabled) return;
                      e.currentTarget.style.transform = 'translateY(-1px)';
                      e.currentTarget.style.boxShadow = '0 6px 12px rgba(255,140,0,0.18)';
                    }}
                    style={{
                    padding: '15px',
                    border: '2px solid #ff8c00',
                    borderRadius: '12px',
                    background: 'white',
                    color: '#ff8c00',
                    fontSize: '0.9rem',
                    fontWeight: '600',
                    cursor: 'pointer',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    gap: '8px',
                    transition: 'all 0.2s ease',
                    boxShadow: '0 1px 3px rgba(0,0,0,0.06)'
                  }} onClick={() => setImportOpen(true)} disabled={!(role === 'owner' || role === 'manager')}>
                    <svg width="28" height="28" fill="#ff8c00" viewBox="0 0 24 24">
                      <path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zM9 17H7v-7h2v7zm4 0h-2V7h2v10zm4 0h-2v-4h2v4z"/>
                    </svg>
                    Import prices
                  </button>
                </div>

                {/* Paste-Code generator */}
                <div style={{ marginTop: '16px', padding: '14px', border: '1px solid #eee', borderRadius: '12px', background: '#ffffff', marginBottom: '14px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
                    <div style={{ fontWeight: 700, color: '#7a4a12' }}>Paste-Code (for copying prices to this store)</div>
                    {pasteCode ? (
                      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                        <div style={{ fontWeight: 700, color: '#333' }}>Code: {pasteCode}</div>
                        <div style={{ color: '#777' }}>Expires in {codeCountdown || '00:00:00'}</div>
                        <button onClick={copyPasteCode} style={{ padding: '6px 10px', borderRadius: '8px', border: '1px solid #eee', background: 'white', color: '#333', fontWeight: 600, cursor: 'pointer' }}>Copy</button>
                      </div>
                    ) : (
                      <div style={{ color: '#999' }}>No active code</div>
                    )}
                  </div>
                  <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
                    <button
                      disabled={role !== 'owner'}
                      onClick={generatePasteCode}
                      style={{ padding: '10px 14px', borderRadius: '10px', border: 'none', background: role !== 'owner' ? '#ffd8ae' : 'linear-gradient(135deg, #ffb800 0%, #ff8c00 100%)', color: 'white', fontWeight: 700, cursor: role !== 'owner' ? 'not-allowed' : 'pointer' }}
                    >
                      Generate Code
                    </button>
                    <button
                      disabled={role !== 'owner' || !pasteCode}
                      onClick={revokePasteCode}
                      style={{ padding: '10px 14px', borderRadius: '10px', border: '1px solid #ff8c00', background: 'white', color: '#ff8c00', fontWeight: 700, cursor: role !== 'owner' || !pasteCode ? 'not-allowed' : 'pointer' }}
                    >
                      Revoke
                    </button>
                  </div>
                  {role !== 'owner' && (
                    <div style={{ marginTop: '8px', color: '#a85e00' }}>Only store owners can generate or revoke codes.</div>
                  )}
                </div>

                {/* Store Details Form */}
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '15px' }}>
                  <div>
                    <label style={{ display: 'block', fontSize: '0.85rem', color: '#777', marginBottom: '6px' }}>Store name</label>
                    <input
                      value={storeName}
                      onChange={(e) => setStoreName(e.target.value)}
                      placeholder="Store name"
                      style={{
                        width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid #ddd',
                        outline: 'none', fontSize: '0.95rem', color: '#00bcd4', fontWeight: 500
                      }}
                    />
                  </div>
                  <div>
                    <label style={{ display: 'block', fontSize: '0.85rem', color: '#777', marginBottom: '6px' }}>Branch</label>
                    <input
                      value={branch}
                      onChange={(e) => setBranch(e.target.value)}
                      placeholder="Branch"
                      style={{
                        width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid #ddd',
                        outline: 'none', fontSize: '0.95rem', color: '#00bcd4', fontWeight: 500
                      }}
                    />
                  </div>
                  <div>
                    <label style={{ display: 'block', fontSize: '0.85rem', color: '#777', marginBottom: '6px' }}>Type</label>
                    <input
                      value={storeType}
                      onChange={(e) => setStoreType(e.target.value)}
                      placeholder="Type"
                      style={{
                        width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid #ddd',
                        outline: 'none', fontSize: '0.95rem', color: '#00bcd4', fontWeight: 500
                      }}
                    />
                  </div>
                </div>
                {/* Orange gradient DONE button */}
                <button
                  onClick={handleSave}
                  style={{
                    width: '100%',
                    padding: '16px',
                    background: 'linear-gradient(135deg, #ffb800 0%, #ff8c00 100%)',
                    color: 'white',
                    border: 'none',
                    borderRadius: '25px',
                    fontSize: '1.1rem',
                    fontWeight: '700',
                    cursor: 'pointer',
                    marginTop: '20px'
                  }}
                >
                  DONE
                </button>
                {actionStatus.kind && (
                  <div style={{
                    marginTop: '10px',
                    textAlign: 'center',
                    fontWeight: 600,
                    color: actionStatus.kind === 'success' ? '#1d7a33' : actionStatus.kind === 'error' ? '#b32626' : '#a85e00'
                  }}>
                    {actionStatus.text}
                  </div>
                )}
              </div>
            )}

            {/* Members Tab */}
            {activeTab === 'members' && (
              <div>
                {/* Owners Section Header */}
                <div style={{
                  background: 'linear-gradient(135deg, #ffb800 0%, #ff8c00 100%)',
                  padding: '12px 20px',
                  borderRadius: '25px',
                  color: 'white',
                  fontWeight: '700',
                  fontSize: '1rem',
                  textAlign: 'center',
                  marginBottom: '15px'
                }}>
                  Owners
                </div>
                {(members || []).filter(m => m.role === 'owner').map(member => (
                  <div key={member.id} style={{
                    padding: '15px',
                    borderBottom: '1px solid #f0f0f0',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    flexWrap: 'wrap',
                    gap: '10px'
                  }}>
                    <div>
                      <div style={{ fontSize: '1rem', fontWeight: '600', color: '#333' }}>{member.name}</div>
                      <div style={{ fontSize: '0.85rem', color: '#999' }}>Owner</div>
                    </div>
                    {/* No options for owners */}
                  </div>
                ))}

                {/* Managers Section */}
                <div style={{
                  background: 'linear-gradient(135deg, #ffb800 0%, #ff8c00 100%)',
                  padding: '12px 20px',
                  borderRadius: '25px',
                  color: 'white',
                  fontWeight: '700',
                  fontSize: '1rem',
                  textAlign: 'center',
                  margin: '20px 0 15px'
                }}>
                  Managers
                </div>
                {(members || []).filter(m => m.role === 'manager').map(member => (
                  <div key={member.id} style={{
                    padding: '15px',
                    borderBottom: '1px solid #f0f0f0',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    flexWrap: 'wrap',
                    gap: '10px'
                  }}>
                    <div>
                      <div style={{ fontSize: '1rem', fontWeight: '600', color: '#333' }}>{member.name}</div>
                      <div style={{ fontSize: '0.85rem', color: '#999' }}>Manager</div>
                    </div>
                    <button style={{ 
                      width: '36px', 
                      height: '36px', 
                      background: 'transparent', 
                      border: '1px solid #e0e0e0', 
                      borderRadius: '50%', 
                      cursor: 'pointer', 
                      display: 'flex', 
                      alignItems: 'center', 
                      justifyContent: 'center', 
                      color: '#666' 
                    }} onClick={() => setMemberOptions({ member, role: 'manager' })} aria-label={`Edit ${member.name}`}>
                      <svg width="16" height="16" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z"/>
                      </svg>
                    </button>
                  </div>
                ))}

                {/* Sale Staff Section */}
                <div style={{
                  background: 'linear-gradient(135deg, #ffb800 0%, #ff8c00 100%)',
                  padding: '12px 20px',
                  borderRadius: '25px',
                  color: 'white',
                  fontWeight: '700',
                  fontSize: '1rem',
                  textAlign: 'center',
                  margin: '20px 0 15px'
                }}>
                  Sale Staff
                </div>
                {(members || []).filter(m => m.role !== 'owner' && m.role !== 'manager').map(member => (
                  <div key={member.id} style={{
                    padding: '15px',
                    borderBottom: '1px solid #f0f0f0',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    flexWrap: 'wrap',
                    gap: '10px'
                  }}>
                    <div>
                      <div style={{ fontSize: '1rem', fontWeight: '600', color: '#333' }}>{member.name}</div>
                      <div style={{ fontSize: '0.85rem', color: '#999' }}>Sale Staff</div>
                    </div>
                    <button style={{ 
                      width: '36px', 
                      height: '36px', 
                      background: 'transparent', 
                      border: '1px solid #e0e0e0', 
                      borderRadius: '50%', 
                      cursor: 'pointer', 
                      display: 'flex', 
                      alignItems: 'center', 
                      justifyContent: 'center', 
                      color: '#666' 
                    }} onClick={() => setMemberOptions({ member, role: 'employee' })} aria-label={`Edit ${member.name}`}>
                      <svg width="16" height="16" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z"/>
                      </svg>
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
      </div>
    </div>

      {/* Member Options Modal */}
      {memberOptions && (
        <div 
          className="member-options-overlay"
          onClick={() => setMemberOptions(null)}
          style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', zIndex: 9999 }}
        >
          <div 
            className="member-options-card"
            onClick={(e) => e.stopPropagation()}
            style={{ background: '#fff', borderRadius: 20, maxWidth: 420, width: '90%', margin: '10% auto', padding: 20, boxShadow: '0 10px 40px rgba(0,0,0,0.2)' }}
          >
            <h3 style={{ margin: 0, fontSize: '1.2rem', color: '#ff8c00', textAlign: 'center', fontWeight: 800 }}>Manage Member</h3>
            <p style={{ textAlign: 'center', marginTop: 8, color: '#444', fontWeight: 600 }}>{memberOptions.member.name}</p>
            <div style={{ display: 'grid', gap: 12, marginTop: 18 }}>
              <button
                onClick={async () => {
                  try {
                    const { error } = await supabase.rpc('update_store_member_role', { p_store_id: storeId, p_member_id: memberOptions.member.id, p_new_role: 'employee' });
                    if (error) throw error;
                    setActionStatus({ kind: 'success', text: 'Updated to view-only.' });
                    setTimeout(() => setActionStatus({ kind: null, text: '' }), 3000);
                    const { data: memRows } = await supabase.rpc('get_store_members', { p_store_id: storeId });
                    const enriched = (memRows || []).map(m => ({
                      id: m.user_id,
                      role: m.role,
                      name: m.name || (m.email ? m.email.split('@')[0] : 'User'),
                      email: m.email || ''
                    }));
                    setMembers(enriched);
                  } catch (e) {
                    setActionStatus({ kind: 'error', text: e.message || 'Failed to update role.' });
                    setTimeout(() => setActionStatus({ kind: null, text: '' }), 4000);
                  } finally {
                    setMemberOptions(null);
                  }
                }}
                style={{ padding: '12px 14px', borderRadius: 14, border: '1px solid #e0e0e0', background: '#fff', color: '#333', fontWeight: 700 }}
              >View only pricelist</button>

              <button
                onClick={async () => {
                  try {
                    const { error } = await supabase.rpc('update_store_member_role', { p_store_id: storeId, p_member_id: memberOptions.member.id, p_new_role: 'manager' });
                    if (error) throw error;
                    setActionStatus({ kind: 'success', text: 'Updated to manage prices.' });
                    setTimeout(() => setActionStatus({ kind: null, text: '' }), 3000);
                    const { data: memRows } = await supabase.rpc('get_store_members', { p_store_id: storeId });
                    const enriched = (memRows || []).map(m => ({
                      id: m.user_id,
                      role: m.role,
                      name: m.name || (m.email ? m.email.split('@')[0] : 'User'),
                      email: m.email || ''
                    }));
                    setMembers(enriched);
                  } catch (e) {
                    setActionStatus({ kind: 'error', text: e.message || 'Failed to update role.' });
                    setTimeout(() => setActionStatus({ kind: null, text: '' }), 4000);
                  } finally {
                    setMemberOptions(null);
                  }
                }}
                style={{ padding: '12px 14px', borderRadius: 14, border: '1px solid #e0e0e0', background: '#fff', color: '#333', fontWeight: 700 }}
              >Manage prices</button>

              <button
                onClick={async () => {
                  try {
                    const { error } = await supabase.rpc('update_store_member_role', { p_store_id: storeId, p_member_id: memberOptions.member.id, p_new_role: 'owner' });
                    if (error) throw error;
                    setActionStatus({ kind: 'success', text: 'Granted all-access (owner).' });
                    setTimeout(() => setActionStatus({ kind: null, text: '' }), 3000);
                    const { data: memRows } = await supabase.rpc('get_store_members', { p_store_id: storeId });
                    const enriched = (memRows || []).map(m => ({
                      id: m.user_id,
                      role: m.role,
                      name: m.name || (m.email ? m.email.split('@')[0] : 'User'),
                      email: m.email || ''
                    }));
                    setMembers(enriched);
                  } catch (e) {
                    setActionStatus({ kind: 'error', text: e.message || 'Failed to assign owner role.' });
                    setTimeout(() => setActionStatus({ kind: null, text: '' }), 4000);
                  } finally {
                    setMemberOptions(null);
                  }
                }}
                style={{ padding: '12px 14px', borderRadius: 14, border: '1px solid #e0e0e0', background: '#fff', color: '#333', fontWeight: 700 }}
              >All access on store</button>

              <button
                onClick={() => {
                  setConfirmConfig({
                    title: 'Remove Member',
                    message: `Are you sure you want to remove ${memberOptions.member.name} from this store?`,
                    confirmLabel: 'Remove',
                    action: async () => {
                      try {
                        const { error } = await supabase.rpc('remove_store_member', { p_store_id: storeId, p_member_id: memberOptions.member.id });
                        if (error) throw error;
                        setActionStatus({ kind: 'success', text: 'Member removed.' });
                        setTimeout(() => setActionStatus({ kind: null, text: '' }), 3000);
                        const { data: memRows } = await supabase.rpc('get_store_members', { p_store_id: storeId });
                        const enriched = (memRows || []).map(m => ({
                          id: m.user_id,
                          role: m.role,
                          name: m.name || (m.email ? m.email.split('@')[0] : 'User'),
                          email: m.email || ''
                        }));
                        setMembers(enriched);
                      } catch (e) {
                        setActionStatus({ kind: 'error', text: e.message || 'Failed to remove member.' });
                        setTimeout(() => setActionStatus({ kind: null, text: '' }), 4000);
                      } finally {
                        setMemberOptions(null);
                        setConfirmOpen(false);
                      }
                    }
                  });
                  setConfirmOpen(true);
                }}
                style={{ padding: '12px 14px', borderRadius: 14, border: '1px solid #f5caca', background: '#fdecec', color: '#b32626', fontWeight: 800 }}
              >Remove from store</button>

              <button onClick={() => setMemberOptions(null)} style={{ padding: '10px 12px', borderRadius: 14, border: '1px solid #e0e0e0', background: '#fafafa', color: '#666', fontWeight: 700 }}>Back</button>
            </div>
          </div>
        </div>
      )}

      {/* Invite Staff modal */}
      <InviteStaffModal
        open={inviteOpen}
        onClose={() => setInviteOpen(false)}
        storeId={storeId}
        storeName={storeName}
        isOwner={role === 'owner'}
      />

      {/* Confirm modal for destructive actions */}
      <ConfirmModal
        open={confirmOpen}
        title={confirmConfig.title}
        message={confirmConfig.message}
        confirmLabel={confirmConfig.confirmLabel}
        onConfirm={confirmConfig.action}
        onCancel={() => setConfirmOpen(false)}
      />

      {/* Export Pricelist modal */}
      <ExportPricelistModal
        open={exportOpen}
        onClose={() => setExportOpen(false)}
        storeId={storeId}
        storeName={storeName}
        branch={branch}
        role={role}
      />

      {/* Copy Prices modal */}
      <CopyPricesModal
        open={copyOpen}
        onClose={() => setCopyOpen(false)}
        sourceStoreId={storeId}
        sourceStoreName={storeName}
      />

      {/* Import Prices modal */}
      <ImportPricesModal
        open={importOpen}
        onClose={() => setImportOpen(false)}
        storeId={storeId}
        storeName={storeName}
        role={role}
      />

      
    </div>
  );
}