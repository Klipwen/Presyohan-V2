import React, { useEffect, useState } from 'react';
import '../styles/StoresPage.css';
import '../styles/StorePage.css';
import StoreHeader from '../components/layout/StoreHeader';
import StoreBanner from '../components/store/StoreHeader';
import StoreSearchBar from '../components/store/StoreSearchBar';
import CategorySidebar from '../components/store/CategorySidebar';
import ProductsGrid from '../components/store/ProductsGrid';
import { supabase } from '../config/supabaseClient';
import { useLocation, useNavigate } from 'react-router-dom';
import storeIcon from '../assets/icon_store.png';

export default function StorePage() {
  const location = useLocation();
  const navigate = useNavigate();
  const params = new URLSearchParams(location.search);
  const storeId = params.get('id');

  const [storeName, setStoreName] = useState('Store');
  const [storeBranch, setStoreBranch] = useState('');
  const [iconSrc] = useState(storeIcon); // Placeholder until DB supports per-store icon
  const [stores, setStores] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('PRICELIST');
  
  const [products] = useState([
    { id: 1, name: 'Bottle water', price: 18.00, unit: '500 ml', category_id: 1 },
    { id: 2, name: 'Bottle water', price: 18.50, unit: '510 ml', category_id: 1 },
    { id: 3, name: 'Bottle water', price: 19.00, unit: '1000 ml', category_id: 1 },
    { id: 4, name: 'Bottle water', price: 20.00, unit: '1000 ml', category_id: 1 },
    { id: 5, name: 'Bottle water', price: 18.00, unit: '500 ml', category_id: 1 },
    { id: 6, name: 'Bottle water', price: 18.50, unit: '600 ml', category_id: 1 },
    { id: 7, name: 'Bottle water', price: 18.00, unit: '500 ml', category_id: 1 },
    { id: 8, name: 'Bottle water', price: 18.50, unit: '510 ml', category_id: 1 },
  ]);

  const [categories] = useState([
    { id: 1, name: 'PRICELIST', store_id: 1 },
    { id: 2, name: 'DAIRY', store_id: 1 },
    { id: 3, name: 'MEAT', store_id: 1 },
    { id: 4, name: 'BAKERY', store_id: 1 },
    { id: 5, name: 'BEVERAGES', store_id: 1 },
    { id: 6, name: 'SNACKS', store_id: 1 },
  ]);

  const filteredProducts = products.filter(p => {
    const matchesCategory = selectedCategory === 'PRICELIST' || 
      categories.find(c => c.id === p.category_id)?.name === selectedCategory;
    const matchesSearch = p.name.toLowerCase().includes(searchQuery.toLowerCase());
    return matchesCategory && matchesSearch;
  });

  // Load real store name/branch via RPC
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
          console.warn('Failed to load stores for header:', error);
          return;
        }
        const rows = Array.isArray(data) ? data : [];
        // Map to StoreHeader expected shape
        const mappedStores = rows.map(r => ({ id: r.store_id, name: r.name, branch: r.branch || '' }));
        setStores(mappedStores);
        let row = null;
        if (storeId) {
          row = rows.find(r => r.store_id === storeId);
        }
        if (!row && rows.length > 0) {
          row = rows[0];
        }
        if (row) {
          setStoreName(row.name || 'Store');
          setStoreBranch(row.branch || '');
        }
      } catch (e) {
        console.warn('Unexpected error loading store header:', e);
      }
    };
    init();
  }, [storeId, navigate]);

  return (
    <div style={{ minHeight: '100vh', background: '#f5f5f5', fontFamily: 'system-ui, -apple-system, sans-serif' }}>
      <StoreHeader stores={stores} />
      {/* Store Header Banner */}
      <StoreBanner storeName={storeName} storeBranch={storeBranch} storeIcon={iconSrc} />

      {/* Search Bar */}
      <StoreSearchBar searchQuery={searchQuery} setSearchQuery={setSearchQuery} />

      {/* Main Content Layout */}
      <div style={{ display: 'flex', minHeight: 'calc(100vh - 180px)' }}>
        {/* Categories Sidebar */}
        <CategorySidebar categories={categories} selectedCategory={selectedCategory} setSelectedCategory={setSelectedCategory} />

        {/* Products Grid */}
        <ProductsGrid products={filteredProducts} />
      </div>

      {/* Floating Action Button */}
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
      }}>
        <svg width="28" height="28" fill="none" stroke="white" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M12 4v16m8-8H4"/>
        </svg>
      </button>
    </div>
  );
}