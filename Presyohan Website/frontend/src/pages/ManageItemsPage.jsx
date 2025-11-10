import React, { useEffect, useState } from 'react';
import '../styles/StoresPage.css';
import StoreHeader from '../components/layout/StoreHeader';
import '../styles/ManageItemsPage.css';
import MobileCategorySelect from '../components/manage/MobileCategorySelect';
import ManageHeader from '../components/manage/ManageHeader';
import CategorySidebar from '../components/manage/CategorySidebar';
import ItemsList from '../components/manage/ItemsList';
import AddItemModal from '../components/items/AddItemModal';
import EditItemModal from '../components/items/EditItemModal';
import ConfirmationModal from '../components/common/ConfirmationModal';
import { supabase } from '../config/supabaseClient';
import { useNavigate } from 'react-router-dom';

export default function ManageItemsPage() {
  const navigate = useNavigate();
  const storeId = new URLSearchParams(window.location.search).get('id');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('PRICELIST');
  const [showMobileCategories, setShowMobileCategories] = useState(false);
  const [stores, setStores] = useState([]);
  const [categories, setCategories] = useState([]);
  const [items, setItems] = useState([]);
  const [totalItems, setTotalItems] = useState(0);
  const [showAddItem, setShowAddItem] = useState(false);
  const [editItem, setEditItem] = useState(null);
  const [confirm, setConfirm] = useState({ open: false, title: '', message: '', onConfirm: null, confirmText: 'Confirm', cancelText: 'Cancel' });
  const [hasChanges, setHasChanges] = useState(false);

  const filteredItems = items.filter(item => {
    const matchesCategory = selectedCategory === 'PRICELIST' || item.category === selectedCategory;
    const matchesSearch = item.name.toLowerCase().includes(searchQuery.toLowerCase());
    return matchesCategory && matchesSearch;
  });

  const groupedItems = filteredItems.reduce((acc, item) => {
    const categoryName = item.category || 'Other';
    if (!acc[categoryName]) {
      acc[categoryName] = [];
    }
    acc[categoryName].push(item);
    return acc;
  }, {});

  // Load user stores for sidebar dropdown
  useEffect(() => {
    const init = async () => {
      const { data: { session } } = await supabase.auth.getSession();
      if (!session) {
        navigate('/login', { replace: true });
        return;
      }
      try {
        const { data, error } = await supabase.rpc('get_user_stores');
        if (error) {
          console.warn('Failed to load stores:', error);
          return;
        }
        const rows = Array.isArray(data) ? data : [];
        const mapped = rows.map(r => ({ id: r.store_id, name: r.name, branch: r.branch || '' }));
        setStores(mapped);
      } catch (e) {
        console.warn('Unexpected error loading stores:', e);
      }
      try {
        if (storeId) {
          const { data: cats, error: catsErr } = await supabase.rpc('get_user_categories', { p_store_id: storeId });
          if (!catsErr) setCategories(Array.isArray(cats) ? cats.map(c => ({ id: c.category_id, name: c.name })) : []);
        }
      } catch (e) {
        console.warn('Error loading categories:', e);
      }
    };
    init();
  }, [navigate]);

  useEffect(() => {
    const loadItems = async () => {
      if (!storeId) return;
      try {
        const args = { p_store_id: storeId };
        const cat = selectedCategory && selectedCategory !== 'PRICELIST' ? selectedCategory : null;
        if (cat) args.p_category_filter = cat;
        const q = searchQuery?.trim();
        if (q) args.p_search_query = q;
        const { data, error } = await supabase.rpc('get_store_products', args);
        if (error) {
          console.warn('Failed to load products:', error);
          return;
        }
        const rows = Array.isArray(data) ? data : [];
        const mapped = rows.map(r => ({ id: r.product_id, name: r.name, description: r.description || '', price: Number(r.price) || 0, unit: r.units || '', category: r.category || '' }));
        setItems(mapped);
        setTotalItems(mapped.length);
      } catch (e) {
        console.warn('Unexpected error loading products:', e);
      }
    };
    loadItems();
  }, [storeId, selectedCategory, searchQuery]);

  return (
    <div style={{ 
      minHeight: '100vh', 
      background: '#f5f5f5', 
      fontFamily: 'system-ui, -apple-system, sans-serif',
      paddingBottom: '80px'
    }}>
      <StoreHeader stores={stores} />
      {/* ... existing code ... */}
      {/* Header */}
      <ManageHeader 
        onBack={() => {
          setConfirm({
            open: true,
            title: 'Confirm Navigation',
            message: hasChanges ? 'You made changes. Go back?' : 'Go back?',
            confirmText: 'Back',
            cancelText: 'Stay',
            onConfirm: () => {
              setConfirm({ open: false, title: '', message: '', onConfirm: null, confirmText: 'Confirm', cancelText: 'Cancel' });
              navigate(-1);
            }
          });
        }}
        onDone={() => {
          setConfirm({
            open: true,
            title: 'Done Managing Items',
            message: hasChanges ? 'All changes are saved. Return to the store page?' : 'No changes made. Return to the store page?',
            confirmText: 'Done',
            cancelText: 'Cancel',
            onConfirm: () => {
              setConfirm({ open: false, title: '', message: '', onConfirm: null, confirmText: 'Confirm', cancelText: 'Cancel' });
              navigate(`/store?id=${encodeURIComponent(storeId || '')}`);
            }
          });
        }}
      />

      {/* Main Content */}
      <div style={{ 
        display: 'flex', 
        flexDirection: 'row',
        gap: '0',
        minHeight: 'calc(100vh - 70px)'
      }}>
        {/* Categories Sidebar - Desktop */}
        <CategorySidebar 
          categories={categories}
          selectedCategory={selectedCategory}
          setSelectedCategory={setSelectedCategory}
          totalItems={totalItems}
        />

        {/* Items Content */}
        <div style={{ flex: 1, background: '#f5f5f5' }}>
          {/* Search Bar */}
          <div style={{ padding: '20px', background: 'white', borderBottom: '1px solid #e0e0e0' }}>
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: '15px',
              flexWrap: 'wrap'
            }}>
              <div style={{
                flex: 1,
                minWidth: '200px',
                display: 'flex',
                alignItems: 'center',
                background: '#f5f5f5',
                borderRadius: '25px',
                padding: '10px 20px',
                gap: '10px'
              }}>
                <svg width="18" height="18" fill="none" stroke="#999" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/>
                </svg>
                <input 
                  type="text" 
                  placeholder="Search items..." 
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  style={{
                    flex: 1,
                    border: 'none',
                    background: 'transparent',
                    outline: 'none',
                    fontSize: '0.9rem',
                    color: '#333'
                  }}
                />
              </div>
              <button style={{
                background: '#ff8c00',
                color: 'white',
                border: 'none',
                padding: '10px 20px',
                borderRadius: '25px',
                fontSize: '0.85rem',
                fontWeight: '600',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                whiteSpace: 'nowrap'
              }} onClick={() => setShowAddItem(true)}>
                <svg width="16" height="16" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M12 4v16m8-8H4"/>
                </svg>
                Add Item
              </button>
            </div>
          </div>

          {/* Mobile Category Selector */}
          <MobileCategorySelect 
            categories={categories}
            selectedCategory={selectedCategory}
            setSelectedCategory={setSelectedCategory}
            showMobileCategories={showMobileCategories}
            setShowMobileCategories={setShowMobileCategories}
          />

          {/* Items List by Category */}
          <ItemsList 
            groupedItems={groupedItems}
            onEditItem={(item) => setEditItem(item)}
            onDeleteItem={(item) => {
              setConfirm({
                open: true,
                title: 'Delete Item',
                message: `Are you sure you want to delete "${item.name}"?`,
                onConfirm: async () => {
                  try {
                    await supabase.from('products').delete().eq('id', item.id).eq('store_id', storeId);
                    // Auto-delete category if no products remain
                    const catName = item.category;
                    if (catName) {
                      const { data: cats } = await supabase.from('categories').select('id').eq('store_id', storeId).eq('name', catName).limit(1);
                      const catId = Array.isArray(cats) && cats[0]?.id;
                      if (catId) {
                        const { data: prods } = await supabase.from('products').select('id').eq('store_id', storeId).eq('category_id', catId).limit(1);
                        if (Array.isArray(prods) && prods.length === 0) {
                          await supabase.from('categories').delete().eq('id', catId).eq('store_id', storeId);
                          setCategories(prev => prev.filter(c => c.name !== catName));
                        }
                      }
                    }
                    setConfirm({ open: false, title: '', message: '', onConfirm: null });
                    // Refresh
                    setHasChanges(true);
                    setSelectedCategory('PRICELIST');
                    window.location.reload();
                  } catch (e) {
                    alert(e.message || 'Failed to delete item');
                  }
                }
              });
            }}
            onAddItemToCategory={(categoryName) => {
              setSelectedCategory(categoryName);
              setShowAddItem(true);
            }}
          />
        </div>
      </div>

      {/* Responsive Styles moved to ManageItemsPage.css */}
      {showAddItem && (
        <AddItemModal
          open={showAddItem}
          onClose={() => setShowAddItem(false)}
          storeName={stores.find(s => s.id === storeId)?.name || ''}
          storeBranch={stores.find(s => s.id === storeId)?.branch || ''}
          categories={categories}
          defaultCategory={selectedCategory !== 'PRICELIST' ? selectedCategory : ''}
          onAddCategory={async (newName) => {
            try {
              const upper = newName.trim().toUpperCase();
              const { data: inserted, error } = await supabase.rpc('add_category', { p_store_id: storeId, p_name: upper });
              if (error) throw error;
              const newId = inserted?.[0]?.category_id;
              if (newId) setCategories(prev => [...prev, { id: newId, name: upper }]);
              setSelectedCategory(upper);
            } catch (e) {
              alert(e.message || 'Failed to add category');
            }
          }}
          onCreateItem={async (payload) => {
            try {
              const catName = payload.category.trim();
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
              setShowAddItem(false);
              setHasChanges(true);
              setSelectedCategory('PRICELIST');
              window.location.reload();
            } catch (e) {
              alert(e.message || 'Failed to add item');
            }
          }}
        />
      )}

      {editItem && (
        <EditItemModal
          open={!!editItem}
          onClose={() => setEditItem(null)}
          item={editItem}
          categories={categories}
          onAddCategory={async (newName) => {
            try {
              const upper = newName.trim().toUpperCase();
              const { data: inserted, error } = await supabase.rpc('add_category', { p_store_id: storeId, p_name: upper });
              if (error) throw error;
              const newId = inserted?.[0]?.category_id;
              if (newId) setCategories(prev => [...prev, { id: newId, name: upper }]);
            } catch (e) {
              alert(e.message || 'Failed to add category');
            }
          }}
          onSave={async (payload) => {
            try {
              // Map category name to id (optional)
              const catName = payload.category.trim();
              const existing = categories.find(c => c.name === catName);
              const categoryId = existing?.id ?? null;
              const previousCategoryName = editItem?.category || '';
              await supabase.from('products').update({
                name: payload.name,
                description: payload.description || null,
                price: payload.price,
                unit: payload.unit,
                category_id: categoryId
              }).eq('id', payload.id).eq('store_id', storeId);

              // If item moved categories, check and delete previous category if now empty
              if (previousCategoryName && previousCategoryName !== catName) {
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
              setEditItem(null);
              setHasChanges(true);
              setSelectedCategory('PRICELIST');
              window.location.reload();
            } catch (e) {
              alert(e.message || 'Failed to save item');
            }
          }}
        />
      )}

      <ConfirmationModal
        open={confirm.open}
        title={confirm.title}
        message={confirm.message}
        onConfirm={confirm.onConfirm}
        onCancel={() => setConfirm({ open: false, title: '', message: '', onConfirm: null, confirmText: 'Confirm', cancelText: 'Cancel' })}
        confirmText={confirm.confirmText}
        cancelText={confirm.cancelText}
      />
    </div>
  );
}