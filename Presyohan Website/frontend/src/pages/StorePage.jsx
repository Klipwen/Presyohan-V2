import React, { useEffect, useState } from 'react';
import '../styles/StoresPage.css';
import StoreHeader from '../components/layout/StoreHeader';
import StoreBanner from '../components/store/StoreHeader';
import StoreOptionsModal from '../components/store/StoreOptionsModal';
import InviteStaffModal from '../components/store/InviteStaffModal';
import ConfirmModal from '../components/store/ConfirmModal';
import StoreSearchBar from '../components/store/StoreSearchBar';
import CategorySidebar from '../components/store/CategorySidebar';
import ProductsGrid from '../components/store/ProductsGrid';
import AddItemModal from '../components/items/AddItemModal';
import { useNavigate } from 'react-router-dom';
import { supabase } from '../config/supabaseClient';

export default function StorePage() {
  const [storeName, setStoreName] = useState('');
  const [storeBranch, setStoreBranch] = useState('');
  const [storeId, setStoreId] = useState(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('PRICELIST');
  const [showAddItem, setShowAddItem] = useState(false);
  const [optionsOpen, setOptionsOpen] = useState(false);
  const [inviteOpen, setInviteOpen] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [confirmConfig, setConfirmConfig] = useState({ title: '', message: '', action: null, confirmLabel: 'Confirm' });
  const navigate = useNavigate();
  
  const [products, setProducts] = useState([]);

  const [categories, setCategories] = useState([]);
  const [stores, setStores] = useState([]);
  const [currentRole, setCurrentRole] = useState(null);

  useEffect(() => {
    const id = new URLSearchParams(window.location.search).get('id');
    setStoreId(id);
    const init = async () => {
      const { data: { session } } = await supabase.auth.getSession();
      if (!session) {
        navigate('/login', { replace: true });
        return;
      }
      // If no store id is provided, send the user back to Stores
      if (!id) {
        navigate('/stores', { replace: true });
        return;
      }
      try {
        const { data, error } = await supabase.rpc('get_user_stores');
        if (error) {
          console.warn('Failed to load stores:', error);
        } else {
          const rows = Array.isArray(data) ? data : [];
          setStores(rows.map(r => ({ id: r.store_id, name: r.name, branch: r.branch })));
          const match = rows.find(r => String(r.store_id) === String(id));
          if (match) {
            setStoreName(match.name);
            setStoreBranch(match.branch || '');
            setCurrentRole(match.role || null);
          }
        }
      } catch (e) {
        console.warn('Unexpected error loading store:', e);
      }

      try {
        if (id) {
          const { data: cats, error: catsErr } = await supabase.rpc('get_user_categories', { p_store_id: id });
          if (!catsErr) setCategories(Array.isArray(cats) ? cats.map(c => ({ id: c.category_id, name: c.name })) : []);
        }
      } catch (e) {
        console.warn('Error loading categories:', e);
      }
    };
    init();
  }, [navigate]);

  const getCurrentUserId = async () => {
    const { data: { session } } = await supabase.auth.getSession();
    const authId = session?.user?.id;
    if (!authId) return null;
    const { data: userRow } = await supabase.from('app_users').select('id, auth_uid').eq('auth_uid', authId).maybeSingle();
    return userRow?.id || authId;
  };

  const triggerConfirm = (type) => {
    if (!storeId) return;
    if (type === 'delete') {
      setConfirmConfig({
        title: 'Delete Store',
        message: 'Are you sure you want to delete this store? This action cannot be undone.',
        confirmLabel: 'Delete',
        action: async () => {
          const { error } = await supabase.from('stores').delete().eq('id', storeId);
          if (error) { alert(error.message || 'Failed to delete store'); return; }
          setConfirmOpen(false);
          navigate('/stores');
        }
      });
    } else {
      setConfirmConfig({
        title: 'Leave Store',
        message: 'Are you sure you want to leave this store?',
        confirmLabel: 'Leave',
        action: async () => {
          try {
            const { error } = await supabase.rpc('leave_store', { p_store_id: storeId });
            if (error) { alert(error.message || 'Failed to leave store'); return; }
            setConfirmOpen(false);
            navigate('/stores');
          } catch (e) {
            alert(e.message || 'Failed to leave store');
          }
        }
      });
    }
    setOptionsOpen(false);
    setConfirmOpen(true);
  };

  useEffect(() => {
    const loadProducts = async () => {
      if (!storeId) return;
      try {
        const args = { p_store_id: storeId };
        const cat = selectedCategory && selectedCategory !== 'PRICELIST' ? selectedCategory : null;
        if (cat) args.p_category_filter = cat;
        const query = searchQuery?.trim();
        if (query) args.p_search_query = query;
        const { data, error } = await supabase.rpc('get_store_products', args);
        if (error) {
          console.warn('Failed to load products:', error);
          return;
        }
        const rows = Array.isArray(data) ? data : [];
        setProducts(rows.map(r => ({
          id: r.product_id,
          name: r.name,
          description: r.description || '',
          price: Number(r.price) || 0,
          unit: r.units || '',
          category: r.category || ''
        })));
      } catch (e) {
        console.warn('Unexpected error loading products:', e);
      }
    };
    loadProducts();
  }, [storeId, selectedCategory, searchQuery]);

  // Keyboard shortcut: Ctrl+A (or Cmd+A on Mac) opens Add Item modal
  useEffect(() => {
    // Disable shortcut for sales staff (employee role)
    if (currentRole === 'employee') return;
    const onKeyDown = (e) => {
      // Guard in handler in case role changes at runtime
      if (currentRole === 'employee') return;
      const isCtrlA = (e.ctrlKey || e.metaKey) && ((e.key && e.key.toLowerCase() === 'a') || e.code === 'KeyA');
      if (!isCtrlA) return;
      const tag = (e.target?.tagName || '').toLowerCase();
      const isEditable = e.target?.isContentEditable || ['input', 'textarea', 'select'].includes(tag);
      if (isEditable) return; // don't override select-all inside editable fields
      e.preventDefault();
      setShowAddItem(true);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [currentRole]);

  return (
    <div style={{ minHeight: '100vh', background: '#f5f5f5', fontFamily: 'system-ui, -apple-system, sans-serif' }}>
      <StoreHeader stores={stores} includeAllStoresLink={true} />
      {/* Store Header Banner */}
      <StoreBanner storeName={storeName} storeBranch={storeBranch} onOptionsClick={() => setOptionsOpen(true)} />

      {/* Search Bar */}
      <StoreSearchBar searchQuery={searchQuery} setSearchQuery={setSearchQuery} />

      {/* Main Content Layout */}
      <div style={{ display: 'flex', minHeight: 'calc(100vh - 180px)' }}>
        {/* Categories Sidebar */}
        <CategorySidebar categories={categories} selectedCategory={selectedCategory} setSelectedCategory={setSelectedCategory} />

        {/* Products Grid */}
        <ProductsGrid 
          products={products} 
          categories={categories}
          onEditItem={async (updatedItem) => {
            if (!storeId) return;
            try {
              // Find category ID if category is provided
              let categoryId = null;
              if (updatedItem.category?.trim()) {
                const existing = categories.find(c => c.name === updatedItem.category.trim());
                if (existing) {
                  categoryId = existing.id;
                } else {
                  // Create new category if it doesn't exist
                  const { data: inserted, error } = await supabase.rpc('add_category', { 
                    p_store_id: storeId, 
                    p_name: updatedItem.category.trim() 
                  });
                  if (error) throw error;
                  categoryId = inserted?.[0]?.category_id;
                  if (categoryId) {
                    setCategories(prev => [...prev, { id: categoryId, name: updatedItem.category.trim() }]);
                  }
                }
              }

              // Update the product
              const { error } = await supabase
                .from('products')
                .update({
                  name: updatedItem.name,
                  description: updatedItem.description || null,
                  price: updatedItem.price,
                  units: updatedItem.unit,
                  category_id: categoryId
                })
                .eq('id', updatedItem.id)
                .eq('store_id', storeId);

              if (error) throw error;

              // If item moved categories, check and delete previous category if now empty
              const previousCategoryName = products.find(p => p.id === updatedItem.id)?.category || '';
              const newCategoryName = updatedItem.category?.trim() || '';
              if (previousCategoryName && previousCategoryName !== newCategoryName) {
                const { data: prevCatRows } = await supabase
                  .from('categories')
                  .select('id')
                  .eq('store_id', storeId)
                  .eq('name', previousCategoryName)
                  .limit(1);
                const prevCatId = Array.isArray(prevCatRows) && prevCatRows[0]?.id;
                if (prevCatId) {
                  const { data: remaining } = await supabase
                    .from('products')
                    .select('id')
                    .eq('store_id', storeId)
                    .eq('category_id', prevCatId)
                    .limit(1);
                  if (Array.isArray(remaining) && remaining.length === 0) {
                    await supabase.from('categories').delete().eq('id', prevCatId).eq('store_id', storeId);
                    setCategories(prev => prev.filter(c => c.name !== previousCategoryName));
                  }
                }
              }
              // Refresh to reflect updates
              setSelectedCategory('PRICELIST');
              window.location.reload();
            } catch (e) {
              alert(e.message || 'Failed to update item');
            }
          }}
          onDeleteItem={async (itemId) => {
            if (!storeId) return;
            try {
              const catName = products.find(p => p.id === itemId)?.category;
              const { error } = await supabase
                .from('products')
                .delete()
                .eq('id', itemId)
                .eq('store_id', storeId);

              if (error) throw error;
              // Auto-delete category if this was the last item in it
              if (catName) {
                const { data: cats } = await supabase
                  .from('categories')
                  .select('id')
                  .eq('store_id', storeId)
                  .eq('name', catName)
                  .limit(1);
                const catId = Array.isArray(cats) && cats[0]?.id;
                if (catId) {
                  const { data: prods } = await supabase
                    .from('products')
                    .select('id')
                    .eq('store_id', storeId)
                    .eq('category_id', catId)
                    .limit(1);
                  if (Array.isArray(prods) && prods.length === 0) {
                    await supabase.from('categories').delete().eq('id', catId).eq('store_id', storeId);
                    setCategories(prev => prev.filter(c => c.name !== catName));
                  }
                }
              }
              // Refresh to reflect deletion
              setSelectedCategory('PRICELIST');
              window.location.reload();
            } catch (e) {
              alert(e.message || 'Failed to delete item');
            }
          }}
          onAddCategory={async (newName) => {
            if (!storeId) return;
            try {
              const upper = newName.trim().toUpperCase();
              const { data, error } = await supabase
                .from('categories')
                .insert({ store_id: storeId, name: upper })
                .select();
              if (error) {
                alert(error.message || 'Failed to add category');
                return;
              }
              const inserted = Array.isArray(data) ? data[0] : null;
              if (inserted) setCategories(prev => [...prev, inserted]);
            } catch (e) {
              alert(e.message || 'Unexpected error adding category');
            }
          }}
        />
      </div>

      {/* Floating Action Button (hidden for sales staff) */}
      {currentRole !== 'employee' && (
      <button style={{
        position: 'fixed',
        bottom: '30px',
        right: '30px',
        width: '60px',
        height: '60px',
        background: '#ff8c00',
        border: 'none',
        borderRadius: '50%',
        boxShadow: '0 4px 12px rgba(255, 140, 0, 0.4)',
        cursor: 'pointer',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 100
      }} title="Shortcut: Ctrl+A" onClick={() => currentRole !== 'employee' && setShowAddItem(true)} aria-label="Add Item">
        <svg width="28" height="28" fill="none" stroke="white" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M12 4v16m8-8H4"/>
        </svg>
      </button>
      )}

      {showAddItem && currentRole !== 'employee' && (
        <AddItemModal
          open={showAddItem}
          onClose={() => setShowAddItem(false)}
          storeName={storeName}
          storeBranch={storeBranch}
          categories={categories}
          onManageItems={() => navigate(`/manage-items?id=${encodeURIComponent(storeId || '')}`)}
          onAddCategory={async (newName) => {
            if (!storeId) return;
            try {
              const upper = newName.trim().toUpperCase();
              const { data, error } = await supabase
                .from('categories')
                .insert({ store_id: storeId, name: upper })
                .select();
              if (error) {
                alert(error.message || 'Failed to add category');
                return;
              }
              const inserted = Array.isArray(data) ? data[0] : null;
              if (inserted) setCategories(prev => [...prev, inserted]);
              setSelectedCategory(upper);
            } catch (e) {
              alert(e.message || 'Unexpected error adding category');
            }
          }}
          onCreateItem={async (payload) => {
            if (!storeId) return;
            try {
              const catName = payload.category.trim();
              // Map to category_id (create via RPC if missing)
              const existing = categories.find(c => c.name === catName);
              let categoryId = existing?.id;
              if (!categoryId && catName) {
                const { data: inserted, error } = await supabase.rpc('add_category', { p_store_id: storeId, p_name: catName });
                if (error) throw error;
                categoryId = inserted?.[0]?.category_id;
                if (categoryId) setCategories(prev => [...prev, { id: categoryId, name: catName }]);
              }
              const { error: addErr } = await supabase.rpc('add_product', {
                p_store_id: storeId,
                p_category_id: categoryId ?? null,
                p_name: payload.name,
                p_description: payload.description || null,
                p_price: payload.price,
                p_unit: payload.unit
              });
              if (addErr) throw addErr;
              // Refresh: ensure latest items are shown on the page
              setSelectedCategory('PRICELIST');
              window.location.reload();
            } catch (e) {
              alert(e.message || 'Failed to add item');
            }
          }}
        />
      )}

      {/* Options modal */}
      {storeId && (
        <StoreOptionsModal
          open={optionsOpen}
          onClose={() => setOptionsOpen(false)}
          storeId={storeId}
          storeName={storeName}
          role={currentRole}
          onSettings={() => navigate(`/manage-store?id=${encodeURIComponent(storeId)}`)}
          onInvite={() => { setOptionsOpen(false); setInviteOpen(true); }}
          onDelete={() => triggerConfirm('delete')}
          onLeave={() => triggerConfirm('leave')}
        />
      )}

      {/* Invite Staff modal */}
      {storeId && (
        <InviteStaffModal
          open={inviteOpen}
          onClose={() => setInviteOpen(false)}
          storeId={storeId}
          storeName={storeName}
          isOwner={currentRole === 'owner'}
        />
      )}

      {/* Confirm modal */}
      <ConfirmModal
        open={confirmOpen}
        title={confirmConfig.title}
        message={confirmConfig.message}
        confirmLabel={confirmConfig.confirmLabel}
        onConfirm={confirmConfig.action}
        onCancel={() => setConfirmOpen(false)}
      />
    </div>
  );
}