import React, { useState, useEffect } from 'react';
import { supabase } from '../../config/supabaseClient';

export default function StoreSukisModal({ store, isOpen, onClose, onSukiCountChanged }) {
  const [sukis, setSukis] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');
  const [actionLoadingId, setActionLoadingId] = useState(null);
  const [errorMessage, setErrorMessage] = useState('');
  const [removingSuki, setRemovingSuki] = useState(null);

  // Add/Pair Suki modal states
  const [showAddModal, setShowAddModal] = useState(false);
  const [allUsers, setAllUsers] = useState([]);
  const [loadingUsers, setLoadingUsers] = useState(false);
  const [userSearchQuery, setUserSearchQuery] = useState('');
  const [pairingUserId, setPairingUserId] = useState(null);

  const fetchSukis = async () => {
    if (!store?.id) return;
    try {
      setLoading(true);
      setErrorMessage('');

      // 1. Fetch raw suki_relationships rows (No relational join to avoid schema cache failures)
      const { data: relRows, error: relErr } = await supabase
        .from('suki_relationships')
        .select('*')
        .eq('store_id', store.id)
        .order('created_at', { ascending: false });

      if (relErr) {
        console.error('Error querying suki_relationships:', relErr);
        throw relErr;
      }

      // 2. Fetch pending suki notifications if any
      let notifRows = [];
      try {
        const { data: nData, error: nErr } = await supabase
          .from('notifications')
          .select('*')
          .eq('store_id', store.id);

        if (!nErr && nData) {
          notifRows = nData.filter(n => {
            const t = (n.type || '').toLowerCase();
            return (t.includes('suki_request') || t.includes('suki_pending') || t === 'suki') &&
                   !t.includes('accepted') && !t.includes('rejected') && !t.includes('declined') && !t.includes('cancel');
          });
        }
      } catch (e) {
        console.warn('Notifications query skipped:', e);
      }

      // 3. Collect all user IDs
      const sukiUserIds = (relRows || []).map(r => r.user_id).filter(Boolean);
      const notifUserIds = notifRows.map(n => n.sender_user_id || n.receiver_user_id).filter(Boolean);
      const uniqueUserIds = Array.from(new Set([...sukiUserIds, ...notifUserIds]));

      // 4. Fetch user profiles from app_users for these IDs
      const userMap = {};
      if (uniqueUserIds.length > 0) {
        try {
          const { data: usersData, error: uErr } = await supabase
            .from('app_users')
            .select('*')
            .in('id', uniqueUserIds);

          if (!uErr && usersData) {
            usersData.forEach(u => {
              if (u.id) userMap[u.id] = u;
            });
          }
        } catch (uEx) {
          console.warn('Failed to load user profiles in batch:', uEx);
        }
      }

      // 5. Construct merged suki list
      const items = [];
      const seenUserIds = new Set();

      // Process suki_relationships
      (relRows || []).forEach(rel => {
        const uId = rel.user_id;
        if (!uId) return;
        seenUserIds.add(uId);

        const profile = userMap[uId] || {
          id: uId,
          name: 'Registered User',
          email: null,
          username: null,
          user_code: uId.substring(0, 8).toUpperCase(),
          avatar_url: null
        };

        items.push({
          id: rel.id || uId,
          user_id: uId,
          store_id: store.id,
          status: rel.status || 'active',
          created_at: rel.created_at || new Date().toISOString(),
          app_users: profile
        });
      });

      // Process pending notifications
      notifRows.forEach(notif => {
        const uId = notif.sender_user_id;
        if (!uId || seenUserIds.has(uId)) return;
        seenUserIds.add(uId);

        const profile = userMap[uId] || {
          id: uId,
          name: 'Suki Applicant',
          email: null,
          username: null,
          user_code: uId.substring(0, 8).toUpperCase(),
          avatar_url: null
        };

        items.push({
          id: notif.id || uId,
          user_id: uId,
          store_id: store.id,
          status: 'pending',
          created_at: notif.created_at || new Date().toISOString(),
          app_users: profile
        });
      });

      setSukis(items);
    } catch (err) {
      console.error('Failed to load store sukis:', err);
      setErrorMessage('Could not load sukis: ' + (err.message || 'Unknown database error'));
      setSukis([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (isOpen && store?.id) {
      setSearchQuery('');
      setStatusFilter('all');
      fetchSukis();
    }
  }, [isOpen, store?.id]);

  // Load all app users when Add Suki modal is opened
  const loadAllUsersForPairing = async () => {
    try {
      setLoadingUsers(true);
      const { data, error } = await supabase
        .from('app_users')
        .select('id, name, email, username, user_code, role, avatar_url')
        .order('name', { ascending: true })
        .limit(100);

      if (error) throw error;
      setAllUsers(data || []);
    } catch (err) {
      console.error('Failed to load users for pairing:', err);
    } finally {
      setLoadingUsers(false);
    }
  };

  const handleOpenAddModal = () => {
    setUserSearchQuery('');
    setShowAddModal(true);
    loadAllUsersForPairing();
  };

  // Pair a customer as suki
  const handlePairCustomer = async (user) => {
    try {
      setPairingUserId(user.id);

      const { error } = await supabase
        .from('suki_relationships')
        .upsert({
          user_id: user.id,
          store_id: store.id,
          status: 'active',
          created_at: new Date().toISOString()
        }, { onConflict: 'user_id,store_id' });

      if (error) throw error;

      await fetchSukis();
      if (onSukiCountChanged) onSukiCountChanged();
      setShowAddModal(false);
    } catch (err) {
      console.error('Failed to pair customer as suki:', err);
      alert('Error pairing customer: ' + err.message);
    } finally {
      setPairingUserId(null);
    }
  };

  // Handle Remove Suki
  const handleConfirmRemove = async () => {
    if (!removingSuki) return;
    try {
      setActionLoadingId(removingSuki.user_id || removingSuki.id);

      // Delete from suki_relationships
      const { error: delErr } = await supabase
        .from('suki_relationships')
        .delete()
        .eq('store_id', store.id)
        .eq('user_id', removingSuki.user_id);

      if (delErr) {
        console.warn('Direct delete error, trying RPC:', delErr);
        await supabase.rpc('remove_suki_relationship', {
          p_store_id: store.id,
          p_user_id: removingSuki.user_id
        });
      }

      setSukis(prev => prev.filter(s => (s.user_id || s.id) !== (removingSuki.user_id || removingSuki.id)));
      setRemovingSuki(null);
      if (onSukiCountChanged) {
        onSukiCountChanged();
      }
    } catch (err) {
      console.error('Failed to remove suki:', err);
      alert('Error removing suki: ' + err.message);
    } finally {
      setActionLoadingId(null);
    }
  };

  if (!isOpen || !store) return null;

  // Format Date Helper
  const formatDate = (isoStr) => {
    if (!isoStr) return 'N/A';
    try {
      const d = new Date(isoStr);
      return d.toLocaleDateString('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric'
      });
    } catch {
      return isoStr;
    }
  };

  const getTimeAgo = (isoStr) => {
    if (!isoStr) return '';
    try {
      const diffMs = Date.now() - new Date(isoStr).getTime();
      const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
      if (diffDays === 0) return 'Today';
      if (diffDays === 1) return 'Yesterday';
      if (diffDays < 30) return `${diffDays}d ago`;
      const diffMonths = Math.floor(diffDays / 30);
      if (diffMonths < 12) return `${diffMonths}mo ago`;
      return `${Math.floor(diffMonths / 12)}y ago`;
    } catch {
      return '';
    }
  };

  const getInitials = (name) => {
    if (!name) return 'S';
    return name
      .split(' ')
      .map(part => part[0])
      .filter(Boolean)
      .slice(0, 2)
      .join('')
      .toUpperCase();
  };

  // Filtering
  const filteredSukis = sukis.filter(item => {
    const user = item.app_users || {};
    const name = (user.name || '').toLowerCase();
    const email = (user.email || '').toLowerCase();
    const username = (user.username || '').toLowerCase();
    const userCode = (user.user_code || '').toLowerCase();
    const q = searchQuery.toLowerCase().trim();

    const matchesQuery = !q || name.includes(q) || email.includes(q) || username.includes(q) || userCode.includes(q);
    
    let matchesStatus = true;
    if (statusFilter === 'active') {
      matchesStatus = (item.status || 'active').toLowerCase() === 'active';
    } else if (statusFilter === 'pending') {
      matchesStatus = (item.status || '').toLowerCase().includes('pending');
    }

    return matchesQuery && matchesStatus;
  });

  const totalCount = sukis.length;
  const activeCount = sukis.filter(s => (s.status || 'active').toLowerCase() === 'active').length;
  const pendingCount = sukis.filter(s => (s.status || '').toLowerCase().includes('pending')).length;

  const existingSukiUserIds = new Set(sukis.map(s => s.user_id));

  const filteredPairingUsers = allUsers.filter(u => {
    const q = userSearchQuery.toLowerCase().trim();
    if (!q) return true;
    return (u.name || '').toLowerCase().includes(q) ||
           (u.email || '').toLowerCase().includes(q) ||
           (u.username || '').toLowerCase().includes(q) ||
           (u.user_code || '').toLowerCase().includes(q);
  });

  return (
    <div style={{
      position: 'fixed',
      inset: 0,
      backgroundColor: 'rgba(15, 23, 42, 0.65)',
      backdropFilter: 'blur(4px)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      zIndex: 200,
      fontFamily: 'Outfit, sans-serif',
      padding: '20px'
    }}>
      <div style={{
        backgroundColor: '#ffffff',
        borderRadius: '20px',
        width: '100%',
        maxWidth: '880px',
        maxHeight: '90vh',
        display: 'flex',
        flexDirection: 'column',
        boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
        overflow: 'hidden',
        animation: 'fadeIn 0.2s ease-out'
      }}>
        {/* Modal Header */}
        <div style={{
          padding: '22px 28px',
          borderBottom: '1px solid #f1f5f9',
          display: 'flex',
          alignItems: 'flex-start',
          justifyContent: 'space-between',
          backgroundColor: '#fafbfc'
        }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '4px', flexWrap: 'wrap' }}>
              <h2 style={{ margin: 0, fontSize: '1.35rem', fontWeight: 800, color: '#0f172a' }}>
                Sukis of {store.name}
              </h2>
              {store.is_standard_store ? (
                <span style={{
                  backgroundColor: 'rgba(255, 140, 0, 0.1)',
                  color: '#ff8c00',
                  fontSize: '0.72rem',
                  fontWeight: 700,
                  padding: '3px 8px',
                  borderRadius: '6px',
                  letterSpacing: '0.3px',
                  textTransform: 'uppercase'
                }}>
                  Standard Reference Store
                </span>
              ) : (
                <span style={{
                  backgroundColor: 'rgba(0, 188, 212, 0.1)',
                  color: '#00bcd4',
                  fontSize: '0.72rem',
                  fontWeight: 700,
                  padding: '3px 8px',
                  borderRadius: '6px',
                  letterSpacing: '0.3px',
                  textTransform: 'uppercase'
                }}>
                  {store.type || 'Store'}
                </span>
              )}
            </div>
            <p style={{ margin: 0, fontSize: '0.85rem', color: '#64748b' }}>
              Registered customers paired with this store to track reference prices and catalog updates.
            </p>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <button
              onClick={handleOpenAddModal}
              style={{
                backgroundColor: '#ff8c00',
                color: '#ffffff',
                border: 'none',
                borderRadius: '10px',
                padding: '8px 14px',
                fontSize: '0.82rem',
                fontWeight: 700,
                cursor: 'pointer',
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                boxShadow: '0 2px 6px rgba(255, 140, 0, 0.25)'
              }}
            >
              <span>+</span>
              <span>Pair Customer</span>
            </button>

            <button
              onClick={onClose}
              style={{
                background: '#f1f5f9',
                border: 'none',
                borderRadius: '50%',
                width: '34px',
                height: '34px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                cursor: 'pointer',
                color: '#64748b',
                fontSize: '1.1rem',
                transition: 'all 0.2s ease'
              }}
              title="Close"
            >
              ✕
            </button>
          </div>
        </div>

        {/* Metrics Summary Strip */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))',
          gap: '12px',
          padding: '14px 28px',
          backgroundColor: '#ffffff',
          borderBottom: '1px solid #f1f5f9'
        }}>
          <div style={{
            padding: '10px 14px',
            backgroundColor: '#f8fafc',
            borderRadius: '12px',
            border: '1px solid #e2e8f0',
            display: 'flex',
            alignItems: 'center',
            gap: '12px'
          }}>
            <div style={{
              width: '36px',
              height: '36px',
              borderRadius: '10px',
              backgroundColor: 'rgba(255, 140, 0, 0.1)',
              color: '#ff8c00',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '1.1rem'
            }}>
              👥
            </div>
            <div>
              <div style={{ fontSize: '0.7rem', fontWeight: 600, color: '#64748b', textTransform: 'uppercase' }}>Total Sukis</div>
              <div style={{ fontSize: '1.2rem', fontWeight: 800, color: '#0f172a' }}>{totalCount}</div>
            </div>
          </div>

          <div style={{
            padding: '10px 14px',
            backgroundColor: '#f8fafc',
            borderRadius: '12px',
            border: '1px solid #e2e8f0',
            display: 'flex',
            alignItems: 'center',
            gap: '12px'
          }}>
            <div style={{
              width: '36px',
              height: '36px',
              borderRadius: '10px',
              backgroundColor: 'rgba(16, 185, 129, 0.1)',
              color: '#10b981',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '1.1rem'
            }}>
              ✓
            </div>
            <div>
              <div style={{ fontSize: '0.7rem', fontWeight: 600, color: '#64748b', textTransform: 'uppercase' }}>Active Suki</div>
              <div style={{ fontSize: '1.2rem', fontWeight: 800, color: '#10b981' }}>{activeCount}</div>
            </div>
          </div>

          <div style={{
            padding: '10px 14px',
            backgroundColor: '#f8fafc',
            borderRadius: '12px',
            border: '1px solid #e2e8f0',
            display: 'flex',
            alignItems: 'center',
            gap: '12px'
          }}>
            <div style={{
              width: '36px',
              height: '36px',
              borderRadius: '10px',
              backgroundColor: 'rgba(0, 188, 212, 0.1)',
              color: '#00bcd4',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '1.1rem'
            }}>
              👁️
            </div>
            <div>
              <div style={{ fontSize: '0.7rem', fontWeight: 600, color: '#64748b', textTransform: 'uppercase' }}>Store Status</div>
              <div style={{ fontSize: '1.05rem', fontWeight: 700, color: store.is_public ? '#00bcd4' : '#64748b' }}>
                {store.is_public ? 'Public' : 'Private'}
              </div>
            </div>
          </div>
        </div>

        {/* Search and Filters Bar */}
        <div style={{
          padding: '14px 28px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: '12px',
          flexWrap: 'wrap',
          backgroundColor: '#fafbfc',
          borderBottom: '1px solid #f1f5f9'
        }}>
          {/* Search Input */}
          <div style={{ position: 'relative', flex: 1, minWidth: '240px' }}>
            <span style={{
              position: 'absolute',
              left: '12px',
              top: '50%',
              transform: 'translateY(-50%)',
              color: '#94a3b8',
              fontSize: '0.9rem'
            }}>
              🔍
            </span>
            <input
              type="text"
              placeholder="Search by name, email, username, or code..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              style={{
                width: '100%',
                padding: '8px 12px 8px 36px',
                borderRadius: '10px',
                border: '1px solid #cbd5e1',
                fontSize: '0.85rem',
                fontFamily: 'inherit',
                outline: 'none',
                boxSizing: 'border-box'
              }}
            />
            {searchQuery && (
              <button
                onClick={() => setSearchQuery('')}
                style={{
                  position: 'absolute',
                  right: '10px',
                  top: '50%',
                  transform: 'translateY(-50%)',
                  background: 'none',
                  border: 'none',
                  color: '#94a3b8',
                  cursor: 'pointer',
                  fontSize: '0.8rem'
                }}
              >
                ✕
              </button>
            )}
          </div>

          {/* Filter Dropdown & Refresh */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              style={{
                padding: '8px 12px',
                borderRadius: '10px',
                border: '1px solid #cbd5e1',
                fontSize: '0.85rem',
                backgroundColor: '#ffffff',
                fontFamily: 'inherit',
                color: '#334155'
              }}
            >
              <option value="all">All Statuses ({totalCount})</option>
              <option value="active">Active ({activeCount})</option>
              {pendingCount > 0 && <option value="pending">Pending ({pendingCount})</option>}
            </select>

            <button
              onClick={fetchSukis}
              disabled={loading}
              style={{
                padding: '8px 14px',
                borderRadius: '10px',
                border: '1px solid #cbd5e1',
                backgroundColor: '#ffffff',
                fontSize: '0.85rem',
                fontWeight: 600,
                color: '#475569',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: '6px'
              }}
              title="Refresh Suki List"
            >
              <span style={{ display: 'inline-block', transform: loading ? 'rotate(180deg)' : 'none', transition: 'transform 0.4s ease' }}>
                🔄
              </span>
              Refresh
            </button>
          </div>
        </div>

        {/* Sukis Content Area */}
        <div style={{
          flex: 1,
          overflowY: 'auto',
          padding: '18px 28px',
          minHeight: '280px',
          maxHeight: '480px'
        }}>
          {errorMessage && (
            <div style={{
              padding: '12px 16px',
              backgroundColor: '#fef2f2',
              border: '1px solid #fecaca',
              borderRadius: '10px',
              color: '#dc2626',
              fontSize: '0.85rem',
              marginBottom: '16px'
            }}>
              {errorMessage}
            </div>
          )}

          {loading ? (
            <div style={{ textAlign: 'center', padding: '60px 0', color: '#94a3b8' }}>
              <div style={{
                width: '36px',
                height: '36px',
                border: '3px solid #f1f5f9',
                borderTopColor: '#ff8c00',
                borderRadius: '50%',
                margin: '0 auto 16px',
                animation: 'spin 0.8s linear infinite'
              }} />
              <p style={{ margin: 0, fontSize: '0.9rem', fontWeight: 500 }}>Loading sukis for this standard store...</p>
            </div>
          ) : filteredSukis.length === 0 ? (
            <div style={{
              textAlign: 'center',
              padding: '45px 20px',
              backgroundColor: '#fafbfc',
              borderRadius: '14px',
              border: '1px dashed #cbd5e1'
            }}>
              <div style={{ fontSize: '2.5rem', marginBottom: '10px' }}>👥</div>
              <h4 style={{ margin: '0 0 6px', color: '#334155', fontWeight: 700, fontSize: '1.1rem' }}>
                {searchQuery || statusFilter !== 'all' ? 'No matching sukis found' : 'No Sukis Added to This Store Yet'}
              </h4>
              <p style={{ margin: '0 0 16px', color: '#64748b', fontSize: '0.85rem', maxWidth: '420px', marginInline: 'auto', lineHeight: 1.4 }}>
                {searchQuery || statusFilter !== 'all'
                  ? 'Try adjusting your search keywords or filter settings.'
                  : 'Customers who add or pair with this standard store on the mobile app will automatically show here. You can also pair a customer directly.'}
              </p>
              {searchQuery || statusFilter !== 'all' ? (
                <button
                  onClick={() => {
                    setSearchQuery('');
                    setStatusFilter('all');
                  }}
                  style={{
                    padding: '6px 14px',
                    borderRadius: '8px',
                    border: '1px solid #cbd5e1',
                    backgroundColor: '#ffffff',
                    fontSize: '0.8rem',
                    fontWeight: 600,
                    color: '#ff8c00',
                    cursor: 'pointer'
                  }}
                >
                  Clear Filters
                </button>
              ) : (
                <button
                  onClick={handleOpenAddModal}
                  className="admin-btn-primary"
                  style={{ padding: '8px 18px', fontSize: '0.85rem' }}
                >
                  + Pair a Customer Now
                </button>
              )}
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              <div style={{ fontSize: '0.78rem', fontWeight: 600, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '4px' }}>
                Showing {filteredSukis.length} Suki Account{filteredSukis.length === 1 ? '' : 's'}
              </div>

              {filteredSukis.map((item) => {
                const user = item.app_users || {};
                const isActive = (item.status || 'active').toLowerCase() === 'active';
                const isPending = (item.status || '').toLowerCase().includes('pending');
                const isItemMutating = actionLoadingId === (item.user_id || item.id);

                return (
                  <div
                    key={item.id || item.user_id}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '14px 18px',
                      backgroundColor: '#ffffff',
                      borderRadius: '14px',
                      border: '1px solid #e2e8f0',
                      transition: 'all 0.2s ease',
                      boxShadow: '0 1px 3px rgba(0,0,0,0.02)'
                    }}
                  >
                    {/* User Info & Avatar */}
                    <div style={{ display: 'flex', alignItems: 'center', gap: '14px', minWidth: 0 }}>
                      {user.avatar_url ? (
                        <img
                          src={user.avatar_url}
                          alt={user.name || 'User'}
                          style={{
                            width: '44px',
                            height: '44px',
                            borderRadius: '50%',
                            objectFit: 'cover',
                            border: '2px solid #f1f5f9'
                          }}
                        />
                      ) : (
                        <div style={{
                          width: '44px',
                          height: '44px',
                          borderRadius: '50%',
                          background: 'linear-gradient(135deg, #ff8c00 0%, #ffb74d 100%)',
                          color: '#ffffff',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontWeight: 700,
                          fontSize: '0.95rem',
                          boxShadow: '0 2px 6px rgba(255, 140, 0, 0.25)',
                          flexShrink: 0
                        }}>
                          {getInitials(user.name || user.email || 'S')}
                        </div>
                      )}

                      <div style={{ minWidth: 0 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                          <span style={{ fontWeight: 700, color: '#0f172a', fontSize: '0.95rem' }}>
                            {user.name || 'Suki Customer'}
                          </span>

                          {user.user_code && (
                            <code style={{
                              fontSize: '0.72rem',
                              backgroundColor: '#f1f5f9',
                              color: '#475569',
                              padding: '2px 6px',
                              borderRadius: '4px',
                              fontWeight: 600
                            }}>
                              #{user.user_code}
                            </code>
                          )}

                          {isActive && (
                            <span style={{
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: '4px',
                              backgroundColor: 'rgba(16, 185, 129, 0.1)',
                              color: '#10b981',
                              fontSize: '0.7rem',
                              fontWeight: 700,
                              padding: '2px 8px',
                              borderRadius: '12px'
                            }}>
                              <span style={{ width: '6px', height: '6px', borderRadius: '50%', backgroundColor: '#10b981' }} />
                              Active Suki
                            </span>
                          )}

                          {isPending && (
                            <span style={{
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: '4px',
                              backgroundColor: 'rgba(245, 158, 11, 0.1)',
                              color: '#d97706',
                              fontSize: '0.7rem',
                              fontWeight: 700,
                              padding: '2px 8px',
                              borderRadius: '12px'
                            }}>
                              <span style={{ width: '6px', height: '6px', borderRadius: '50%', backgroundColor: '#d97706' }} />
                              Pending Request
                            </span>
                          )}
                        </div>

                        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginTop: '3px', fontSize: '0.8rem', color: '#64748b', flexWrap: 'wrap' }}>
                          {user.email && <span>✉️ {user.email}</span>}
                          {user.username && <span>👤 @{user.username}</span>}
                          {user.phone_number && <span>📞 {user.phone_number}</span>}
                          <span>📅 Suki since {formatDate(item.created_at)} ({getTimeAgo(item.created_at)})</span>
                        </div>
                      </div>
                    </div>

                    {/* Actions */}
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginLeft: '16px' }}>
                      <button
                        onClick={() => setRemovingSuki(item)}
                        disabled={isItemMutating}
                        style={{
                          padding: '6px 12px',
                          borderRadius: '8px',
                          border: '1px solid rgba(239, 68, 68, 0.25)',
                          backgroundColor: 'rgba(239, 68, 68, 0.04)',
                          color: '#ef4444',
                          fontSize: '0.78rem',
                          fontWeight: 600,
                          cursor: 'pointer',
                          transition: 'all 0.2s ease',
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '4px'
                        }}
                        title="Remove Suki from store"
                      >
                        {isItemMutating ? 'Removing...' : 'Remove'}
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Modal Footer */}
        <div style={{
          padding: '16px 28px',
          borderTop: '1px solid #f1f5f9',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          backgroundColor: '#fafbfc'
        }}>
          <span style={{ fontSize: '0.8rem', color: '#94a3b8' }}>
            Store ID: <code style={{ color: '#475569' }}>{store.display_id || store.id}</code>
          </span>
          <button
            onClick={onClose}
            className="admin-btn-primary"
            style={{ padding: '8px 20px', fontSize: '0.85rem' }}
          >
            Done
          </button>
        </div>
      </div>

      {/* Pair New Suki Customer Sub-Modal */}
      {showAddModal && (
        <div style={{
          position: 'fixed',
          inset: 0,
          backgroundColor: 'rgba(0,0,0,0.55)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 250,
          fontFamily: 'Outfit, sans-serif',
          padding: '20px'
        }}>
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '16px',
            width: '100%',
            maxWidth: '520px',
            maxHeight: '85vh',
            display: 'flex',
            flexDirection: 'column',
            boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
            overflow: 'hidden'
          }}>
            <div style={{
              padding: '18px 22px',
              borderBottom: '1px solid #f1f5f9',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between'
            }}>
              <div>
                <h3 style={{ margin: 0, fontSize: '1.15rem', fontWeight: 800, color: '#0f172a' }}>
                  Pair Customer with {store.name}
                </h3>
                <p style={{ margin: '2px 0 0', fontSize: '0.8rem', color: '#64748b' }}>
                  Select a registered user to add them as an active suki of this store.
                </p>
              </div>
              <button
                onClick={() => setShowAddModal(false)}
                style={{
                  background: 'none',
                  border: 'none',
                  fontSize: '1.1rem',
                  color: '#94a3b8',
                  cursor: 'pointer'
                }}
              >
                ✕
              </button>
            </div>

            <div style={{ padding: '14px 22px', borderBottom: '1px solid #f1f5f9' }}>
              <input
                type="text"
                placeholder="Search users by name, email, or code..."
                value={userSearchQuery}
                onChange={(e) => setUserSearchQuery(e.target.value)}
                style={{
                  width: '100%',
                  padding: '8px 12px',
                  borderRadius: '8px',
                  border: '1px solid #cbd5e1',
                  fontSize: '0.85rem',
                  fontFamily: 'inherit',
                  outline: 'none',
                  boxSizing: 'border-box'
                }}
              />
            </div>

            <div style={{ flex: 1, overflowY: 'auto', padding: '14px 22px', maxHeight: '360px' }}>
              {loadingUsers ? (
                <div style={{ textAlign: 'center', padding: '30px 0', color: '#94a3b8', fontSize: '0.85rem' }}>
                  Loading registered users...
                </div>
              ) : filteredPairingUsers.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '30px 0', color: '#94a3b8', fontSize: '0.85rem' }}>
                  No users found matching query.
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                  {filteredPairingUsers.map(user => {
                    const isAlreadySuki = existingSukiUserIds.has(user.id);
                    const isPairing = pairingUserId === user.id;

                    return (
                      <div
                        key={user.id}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                          padding: '10px 14px',
                          borderRadius: '10px',
                          border: '1px solid #e2e8f0',
                          backgroundColor: isAlreadySuki ? '#f8fafc' : '#ffffff'
                        }}
                      >
                        <div style={{ minWidth: 0, paddingRight: '10px' }}>
                          <div style={{ fontWeight: 600, fontSize: '0.88rem', color: '#0f172a' }}>
                            {user.name || 'Unnamed User'}
                          </div>
                          <div style={{ fontSize: '0.75rem', color: '#64748b' }}>
                            {user.email || `@${user.username}` || `#${user.user_code}`}
                          </div>
                        </div>

                        <div>
                          {isAlreadySuki ? (
                            <span style={{
                              fontSize: '0.72rem',
                              fontWeight: 700,
                              color: '#10b981',
                              backgroundColor: 'rgba(16, 185, 129, 0.1)',
                              padding: '3px 8px',
                              borderRadius: '6px'
                            }}>
                              Already Suki
                            </span>
                          ) : (
                            <button
                              onClick={() => handlePairCustomer(user)}
                              disabled={isPairing}
                              style={{
                                backgroundColor: '#ff8c00',
                                color: '#ffffff',
                                border: 'none',
                                borderRadius: '6px',
                                padding: '5px 12px',
                                fontSize: '0.78rem',
                                fontWeight: 700,
                                cursor: 'pointer'
                              }}
                            >
                              {isPairing ? 'Pairing...' : '+ Pair as Suki'}
                            </button>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            <div style={{
              padding: '12px 22px',
              borderTop: '1px solid #f1f5f9',
              display: 'flex',
              justifyContent: 'flex-end',
              backgroundColor: '#fafbfc'
            }}>
              <button
                onClick={() => setShowAddModal(false)}
                className="admin-btn-action"
                style={{ padding: '6px 14px', fontSize: '0.82rem' }}
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Suki Removal Confirmation Sub-Modal */}
      {removingSuki && (
        <div style={{
          position: 'fixed',
          inset: 0,
          backgroundColor: 'rgba(0,0,0,0.5)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 250,
          fontFamily: 'Outfit, sans-serif',
          padding: '20px'
        }}>
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '16px',
            width: '100%',
            maxWidth: '420px',
            padding: '24px',
            boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
            textAlign: 'center'
          }}>
            <div style={{
              width: '48px',
              height: '48px',
              borderRadius: '50%',
              backgroundColor: '#fee2e2',
              color: '#ef4444',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '1.4rem',
              margin: '0 auto 14px'
            }}>
              ⚠️
            </div>

            <h3 style={{ margin: '0 0 8px', color: '#0f172a', fontSize: '1.15rem', fontWeight: 800 }}>
              Remove Suki Relationship?
            </h3>
            <p style={{ margin: '0 0 20px', color: '#64748b', fontSize: '0.85rem', lineHeight: '1.4' }}>
              Are you sure you want to remove <strong>{removingSuki.app_users?.name || 'this customer'}</strong> from{' '}
              <strong>{store.name}</strong>? They will no longer have suki access to this store.
            </p>

            <div style={{ display: 'flex', gap: '10px', justifyContent: 'center' }}>
              <button
                onClick={() => setRemovingSuki(null)}
                className="admin-btn-action"
                style={{ padding: '8px 16px', fontSize: '0.85rem' }}
              >
                Cancel
              </button>
              <button
                onClick={handleConfirmRemove}
                disabled={actionLoadingId !== null}
                style={{
                  backgroundColor: '#ef4444',
                  color: '#ffffff',
                  border: 'none',
                  borderRadius: '8px',
                  padding: '8px 18px',
                  fontWeight: 600,
                  fontSize: '0.85rem',
                  cursor: 'pointer',
                  boxShadow: '0 2px 4px rgba(239, 68, 68, 0.2)'
                }}
              >
                {actionLoadingId ? 'Removing...' : 'Yes, Remove Suki'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
