import React, { useState } from 'react';
import '../styles/StoresPage.css';
import '../styles/StorePage.css';
import StoreHeader from '../components/layout/StoreHeader';
import StoreBanner from '../components/store/StoreHeader';
import StoreSearchBar from '../components/store/StoreSearchBar';
import CategorySidebar from '../components/store/CategorySidebar';
import ProductsGrid from '../components/store/ProductsGrid';

export default function StorePage() {
  const [storeName] = useState('OSBOS');
  const [storeBranch] = useState('Curva branch');
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

  return (
    <div style={{ minHeight: '100vh', background: '#f5f5f5', fontFamily: 'system-ui, -apple-system, sans-serif' }}>
      <StoreHeader />
      {/* Store Header Banner */}
      <StoreBanner storeName={storeName} storeBranch={storeBranch} />

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