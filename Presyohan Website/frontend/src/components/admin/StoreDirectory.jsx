import React, { useState, useEffect } from 'react';
import { supabase } from '../../config/supabaseClient';

export default function StoreDirectory() {
  const [stores, setStores] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [typeFilter, setTypeFilter] = useState('all');
  const [actionLoading, setActionLoading] = useState(null);
  const [openOwnerDropdownId, setOpenOwnerDropdownId] = useState(null);

  // Close the owner dropdown when clicking anywhere else
  useEffect(() => {
    const handleOutsideClick = () => {
      setOpenOwnerDropdownId(null);
    };
    window.addEventListener('click', handleOutsideClick);
    return () => window.removeEventListener('click', handleOutsideClick);
  }, []);

  const loadStores = async () => {
    try {
      setLoading(true);
      
      // Fetch stores sorted by creation date to calculate display ID sequence correctly
      const { data, error } = await supabase
        .from('stores')
        .select(`
          id,
          display_id,
          name,
          branch,
          invite_code,
          invite_code_created_at,
          paste_code,
          paste_code_expires_at,
          type,
          is_standard_store,
          is_default_standard,
          created_at,
          is_public,
          store_members (
            role,
            app_users (
              name,
              email
            )
          ),
          suki_relationships (count),
          products (count),
          categories (count)
        `)
        .order('created_at', { ascending: true });

      if (error) throw error;

      // Group and calculate sequence numbers for chronological fallback display IDs
      const yearCounts = {};
      const fallbackDisplayIds = {};
      
      (data || []).forEach(store => {
        const year = new Date(store.created_at || Date.now()).getFullYear().toString().slice(-2);
        if (!yearCounts[year]) {
          yearCounts[year] = 0;
        }
        yearCounts[year]++;
        const seqStr = String(yearCounts[year]).padStart(4, '0');
        fallbackDisplayIds[store.id] = `SID${year}-${seqStr}`;
      });
      
      // Process data for cleaner rendering
      const processed = (data || []).map(store => {
        const members = store.store_members || [];
        const sukiCount = store.suki_relationships?.[0]?.count || 0;
        const productCount = store.products?.[0]?.count || 0;
        const categoryCount = store.categories?.[0]?.count || 0;

        return {
          id: store.id,
          displayId: store.display_id || fallbackDisplayIds[store.id],
          name: store.name,
          branch: store.branch || 'Main',
          inviteCode: store.invite_code || '',
          inviteCodeCreatedAt: store.invite_code_created_at,
          pasteCode: store.paste_code || '',
          pasteCodeExpiresAt: store.paste_code_expires_at,
          type: store.type || 'grocery',
          isStandard: store.is_standard_store || false,
          isDefaultStandard: store.is_default_standard || false,
          isPublic: store.is_public || false,
          createdAt: store.created_at,
          members,
          memberCount: members.length,
          sukiCount,
          productCount,
          categoryCount
        };
      });

      // Sort alphabetically by name for final directory listing display
      processed.sort((a, b) => a.name.localeCompare(b.name));

      setStores(processed);
    } catch (err) {
      console.error('Failed to load stores directory:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadStores();
  }, []);

  const handleDeleteStore = async (store) => {
    const confirmMsg = `WARNING: Are you sure you want to permanently delete the store "${store.name} (${store.branch})"? This will delete all products, categories, inventories, and memberships inside this store. This action CANNOT be undone.`;
    
    if (!window.confirm(confirmMsg)) return;

    try {
      setActionLoading(store.id);
      const { error } = await supabase
        .from('stores')
        .delete()
        .eq('id', store.id);

      if (error) throw error;

      // Update local state
      setStores(prev => prev.filter(s => s.id !== store.id));
    } catch (err) {
      console.error('Failed to delete store:', err);
      alert('Error deleting store: ' + err.message);
    } finally {
      setActionLoading(null);
    }
  };

  // Helper to format date as MM/dd/yy
  const formatDate = (isoString) => {
    if (!isoString) return '--/--/--';
    try {
      const date = new Date(isoString);
      const mm = String(date.getMonth() + 1).padStart(2, '0');
      const dd = String(date.getDate()).padStart(2, '0');
      const yy = String(date.getFullYear()).slice(-2);
      return `${mm}/${dd}/${yy}`;
    } catch (e) {
      return '--/--/--';
    }
  };

  // Helper to check invite code expiry (24 hours)
  const getInviteCodeDetails = (code, createdAt) => {
    if (!code) return { code: 'N/A', isExpired: false };
    if (!createdAt) return { code, isExpired: true };
    const createdDate = new Date(createdAt);
    const isExpired = Date.now() - createdDate.getTime() > 24 * 60 * 60 * 1000;
    return { code, isExpired };
  };

  // Helper to check paste code expiry
  const getPasteCodeDetails = (code, expiresAt) => {
    if (!code) return { code: 'N/A', isExpired: false };
    if (!expiresAt) return { code, isExpired: true };
    const expiryDate = new Date(expiresAt);
    const isExpired = Date.now() > expiryDate.getTime();
    return { code, isExpired };
  };

  // Filter stores
  const filteredStores = stores.filter(store => {
    const searchLower = searchQuery.toLowerCase();
    
    // Match name, branch or database displayId
    const nameMatch = store.name.toLowerCase().includes(searchLower) ||
                      store.branch.toLowerCase().includes(searchLower) ||
                      (store.displayId || '').toLowerCase().includes(searchLower);
                      
    // Match any member name or email
    const memberMatch = store.members.some(m => 
      (m.app_users?.name || '').toLowerCase().includes(searchLower) ||
      (m.app_users?.email || '').toLowerCase().includes(searchLower)
    );

    const matchesSearch = nameMatch || memberMatch;

    let matchesType = true;
    if (typeFilter === 'standard') {
      matchesType = store.isStandard === true;
    } else if (typeFilter === 'user') {
      matchesType = store.isStandard !== true;
    }

    return matchesSearch && matchesType;
  });

  if (loading) {
    return (
      <div style={{ padding: '40px', textAlign: 'center', color: '#64748b' }}>
        Loading stores directory...
      </div>
    );
  }

  return (
    <div>
      <div className="admin-table-controls">
        <div className="admin-search-wrapper">
          <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
          <input
            type="text"
            className="admin-search-input"
            placeholder="Search stores by name, branch, ID, owner..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>

        <div>
          <select
            className="admin-select"
            value={typeFilter}
            onChange={(e) => setTypeFilter(e.target.value)}
          >
            <option value="all">All Store Types</option>
            <option value="user">User Created Stores</option>
            <option value="standard">Standard Reference Stores</option>
          </select>
        </div>
      </div>

      <div className="admin-table-container">
        <table className="admin-table">
          <thead>
            <tr>
              <th>Store Details & ID</th>
              <th>Status & Type</th>
              <th>Owner Name</th>
              <th>Stats</th>
              <th>Invite Code</th>
              <th>Paste Code</th>
              <th>Created At</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {filteredStores.map((store) => {
              // 1. Resolve Owners / Members info
              const owners = store.members
                .filter(m => m.role === 'owner')
                .map(o => o.app_users?.name || 'Unnamed User');

              let ownerDisplay = null;
              if (owners.length > 0) {
                ownerDisplay = {
                  primary: owners[0],
                  others: owners.slice(1)
                };
              } else if (store.members.length > 0) {
                const firstMember = store.members[0];
                ownerDisplay = {
                  fallback: true,
                  primary: firstMember.app_users?.name || 'Unnamed User',
                  others: []
                };
              }

              // 2. Resolve codes expiration
              const invite = getInviteCodeDetails(store.inviteCode, store.inviteCodeCreatedAt);
              const paste = getPasteCodeDetails(store.pasteCode, store.pasteCodeExpiresAt);

              return (
                <tr key={store.id}>
                  {/* Name & Branch & ID */}
                  <td>
                    <div style={{ display: 'flex', flexDirection: 'column' }}>
                      <span style={{ fontWeight: 600, color: '#0f172a' }}>{store.name}</span>
                      <span style={{ fontSize: '0.8rem', color: '#64748b', marginTop: '2px' }}>{store.branch}</span>
                      <code style={{ 
                        fontSize: '0.72rem', 
                        color: '#ff8c00', 
                        marginTop: '4px',
                        fontWeight: '600',
                        letterSpacing: '0.5px'
                      }}>
                        {store.displayId || 'N/A'}
                      </code>
                    </div>
                  </td>

                  {/* Status & Type */}
                  <td>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      {store.isPublic ? (
                        <span className="admin-badge active" style={{ fontSize: '0.68rem', width: 'fit-content' }}>
                          Public
                        </span>
                      ) : (
                        <span className="admin-badge" style={{ backgroundColor: '#f1f5f9', color: '#64748b', fontSize: '0.68rem', width: 'fit-content' }}>
                          Private
                        </span>
                      )}

                      {store.isStandard ? (
                        <span className="admin-badge admin" style={{ fontSize: '0.68rem', width: 'fit-content' }}>
                          Standard {store.isDefaultStandard && '• Default'}
                        </span>
                      ) : (
                        <span className="admin-badge member" style={{ fontSize: '0.68rem', width: 'fit-content' }}>
                          User Store
                        </span>
                      )}
                    </div>
                  </td>

                  {/* Owner Name */}
                  <td>
                    {!ownerDisplay ? (
                      <span style={{ color: '#94a3b8', fontSize: '0.8rem', fontStyle: 'italic' }}>No owners registered</span>
                    ) : (
                      <div style={{ position: 'relative' }}>
                        <span style={{ fontWeight: 500, fontSize: '0.85rem', color: '#0f172a' }}>
                          {ownerDisplay.primary}
                        </span>
                        
                        {ownerDisplay.others.length > 0 && (
                          <div style={{ marginTop: '6px' }}>
                            <button
                              onClick={(e) => {
                                e.stopPropagation();
                                setOpenOwnerDropdownId(openOwnerDropdownId === store.id ? null : store.id);
                              }}
                              style={{
                                background: 'rgba(255, 140, 0, 0.08)',
                                border: 'none',
                                borderRadius: '4px',
                                color: '#ff8c00',
                                padding: '3px 8px',
                                fontSize: '0.7rem',
                                cursor: 'pointer',
                                fontWeight: 600,
                                display: 'inline-flex',
                                alignItems: 'center'
                              }}
                            >
                              +{ownerDisplay.others.length} more owner{ownerDisplay.others.length > 1 ? 's' : ''} ▾
                            </button>
                            {openOwnerDropdownId === store.id && (
                              <div style={{
                                position: 'absolute',
                                top: '100%',
                                left: 0,
                                backgroundColor: '#ffffff',
                                border: '1px solid #e2e8f0',
                                borderRadius: '8px',
                                boxShadow: '0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05)',
                                padding: '6px 12px',
                                zIndex: 100,
                                minWidth: '160px'
                              }}>
                                {ownerDisplay.others.map((name, idx) => (
                                  <div key={idx} style={{ 
                                    padding: '6px 0', 
                                    fontSize: '0.8rem',
                                    color: '#334155',
                                    fontWeight: 500,
                                    borderBottom: idx < ownerDisplay.others.length - 1 ? '1px solid #f1f5f9' : 'none' 
                                  }}>
                                    {name}
                                  </div>
                                ))}
                              </div>
                            )}
                          </div>
                        )}
                      </div>
                    )}
                  </td>

                  {/* Statistics */}
                  <td>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '3px', fontSize: '0.8rem' }}>
                      <span><strong>Members:</strong> {store.memberCount}</span>
                      <span><strong>Mga Suki:</strong> {store.sukiCount}</span>
                      <span><strong>Categories:</strong> {store.categoryCount}</span>
                      <span><strong>Items:</strong> {store.productCount}</span>
                    </div>
                  </td>

                  {/* Invite Code */}
                  <td>
                    {invite.code === 'N/A' || !invite.code ? (
                      <span style={{ color: '#94a3b8', fontSize: '0.8rem' }}>N/A</span>
                    ) : invite.isExpired ? (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '3px' }}>
                        <code style={{ textDecoration: 'line-through', color: '#94a3b8', fontSize: '0.85rem' }}>{invite.code}</code>
                        <span className="admin-badge" style={{ backgroundColor: '#fee2e2', color: '#ef4444', fontSize: '0.65rem', padding: '1px 6px', display: 'inline-block', width: 'fit-content' }}>Expired</span>
                      </div>
                    ) : (
                      <code style={{ color: '#0f172a', fontWeight: '700', fontSize: '0.85rem' }}>{invite.code}</code>
                    )}
                  </td>

                  {/* Paste Code */}
                  <td>
                    {paste.code === 'N/A' || !paste.code ? (
                      <span style={{ color: '#94a3b8', fontSize: '0.8rem' }}>N/A</span>
                    ) : paste.isExpired ? (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '3px' }}>
                        <code style={{ textDecoration: 'line-through', color: '#94a3b8', fontSize: '0.85rem' }}>{paste.code}</code>
                        <span className="admin-badge" style={{ backgroundColor: '#fee2e2', color: '#ef4444', fontSize: '0.65rem', padding: '1px 6px', display: 'inline-block', width: 'fit-content' }}>Expired</span>
                      </div>
                    ) : (
                      <code style={{ color: '#0f172a', fontWeight: '700', fontSize: '0.85rem' }}>{paste.code}</code>
                    )}
                  </td>

                  {/* Created At */}
                  <td style={{ fontSize: '0.85rem', color: '#475569' }}>
                    {formatDate(store.createdAt)}
                  </td>

                  {/* Actions */}
                  <td>
                    <div className="admin-actions-cell">
                      <button
                        className="admin-btn-action suspend"
                        disabled={actionLoading === store.id}
                        onClick={() => handleDeleteStore(store)}
                      >
                        {actionLoading === store.id ? 'Deleting...' : 'Delete'}
                      </button>
                    </div>
                  </td>
                </tr>
              );
            })}
            {filteredStores.length === 0 && (
              <tr>
                <td colSpan="8" style={{ textAlign: 'center', color: '#94a3b8', padding: '32px' }}>
                  No stores found matching the search criteria.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
