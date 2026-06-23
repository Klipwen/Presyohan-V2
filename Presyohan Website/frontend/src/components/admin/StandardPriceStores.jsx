import React, { useState, useEffect } from 'react';
import { supabase } from '../../config/supabaseClient';

export default function StandardPriceStores() {
  const [stores, setStores] = useState([]);
  const [loading, setLoading] = useState(true);
  const [managingStore, setManagingStore] = useState(null); // Selected store object for managing products
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [storeName, setStoreName] = useState('');
  const [storeType, setStoreType] = useState('Grocery');
  const [customType, setCustomType] = useState('');
  const [storeStatus, setStoreStatus] = useState('private'); // 'private', 'public', or 'starter'
  const [actionLoading, setActionLoading] = useState(null);

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

    const isPublic = storeStatus !== 'private';
    const isDefaultStandard = storeStatus === 'starter';

    try {
      setActionLoading('create');
      

      const { data, error } = await supabase
        .from('stores')
        .insert({
          name: storeName.trim(),
          branch: 'Presyohan', // system generated branch name
          type: finalType.toLowerCase(),
          is_standard_store: true,
          is_public: isPublic,
          is_default_standard: isDefaultStandard
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

  const handleUpdateStatus = async (storeId, option) => {
    try {
      setActionLoading(`status-${storeId}`);
      
      const isPublic = option !== 'private';
      const isDefaultStandard = option === 'starter';
      
      const { error } = await supabase.rpc('update_standard_store_status', {
        p_store_id: storeId,
        p_is_public: isPublic,
        p_is_default_standard: isDefaultStandard
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
    if (store.is_default_standard) {
      alert('Security lock: Cannot delete the active default standard store. Please designate another store as default first.');
      return;
    }
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
      <StandardStoreProductManager 
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
              <th>Store Name & Branch</th>
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
                    <span style={{ fontSize: '0.8rem', color: '#64748b', marginTop: '2px' }}>{store.branch}</span>
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
                        backgroundColor: (!store.is_public && !store.is_default_standard) ? '#ffffff' : 'transparent',
                        color: (!store.is_public && !store.is_default_standard) ? '#64748b' : '#94a3b8',
                        boxShadow: (!store.is_public && !store.is_default_standard) ? '0 1px 3px rgba(0,0,0,0.1)' : 'none',
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
                        backgroundColor: (store.is_public && !store.is_default_standard) ? '#ffffff' : 'transparent',
                        color: (store.is_public && !store.is_default_standard) ? '#ff8c00' : '#94a3b8',
                        boxShadow: (store.is_public && !store.is_default_standard) ? '0 1px 3px rgba(0,0,0,0.1)' : 'none',
                        transition: 'all 0.2s ease'
                      }}
                    >
                      Public
                    </button>
                    <button
                      disabled={actionLoading !== null}
                      onClick={() => handleUpdateStatus(store.id, 'starter')}
                      style={{
                        padding: '4px 10px',
                        borderRadius: '6px',
                        fontSize: '0.72rem',
                        fontWeight: 600,
                        cursor: 'pointer',
                        border: 'none',
                        backgroundColor: store.is_default_standard ? '#ffffff' : 'transparent',
                        color: store.is_default_standard ? '#10b981' : '#94a3b8',
                        boxShadow: store.is_default_standard ? '0 1px 3px rgba(0,0,0,0.1)' : 'none',
                        transition: 'all 0.2s ease'
                      }}
                    >
                      Starter Store
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
                  <button
                    type="button"
                    onClick={() => setStoreStatus('starter')}
                    style={{
                      flex: 1,
                      padding: '8px 4px',
                      borderRadius: '6px',
                      fontSize: '0.75rem',
                      fontWeight: 600,
                      cursor: 'pointer',
                      border: 'none',
                      backgroundColor: storeStatus === 'starter' ? '#ffffff' : 'transparent',
                      color: storeStatus === 'starter' ? '#10b981' : '#94a3b8',
                      boxShadow: storeStatus === 'starter' ? '0 1px 3px rgba(0,0,0,0.1)' : 'none',
                      transition: 'all 0.2s ease'
                    }}
                  >
                    Starter Store
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
    </div>
  );
}

/* SUBCOMPONENT: Category and Product Database Manager */
function StandardStoreProductManager({ store, onBack }) {
  const [categories, setCategories] = useState([]);
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedCategory, setSelectedCategory] = useState('ALL');
  
  // Modals / forms
  const [showAddProduct, setShowAddProduct] = useState(false);
  const [showAddCategory, setShowAddCategory] = useState(false);
  const [newCategoryName, setNewCategoryName] = useState('');
  const [editingProduct, setEditingProduct] = useState(null);

  // Form states
  const [prodName, setProdName] = useState('');
  const [prodPrice, setProdPrice] = useState('');
  const [prodUnit, setProdUnit] = useState('pc');
  const [prodDesc, setProdDesc] = useState('');
  const [prodCatId, setProdCatId] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

  const loadData = async () => {
    try {
      setLoading(true);

      // 1. Fetch categories
      const { data: cats, error: catsErr } = await supabase
        .from('categories')
        .select('*')
        .eq('store_id', store.id)
        .order('name', { ascending: true });

      if (catsErr) throw catsErr;
      setCategories(cats || []);

      // 2. Fetch products
      const { data: prods, error: prodsErr } = await supabase
        .from('products')
        .select('*')
        .eq('store_id', store.id)
        .order('name', { ascending: true });

      if (prodsErr) throw prodsErr;
      setProducts(prods || []);
    } catch (err) {
      console.error('Failed to load products/categories:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [store.id]);

  const handleAddCategory = async (e) => {
    e.preventDefault();
    if (!newCategoryName.trim()) return;

    try {
      setActionLoading(true);
      const uppercaseName = newCategoryName.trim().toUpperCase();

      // Check duplicate
      if (categories.some(c => c.name === uppercaseName)) {
        alert('A category with this name already exists.');
        return;
      }

      const { error } = await supabase
        .from('categories')
        .insert({
          store_id: store.id,
          name: uppercaseName
        });

      if (error) throw error;
      setNewCategoryName('');
      setShowAddCategory(false);
      await loadData();
    } catch (err) {
      console.error(err);
      alert('Error: ' + err.message);
    } finally {
      setActionLoading(false);
    }
  };

  const handleDeleteCategory = async (catId, catName) => {
    if (!window.confirm(`Are you sure you want to delete the category "${catName}"? This will also delete all items cataloged under this category.`)) {
      return;
    }

    try {
      const { error } = await supabase
        .from('categories')
        .delete()
        .eq('id', catId);

      if (error) throw error;
      setSelectedCategory('ALL');
      await loadData();
    } catch (err) {
      console.error(err);
      alert('Error: ' + err.message);
    }
  };

  const handleProductSubmit = async (e) => {
    e.preventDefault();
    if (!prodName.trim() || !prodPrice) return;

    try {
      setActionLoading(true);
      const payload = {
        store_id: store.id,
        name: prodName.trim(),
        price: parseFloat(prodPrice),
        unit: prodUnit,
        description: prodDesc.trim() || null,
        category_id: prodCatId || null,
        is_public: true // Standard store items are always public
      };

      let query;
      if (editingProduct) {
        query = supabase
          .from('products')
          .update(payload)
          .eq('id', editingProduct.id);
      } else {
        query = supabase
          .from('products')
          .insert(payload);
      }

      const { error } = await query;
      if (error) throw error;

      setShowAddProduct(false);
      setEditingProduct(null);
      setProdName('');
      setProdPrice('');
      setProdUnit('pc');
      setProdDesc('');
      setProdCatId('');
      await loadData();
    } catch (err) {
      console.error(err);
      alert('Error adding/updating product: ' + err.message);
    } finally {
      setActionLoading(false);
    }
  };

  const handleEditProduct = (prod) => {
    setEditingProduct(prod);
    setProdName(prod.name);
    setProdPrice(prod.price.toString());
    setProdUnit(prod.unit || 'pc');
    setProdDesc(prod.description || '');
    setProdCatId(prod.category_id || '');
    setShowAddProduct(true);
  };

  const handleDeleteProduct = async (prodId, prodName) => {
    if (!window.confirm(`Are you sure you want to delete "${prodName}"?`)) return;

    try {
      const { error } = await supabase
        .from('products')
        .delete()
        .eq('id', prodId);

      if (error) throw error;
      await loadData();
    } catch (err) {
      console.error(err);
      alert('Error: ' + err.message);
    }
  };

  // Filter products by selected category
  const filteredProducts = products.filter(p => {
    if (selectedCategory === 'ALL') return true;
    if (selectedCategory === 'UNCATEGORIZED') return !p.category_id;
    return p.category_id === selectedCategory;
  });

  if (loading) {
    return (
      <div style={{ padding: '40px', textAlign: 'center', color: '#64748b' }}>
        Loading catalog inventory...
      </div>
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '24px' }}>
        <button className="admin-btn-action" onClick={onBack}>
          ← Back to Stores
        </button>
        <h2 style={{ fontSize: '1.25rem', fontWeight: 700, margin: 0 }}>
          {store.name} Catalog
        </h2>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '220px 1fr', gap: '28px', alignItems: 'start' }}>
        {/* Category List Panel */}
        <div style={{ background: '#ffffff', borderRadius: '16px', border: '1px solid rgba(0,0,0,0.04)', padding: '16px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px' }}>
            <h4 style={{ margin: 0, fontSize: '0.85rem', color: '#475569', textTransform: 'uppercase', letterSpacing: '0.5px' }}>Categories</h4>
            <button 
              className="admin-btn-action"
              style={{ padding: '4px 8px', fontSize: '0.75rem', color: '#ff8c00', borderColor: 'rgba(255,140,0,0.2)' }}
              onClick={() => setShowAddCategory(true)}
            >
              + Add
            </button>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
            <button
              className={`admin-menu-item ${selectedCategory === 'ALL' ? 'active' : ''}`}
              style={{ padding: '10px 12px', fontSize: '0.85rem' }}
              onClick={() => setSelectedCategory('ALL')}
            >
              All Items ({products.length})
            </button>
            
            {categories.map(c => {
              const count = products.filter(p => p.category_id === c.id).length;
              return (
                <div key={c.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', group: 'true' }}>
                  <button
                    className={`admin-menu-item ${selectedCategory === c.id ? 'active' : ''}`}
                    style={{ padding: '10px 12px', fontSize: '0.85rem', flex: 1, textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}
                    onClick={() => setSelectedCategory(c.id)}
                  >
                    {c.name} ({count})
                  </button>
                  <button 
                    style={{ background: 'none', border: 'none', color: '#ef4444', cursor: 'pointer', padding: '0 8px', fontSize: '0.9rem' }}
                    onClick={() => handleDeleteCategory(c.id, c.name)}
                    title="Delete Category"
                  >
                    ×
                  </button>
                </div>
              );
            })}

            <button
              className={`admin-menu-item ${selectedCategory === 'UNCATEGORIZED' ? 'active' : ''}`}
              style={{ padding: '10px 12px', fontSize: '0.85rem' }}
              onClick={() => setSelectedCategory('UNCATEGORIZED')}
            >
              Uncategorized ({products.filter(p => !p.category_id).length})
            </button>
          </div>
        </div>

        {/* Products Table Panel */}
        <div className="admin-card" style={{ padding: '24px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
            <h4 style={{ margin: 0, fontSize: '1rem', fontWeight: 700 }}>
              Products List ({filteredProducts.length})
            </h4>
            <button className="admin-btn-primary" onClick={() => {
              setEditingProduct(null);
              setProdName('');
              setProdPrice('');
              setProdUnit('pc');
              setProdDesc('');
              setProdCatId(selectedCategory !== 'ALL' && selectedCategory !== 'UNCATEGORIZED' ? selectedCategory : '');
              setShowAddProduct(true);
            }}>
              Add Product
            </button>
          </div>

          <div className="admin-table-container">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Product Name</th>
                  <th>Price</th>
                  <th>Unit</th>
                  <th>Description</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredProducts.map(p => (
                  <tr key={p.id}>
                    <td style={{ fontWeight: 600 }}>{p.name}</td>
                    <td style={{ color: '#ff8c00', fontWeight: 700 }}>₱{Number(p.price).toFixed(2)}</td>
                    <td>{p.unit || 'pc'}</td>
                    <td style={{ color: '#64748b', fontSize: '0.8rem' }}>{p.description || '—'}</td>
                    <td>
                      <div className="admin-actions-cell">
                        <button className="admin-btn-action" onClick={() => handleEditProduct(p)}>
                          Edit
                        </button>
                        <button className="admin-btn-action suspend" onClick={() => handleDeleteProduct(p.id, p.name)}>
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
                {filteredProducts.length === 0 && (
                  <tr>
                    <td colSpan="5" style={{ textAlign: 'center', color: '#94a3b8', padding: '32px' }}>
                      No products found in this category. Click 'Add Product' to insert one.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* Add/Edit Product Modal */}
      {showAddProduct && (
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
          <div className="admin-card" style={{ width: '450px', padding: '28px' }}>
            <h3 style={{ marginBottom: '16px' }}>{editingProduct ? 'Edit Product' : 'Add New Product'}</h3>
            <form onSubmit={handleProductSubmit}>
              <div style={{ marginBottom: '12px' }}>
                <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#475569', marginBottom: '6px' }}>Product Name</label>
                <input
                  type="text"
                  required
                  className="admin-search-input"
                  style={{ paddingLeft: '16px' }}
                  placeholder="e.g. Premium White Rice"
                  value={prodName}
                  onChange={(e) => setProdName(e.target.value)}
                />
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginBottom: '12px' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#475569', marginBottom: '6px' }}>Price (₱)</label>
                  <input
                    type="number"
                    step="0.01"
                    required
                    className="admin-search-input"
                    style={{ paddingLeft: '16px' }}
                    placeholder="50.00"
                    value={prodPrice}
                    onChange={(e) => setProdPrice(e.target.value)}
                  />
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#475569', marginBottom: '6px' }}>Unit</label>
                  <input
                    type="text"
                    required
                    className="admin-search-input"
                    style={{ paddingLeft: '16px' }}
                    placeholder="kg, pc, pack"
                    value={prodUnit}
                    onChange={(e) => setProdUnit(e.target.value)}
                  />
                </div>
              </div>

              <div style={{ marginBottom: '12px' }}>
                <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#475569', marginBottom: '6px' }}>Category</label>
                <select
                  className="admin-select"
                  style={{ width: '100%' }}
                  value={prodCatId}
                  onChange={(e) => setProdCatId(e.target.value)}
                >
                  <option value="">Uncategorized</option>
                  {categories.map(c => (
                    <option key={c.id} value={c.id}>{c.name}</option>
                  ))}
                </select>
              </div>

              <div style={{ marginBottom: '24px' }}>
                <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#475569', marginBottom: '6px' }}>Description</label>
                <textarea
                  className="admin-search-input"
                  style={{ paddingLeft: '16px', height: '80px', resize: 'none', paddingTop: '10px' }}
                  placeholder="e.g. Local well-milled rice"
                  value={prodDesc}
                  onChange={(e) => setProdDesc(e.target.value)}
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
                <button
                  type="button"
                  className="admin-btn-action"
                  onClick={() => {
                    setShowAddProduct(false);
                    setEditingProduct(null);
                  }}
                >
                  Cancel
                </button>
                <button type="submit" className="admin-btn-primary" disabled={actionLoading}>
                  {actionLoading ? 'Saving...' : (editingProduct ? 'Save Changes' : 'Add Product')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Add Category Modal */}
      {showAddCategory && (
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
          <div className="admin-card" style={{ width: '380px', padding: '28px' }}>
            <h3 style={{ marginBottom: '16px' }}>Add Category</h3>
            <form onSubmit={handleAddCategory}>
              <div style={{ marginBottom: '24px' }}>
                <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#475569', marginBottom: '6px' }}>Category Name</label>
                <input
                  type="text"
                  required
                  className="admin-search-input"
                  style={{ paddingLeft: '16px' }}
                  placeholder="e.g. BEVERAGES"
                  value={newCategoryName}
                  onChange={(e) => setNewCategoryName(e.target.value)}
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
                <button
                  type="button"
                  className="admin-btn-action"
                  onClick={() => {
                    setShowAddCategory(false);
                    setNewCategoryName('');
                  }}
                >
                  Cancel
                </button>
                <button type="submit" className="admin-btn-primary" disabled={actionLoading}>
                  {actionLoading ? 'Adding...' : 'Add'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
