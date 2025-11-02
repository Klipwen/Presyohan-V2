import React, { useEffect, useState } from 'react';
import '../styles/StoresPage.css';
import StoreHeader from '../components/layout/StoreHeader';
import '../styles/ManageItemsPage.css';
import MobileCategorySelect from '../components/manage/MobileCategorySelect';
import ManageHeader from '../components/manage/ManageHeader';
import CategorySidebar from '../components/manage/CategorySidebar';
import ItemsList from '../components/manage/ItemsList';
import { supabase } from '../config/supabaseClient';
import { useNavigate } from 'react-router-dom';

export default function ManageItemsPage() {
  const navigate = useNavigate();
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('All Items');
  const [showMobileCategories, setShowMobileCategories] = useState(false);
  const [stores, setStores] = useState([]);
  
  const [categories] = useState([
    { id: 0, name: 'All Items' },
    { id: 1, name: 'SCHOOL SUPPLIES', store_id: 1 },
    { id: 2, name: 'GROCERIES', store_id: 1 },
    { id: 3, name: 'LIQUORS', store_id: 1 },
  ]);

  const [items] = useState([
    { 
      id: 1, 
      name: 'Notebook Competition', 
      category_id: 1,
      description: '',
      price: 18.00,
      unit: 'pc',
      created_at: '2024-01-01',
      updated_at: '2024-01-01'
    },
    { 
      id: 2, 
      name: 'Single line notebook', 
      category_id: 1,
      description: '',
      price: 15.00,
      unit: 'pc',
      created_at: '2024-01-01',
      updated_at: '2024-01-01'
    },
    { 
      id: 3, 
      name: 'Notebook Competition', 
      category_id: 1,
      description: '',
      price: 18.00,
      unit: 'pc',
      created_at: '2024-01-01',
      updated_at: '2024-01-01'
    },
    { 
      id: 4, 
      name: 'Rice Premium', 
      category_id: 2,
      description: '',
      price: 45.00,
      unit: 'kg',
      created_at: '2024-01-01',
      updated_at: '2024-01-01'
    },
    { 
      id: 5, 
      name: 'Cooking Oil', 
      category_id: 2,
      description: '',
      price: 32.00,
      unit: 'L',
      created_at: '2024-01-01',
      updated_at: '2024-01-01'
    },
  ]);

  const [totalItems] = useState(6);

  const filteredItems = items.filter(item => {
    const matchesCategory = selectedCategory === 'All Items' || 
      categories.find(c => c.id === item.category_id)?.name === selectedCategory;
    const matchesSearch = item.name.toLowerCase().includes(searchQuery.toLowerCase());
    return matchesCategory && matchesSearch;
  });

  const groupedItems = filteredItems.reduce((acc, item) => {
    const category = categories.find(c => c.id === item.category_id);
    const categoryName = category ? category.name : 'Other';
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
    };
    init();
  }, [navigate]);

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
      <ManageHeader />

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
              }}>
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
          <ItemsList groupedItems={groupedItems} />
        </div>
      </div>

      {/* Responsive Styles moved to ManageItemsPage.css */}
    </div>
  );
}