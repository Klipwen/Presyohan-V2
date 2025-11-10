import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { supabase } from '../config/supabaseClient';
import Header from '../components/layout/Header';
import Footer from '../components/layout/Footer';
import iconStore from '../assets/icon_store.png';

// Store Settings Page
// Uses the provided layout, replaces dummy data with real store/member data,
// avoids alerts, and wires the Manage Items tab to the ManageItemsPage.
export default function StoreSettingsPage() {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState('store');
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
  const [isMobile, setIsMobile] = useState(() => window.innerWidth <= 768);

  const [storeId, setStoreId] = useState(null);
  const [storeName, setStoreName] = useState('');
  const [branch, setBranch] = useState('');
  const [storeType, setStoreType] = useState('');
  const [role, setRole] = useState(null);

  const [members, setMembers] = useState([]);
  const [actionStatus, setActionStatus] = useState({ kind: null, text: '' });

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

      try {
        if (id) {
          // Get store core info
          const { data: storeRow, error: storeErr } = await supabase
            .from('stores')
            .select('id, name, branch, type')
            .eq('id', id)
            .maybeSingle();
          if (!storeErr && storeRow) {
            setStoreName(storeRow.name || '');
            setBranch(storeRow.branch || '');
            setStoreType(storeRow.type || '');
          }

          // Get current user's role for this store
          const { data: myMembership } = await supabase
            .from('store_members')
            .select('role')
            .eq('store_id', id)
            .eq('user_id', session.user.id)
            .maybeSingle();
          setRole(myMembership?.role || null);

          // Fetch members: roles then enrich with names/emails
          const { data: memRows, error: memErr } = await supabase
            .from('store_members')
            .select('user_id, role')
            .eq('store_id', id);
          if (!memErr) {
            const ids = (memRows || []).map(m => m.user_id).filter(Boolean);
            let infoById = {};
            if (ids.length > 0) {
              const { data: users, error: usersErr } = await supabase
                .from('app_users')
                .select('id, name, email')
                .in('id', ids);
              if (!usersErr) {
                infoById = (users || []).reduce((acc, u) => { acc[u.id] = u; return acc; }, {});
              }
            }
            const enriched = (memRows || []).map(m => ({
              id: m.user_id,
              role: m.role,
              name: infoById[m.user_id]?.name || (infoById[m.user_id]?.email ? infoById[m.user_id].email.split('@')[0] : 'User'),
              email: infoById[m.user_id]?.email || ''
            }));
            setMembers(enriched);
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
      setActionStatus({ kind: 'success', text: 'Store settings saved successfully.' });
    } catch (e) {
      setActionStatus({ kind: 'error', text: e.message || 'Failed to save store settings.' });
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
        <Header variant="store" onToggleSideMenu={() => setMobileSidebarOpen(s => !s)} />
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
          {isMobile && (
            <button 
              className="mobile-menu-toggle"
              onClick={() => setMobileSidebarOpen(!mobileSidebarOpen)}
              style={{
                marginLeft: 'auto',
                background: 'transparent',
                border: 'none',
                cursor: 'pointer',
                padding: '5px',
                color: '#ff8c00'
              }}
            >
              <svg width="28" height="28" fill="currentColor" viewBox="0 0 24 24">
                <path d="M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z"/>
              </svg>
            </button>
          )}
        </div>
      </div>

      {/* Mobile Sidebar Overlay */}
      {mobileSidebarOpen && (
        <div 
          className="mobile-overlay"
          onClick={() => setMobileSidebarOpen(false)}
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            background: 'rgba(0, 0, 0, 0.5)',
            zIndex: 999,
            display: 'block'
          }}
        ></div>
      )}

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
                  <button style={{
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
                    transition: 'all 0.2s ease'
                  }}>
                    <svg width="28" height="28" fill="#ff8c00" viewBox="0 0 24 24">
                      <path d="M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z"/>
                    </svg>
                    Copy Prices
                  </button>
                  <button style={{
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
                    transition: 'all 0.2s ease'
                  }}>
                    <svg width="28" height="28" fill="#ff8c00" viewBox="0 0 24 24">
                      <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/>
                    </svg>
                    Invite staff
                  </button>
                  <button style={{
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
                    transition: 'all 0.2s ease'
                  }}>
                    <svg width="28" height="28" fill="#ff8c00" viewBox="0 0 24 24">
                      <path d="M9 11H7v2h2v-2zm4 0h-2v2h2v-2zm4 0h-2v2h2v-2zm2-7h-1V2h-2v2H8V2H6v2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 16H5V9h14v11z"/>
                    </svg>
                    Convert
                  </button>
                  <button style={{
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
                    transition: 'all 0.2s ease'
                  }}>
                    <svg width="28" height="28" fill="#ff8c00" viewBox="0 0 24 24">
                      <path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zM9 17H7v-7h2v7zm4 0h-2V7h2v10zm4 0h-2v-4h2v4z"/>
                    </svg>
                    Import prices
                  </button>
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
                    <button style={{
                      padding: '8px 16px',
                      background: 'transparent',
                      border: '1px solid #e0e0e0',
                      borderRadius: '20px',
                      fontSize: '0.85rem',
                      color: '#666',
                      cursor: 'pointer'
                    }}>Options</button>
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
                      padding: '8px 16px',
                      background: 'transparent',
                      border: '1px solid #e0e0e0',
                      borderRadius: '20px',
                      fontSize: '0.85rem',
                      color: '#666',
                      cursor: 'pointer'
                    }}>Options</button>
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
                      padding: '8px 16px',
                      background: 'transparent',
                      border: '1px solid #e0e0e0',
                      borderRadius: '20px',
                      fontSize: '0.85rem',
                      color: '#666',
                      cursor: 'pointer'
                    }}>Options</button>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Footer (anchored at bottom) */}
      <Footer />
    </div>
  );
}