import React, { useState, useEffect } from 'react';
import { supabase } from '../../config/supabaseClient';
import StoreProductManager from './StoreProductManager';

export default function StandardPriceStores() {
  const [stores, setStores] = useState([]);
  const [loading, setLoading] = useState(true);
  const [managingStore, setManagingStore] = useState(null); // Selected store object for managing products
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [storeName, setStoreName] = useState('');
  const [storeType, setStoreType] = useState('Grocery');
  const [customType, setCustomType] = useState('');
  const [storeStatus, setStoreStatus] = useState('private'); // 'private' or 'public'
  const [actionLoading, setActionLoading] = useState(null);

  // Edit Store states
  const [editingStore, setEditingStore] = useState(null);
  const [showEditModal, setShowEditModal] = useState(false);
  const [editStoreName, setEditStoreName] = useState('');
  const [editStoreType, setEditStoreType] = useState('Grocery');
  const [editCustomType, setEditCustomType] = useState('');

  const loadStandardStores = async () => {
    try {
      setLoading(true);
      const { data, error } = await supabase
        .from('stores')
        .select('*')
        .eq('is_standard_store', true)
        .order('name', { ascending: true });

      if (error) throw error;
      setStores(data || []);
    } catch (err) {
      console.error('Failed to load standard reference stores:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadStandardStores();
  }, []);

  const handleCreateStore = async (e) => {
    e.preventDefault();
    if (!storeName.trim()) return;

    const finalType = storeType === 'Other' ? customType.trim() : storeType;
    if (!finalType) {
      alert('Please specify a store type.');
      return;
    }

    const isPublic = storeStatus === 'public';

    try {
      setActionLoading('create');
      

      const { data, error } = await supabase
        .from('stores')
        .insert({
          name: storeName.trim(),
          branch: 'Presyohan', // system generated branch name
          type: finalType.toLowerCase(),
          is_standard_store: true,
          is_public: isPublic
        })
        .select();

      if (error) throw error;

      setShowCreateModal(false);
      setStoreName('');
      setStoreType('Grocery');
      setCustomType('');
      setStoreStatus('private');
      await loadStandardStores();
    } catch (err) {
      console.error('Failed to create standard store:', err);
      alert('Error creating store: ' + err.message);
    } finally {
      setActionLoading(null);
    }
  };

  const handleEditStore = (store) => {
    setEditingStore(store);
    setEditStoreName(store.name);
    
    const standardTypes = ['grocery', 'pharmacy', 'laundry'];
    const lowerType = (store.type || '').toLowerCase();
    if (standardTypes.includes(lowerType)) {
      const capType = lowerType.charAt(0).toUpperCase() + lowerType.slice(1);
      setEditStoreType(capType);
      setEditCustomType('');
    } else {
      setEditStoreType('Other');
      setEditCustomType(store.type || '');
    }
    
    setShowEditModal(true);
  };

  const handleUpdateStoreSubmit = async (e) => {
    e.preventDefault();
    if (!editStoreName.trim()) return;

    const finalType = editStoreType === 'Other' ? editCustomType.trim() : editStoreType;
    if (!finalType) {
      alert('Please specify a store type.');
      return;
    }

    try {
      setActionLoading(`update-${editingStore.id}`);
      
      const { error } = await supabase
        .from('stores')
        .update({
          name: editStoreName.trim(),
          type: finalType.toLowerCase()
        })
        .eq('id', editingStore.id);

      if (error) throw error;

      setShowEditModal(false);
      setEditingStore(null);
      await loadStandardStores();
    } catch (err) {
      console.error('Failed to update standard store:', err);
      alert('Error updating store: ' + err.message);
    } finally {
      setActionLoading(null);
    }
  };

  const handleUpdateStatus = async (storeId, option) => {
    try {
      setActionLoading(`status-${storeId}`);
      
      const isPublic = option === 'public';
      
      const { error } = await supabase.rpc('update_standard_store_status', {
        p_store_id: storeId,
        p_is_public: isPublic
      });
      
      if (error) throw error;
      
      await loadStandardStores();
    } catch (err) {
      console.error('Failed to update status:', err);
      alert('Error updating status: ' + err.message);
    } finally {
      setActionLoading(null);
    }
  };

  const handleDeleteStore = async (store) => {
    if (!window.confirm(`Are you sure you want to permanently delete the standard price store "${store.name}"?`)) {
      return;
    }

    try {
      setActionLoading(`delete-${store.id}`);
      const { error } = await supabase
        .from('stores')
        .delete()
        .eq('id', store.id);

      if (error) throw error;
      setStores(prev => prev.filter(s => s.id !== store.id));
    } catch (err) {
      console.error('Failed to delete standard store:', err);
      alert('Error deleting store: ' + err.message);
    } finally {
      setActionLoading(null);
    }
  };

  if (managingStore) {
    return (
      <StoreProductManager 
        store={managingStore} 
        onBack={() => {
          setManagingStore(null);
          loadStandardStores();
        }} 
      />
    );
  }

  if (loading) {
    return (
      <div style={{ padding: '40px', textAlign: 'center', color: '#64748b' }}>
        Loading standard price reference stores...
      </div>
    );
  }

  return (
    <div>
      <div className="admin-table-controls" style={{ justifyContent: 'space-between' }}>
        <p style={{ color: '#64748b', fontSize: '0.9rem', margin: 0 }}>
          Standard Price Stores act as baseline reference pricing for items across the application. Users search these reference lists when checking regular prices.
        </p>
        <button className="admin-btn-primary" onClick={() => setShowCreateModal(true)}>
          Create Standard Store
        </button>
      </div>

      <div className="admin-table-container">
        <table className="admin-table">
          <thead>
            <tr>
              <th>Store Name & Type</th>
              <th>Status</th>
              <th>Items</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {stores.map((store) => (
              <tr key={store.id}>
                <td>
                  <div style={{ display: 'flex', flexDirection: 'column' }}>
                    <span style={{ fontWeight: 600, color: '#0f172a' }}>{store.name}</span>
                    <span style={{ fontSize: '0.8rem', color: '#64748b', marginTop: '2px', textTransform: 'capitalize' }}>
                      {store.type || 'Standard'}
                    </span>
                  </div>
                </td>
                <td>
                  <div style={{
                    display: 'inline-flex',
                    backgroundColor: '#f1f5f9',
                    padding: '3px',
                    borderRadius: '8px',
                    border: '1px solid #e2e8f0',
                    gap: '2px'
                  }}>
                    <button
                      disabled={actionLoading !== null}
                      onClick={() => handleUpdateStatus(store.id, 'private')}
                      style={{
                        padding: '4px 10px',
                        borderRadius: '6px',
                        fontSize: '0.72rem',
                        fontWeight: 600,
                        cursor: 'pointer',
                        border: 'none',
                        backgroundColor: !store.is_public ? '#ffffff' : 'transparent',
                        color: !store.is_public ? '#64748b' : '#94a3b8',
                        boxShadow: !store.is_public ? '0 1px 3px rgba(0,0,0,0.1)' : 'none',
                        transition: 'all 0.2s ease'
                      }}
                    >
                      Private
                    </button>
                    <button
                      disabled={actionLoading !== null}
                      onClick={() => handleUpdateStatus(store.id, 'public')}
                      style={{
                        padding: '4px 10px',
                        borderRadius: '6px',
                        fontSize: '0.72rem',
                        fontWeight: 600,
                        cursor: 'pointer',
                        border: 'none',
                        backgroundColor: store.is_public ? '#ffffff' : 'transparent',
                        color: store.is_public ? '#ff8c00' : '#94a3b8',
                        boxShadow: store.is_public ? '0 1px 3px rgba(0,0,0,0.1)' : 'none',
                        transition: 'all 0.2s ease'
                      }}
                    >
                      Public
                    </button>
                  </div>
                </td>
                <td>
                  <button
                    className="admin-btn-action"
                    style={{ color: '#ff8c00', borderColor: 'rgba(255, 140, 0, 0.2)' }}
                    onClick={() => setManagingStore(store)}
                  >
                    Manage Products
                  </button>
                </td>
                <td>
                  <div className="admin-actions-cell">
                    <button
                      className="admin-btn-action"
                      style={{ color: '#ff8c00', borderColor: 'rgba(255, 140, 0, 0.2)' }}
                      onClick={() => handleEditStore(store)}
                    >
                      Edit
                    </button>
                    <button
                      className="admin-btn-action suspend"
                      disabled={actionLoading !== null}
                      onClick={() => handleDeleteStore(store)}
                    >
                      {actionLoading === `delete-${store.id}` ? 'Deleting...' : 'Delete'}
                    </button>
                  </div>
                </td>
              </tr>
            ))}
            {stores.length === 0 && (
              <tr>
                <td colSpan="4" style={{ textAlign: 'center', color: '#94a3b8', padding: '32px' }}>
                  No standard reference stores defined. Create one to begin cataloging reference prices.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* Create Modal */}
      {showCreateModal && (
        <div style={{
          position: 'fixed',
          inset: 0,
          backgroundColor: 'rgba(0,0,0,0.4)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 100,
          fontFamily: 'Outfit'
        }}>
          <div className="admin-card" style={{ width: '400px', padding: '28px' }}>
            <h3 style={{ marginBottom: '16px' }}>New Standard Store</h3>
            <form onSubmit={handleCreateStore}>
              <div style={{ marginBottom: '16px' }}>
                <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#475569', marginBottom: '6px' }}>Store Name</label>
                <input
                  type="text"
                  required
                  className="admin-search-input"
                  style={{ paddingLeft: '16px' }}
                  placeholder="e.g. Presyohan Standard Catalog"
                  value={storeName}
                  onChange={(e) => setStoreName(e.target.value)}
                />
              </div>
              <div style={{ marginBottom: '16px' }}>
                <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#475569', marginBottom: '6px' }}>Store Type</label>
                <select
                  className="admin-select"
                  style={{ width: '100%', paddingLeft: '12px', height: '42px' }}
                  value={storeType}
                  onChange={(e) => setStoreType(e.target.value)}
                >
                  <option value="Grocery">Grocery</option>
                  <option value="Pharmacy">Pharmacy</option>
                  <option value="Laundry">Laundry</option>
                  <option value="Other">Other (specify)</option>
                </select>
              </div>
              {storeType === 'Other' && (
                <div style={{ marginBottom: '24px' }}>
                  <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#475569', marginBottom: '6px' }}>Specify Store Type</label>
                  <input
                    type="text"
                    required
                    className="admin-search-input"
                    style={{ paddingLeft: '16px' }}
                    placeholder="e.g. Hardware"
                    value={customType}
                    onChange={(e) => setCustomType(e.target.value)}
                  />
                </div>
              )}
              <div style={{ marginBottom: '24px' }}>
                <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#475569', marginBottom: '6px' }}>Visibility & Status</label>
                <div style={{
                  display: 'flex',
                  backgroundColor: '#f1f5f9',
                  padding: '4px',
                  borderRadius: '8px',
                  border: '1px solid #e2e8f0',
                  gap: '4px'
                }}>
                  <button
                    type="button"
                    onClick={() => setStoreStatus('private')}
                    style={{
                      flex: 1,
                      padding: '8px 4px',
                      borderRadius: '6px',
                      fontSize: '0.75rem',
                      fontWeight: 600,
                      cursor: 'pointer',
                      border: 'none',
                      backgroundColor: storeStatus === 'private' ? '#ffffff' : 'transparent',
                      color: storeStatus === 'private' ? '#64748b' : '#94a3b8',
                      boxShadow: storeStatus === 'private' ? '0 1px 3px rgba(0,0,0,0.1)' : 'none',
                      transition: 'all 0.2s ease'
                    }}
                  >
                    Private
                  </button>
                  <button
                    type="button"
                    onClick={() => setStoreStatus('public')}
                    style={{
                      flex: 1,
                      padding: '8px 4px',
                      borderRadius: '6px',
                      fontSize: '0.75rem',
                      fontWeight: 600,
                      cursor: 'pointer',
                      border: 'none',
                      backgroundColor: storeStatus === 'public' ? '#ffffff' : 'transparent',
                      color: storeStatus === 'public' ? '#ff8c00' : '#94a3b8',
                      boxShadow: storeStatus === 'public' ? '0 1px 3px rgba(0,0,0,0.1)' : 'none',
                      transition: 'all 0.2s ease'
                    }}
                  >
                    Public
                  </button>
                </div>
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '24px' }}>
                <button
                  type="button"
                  className="admin-btn-action"
                  onClick={() => {
                    setShowCreateModal(false);
                    setStoreName('');
                    setStoreType('Grocery');
                    setCustomType('');
                    setStoreStatus('private');
                  }}
                >
                  Cancel
                </button>
                <button type="submit" className="admin-btn-primary" disabled={actionLoading === 'create'}>
                  {actionLoading === 'create' ? 'Creating...' : 'Create Store'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Edit Modal */}
      {showEditModal && (
        <div style={{
          position: 'fixed',
          inset: 0,
          backgroundColor: 'rgba(0,0,0,0.4)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 100,
          fontFamily: 'Outfit'
        }}>
          <div className="admin-card" style={{ width: '400px', padding: '28px' }}>
            <h3 style={{ marginBottom: '16px' }}>Edit Standard Store</h3>
            <form onSubmit={handleUpdateStoreSubmit}>
              <div style={{ marginBottom: '16px' }}>
                <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#475569', marginBottom: '6px' }}>Store Name</label>
                <input
                  type="text"
                  required
                  className="admin-search-input"
                  style={{ paddingLeft: '16px' }}
                  placeholder="e.g. Presyohan Standard Catalog"
                  value={editStoreName}
                  onChange={(e) => setEditStoreName(e.target.value)}
                />
              </div>
              <div style={{ marginBottom: '24px' }}>
                <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#475569', marginBottom: '6px' }}>Store Type</label>
                <select
                  className="admin-select"
                  style={{ width: '100%', paddingLeft: '12px', height: '42px' }}
                  value={editStoreType}
                  onChange={(e) => setEditStoreType(e.target.value)}
                >
                  <option value="Grocery">Grocery</option>
                  <option value="Pharmacy">Pharmacy</option>
                  <option value="Laundry">Laundry</option>
                  <option value="Other">Other (specify)</option>
                </select>
              </div>
              {editStoreType === 'Other' && (
                <div style={{ marginBottom: '24px' }}>
                  <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#475569', marginBottom: '6px' }}>Specify Store Type</label>
                  <input
                    type="text"
                    required
                    className="admin-search-input"
                    style={{ paddingLeft: '16px' }}
                    placeholder="e.g. Hardware"
                    value={editCustomType}
                    onChange={(e) => setEditCustomType(e.target.value)}
                  />
                </div>
              )}
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '24px' }}>
                <button
                  type="button"
                  className="admin-btn-action"
                  onClick={() => {
                    setShowEditModal(false);
                    setEditingStore(null);
                  }}
                >
                  Cancel
                </button>
                <button type="submit" className="admin-btn-primary" disabled={actionLoading !== null}>
                  {actionLoading === `update-${editingStore?.id}` ? 'Saving...' : 'Save Changes'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}