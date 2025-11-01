import React, { useState } from 'react';

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
      {/* Store Header Banner */}
      <div style={{
        background: 'white',
        padding: '20px 30px',
        borderBottom: '1px solid #e0e0e0'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '15px' }}>
          <button style={{
            width: '40px',
            height: '40px',
            background: 'transparent',
            border: 'none',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#ff8c00'
          }}>
            <svg width="24" height="24" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 19l-7-7 7-7"/>
            </svg>
          </button>
          <div style={{
            width: '45px',
            height: '45px',
            background: '#ff8c00',
            borderRadius: '8px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center'
          }}>
            <svg width="28" height="28" fill="white" viewBox="0 0 24 24">
              <path d="M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 14H4v-6h16v6zm0-10H4V6h16v2z"/>
            </svg>
          </div>
          <div style={{ flex: 1 }}>
            <h1 style={{ fontSize: '1.3rem', fontWeight: '700', margin: 0, color: '#ff8c00' }}>{storeName}</h1>
            <p style={{ fontSize: '0.85rem', color: '#999', margin: 0 }}>| {storeBranch}</p>
          </div>
          <button style={{
            width: '40px',
            height: '40px',
            background: 'transparent',
            border: 'none',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#ff8c00'
          }}>
            <svg width="24" height="24" fill="currentColor" viewBox="0 0 24 24">
              <path d="M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"/>
            </svg>
          </button>
        </div>
      </div>

      {/* Search Bar */}
      <div style={{ padding: '20px 30px', background: 'white', borderBottom: '1px solid #e0e0e0' }}>
        <div style={{
          display: 'flex',
          alignItems: 'center',
          background: '#f5f5f5',
          borderRadius: '25px',
          padding: '10px 20px',
          gap: '10px'
        }}>
          <svg width="20" height="20" fill="none" stroke="#999" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/>
          </svg>
          <input 
            type="text" 
            placeholder="Search item" 
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            style={{
              flex: 1,
              border: 'none',
              background: 'transparent',
              outline: 'none',
              fontSize: '0.95rem',
              color: '#333'
            }}
          />
          <button style={{
            width: '40px',
            height: '40px',
            background: '#00bcd4',
            border: 'none',
            borderRadius: '50%',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center'
          }}>
            <svg width="18" height="18" fill="none" stroke="white" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/>
            </svg>
          </button>
        </div>
      </div>

      {/* Main Content Layout */}
      <div style={{ display: 'flex', minHeight: 'calc(100vh - 180px)' }}>
        {/* Categories Sidebar */}
        <div style={{ 
          width: '300px', 
          background: 'white', 
          borderRight: '1px solid #e0e0e0',
          padding: '30px 25px',
          position: 'sticky',
          top: 0,
          height: 'fit-content'
        }}>
          <h3 style={{ 
            fontSize: '0.75rem', 
            fontWeight: '700', 
            color: '#999', 
            letterSpacing: '1px',
            marginBottom: '20px',
            paddingLeft: '5px'
          }}>
            CATEGORIES
          </h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {categories.map((cat) => {
              const isSelected = selectedCategory === cat.name;
              return (
                <button
                  key={cat.id}
                  onClick={() => setSelectedCategory(cat.name)}
                  onMouseEnter={(e) => {
                    if (!isSelected) {
                      e.currentTarget.style.background = 'linear-gradient(135deg, #e0f7fa 0%, #b2ebf2 100%)';
                      e.currentTarget.style.transform = 'translateX(5px)';
                    }
                  }}
                  onMouseLeave={(e) => {
                    if (!isSelected) {
                      e.currentTarget.style.background = 'white';
                      e.currentTarget.style.transform = 'translateX(0)';
                    }
                  }}
                  style={{
                    padding: '16px 20px',
                    border: isSelected ? 'none' : '1px solid #f0f0f0',
                    borderRadius: '30px',
                    background: isSelected ? 'linear-gradient(135deg, #00bcd4 0%, #00acc1 100%)' : 'white',
                    color: isSelected ? 'white' : '#555',
                    fontSize: '0.9rem',
                    fontWeight: isSelected ? '600' : '500',
                    cursor: 'pointer',
                    textAlign: 'left',
                    transition: 'all 0.3s ease',
                    boxShadow: isSelected ? '0 4px 12px rgba(0, 188, 212, 0.3)' : '0 1px 3px rgba(0,0,0,0.05)',
                    transform: 'translateX(0)'
                  }}
                >
                  {cat.name}
                </button>
              );
            })}
          </div>
        </div>

        {/* Products Grid */}
        <div style={{ flex: 1, padding: '30px', background: '#f5f5f5' }}>
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))',
            gap: '15px'
          }}>
            {filteredProducts.map((product) => (
              <div key={product.id} style={{
                background: 'white',
                borderRadius: '12px',
                padding: '15px',
                boxShadow: '0 2px 8px rgba(0, 0, 0, 0.08)',
                cursor: 'pointer',
                transition: 'all 0.2s ease',
                display: 'flex',
                flexDirection: 'column',
                gap: '8px'
              }}>
                <h3 style={{ 
                  fontSize: '0.95rem', 
                  fontWeight: '600', 
                  color: '#00bcd4', 
                  margin: 0,
                  lineHeight: '1.3'
                }}>
                  {product.name}
                </h3>
                <div style={{ 
                  display: 'flex', 
                  justifyContent: 'space-between', 
                  alignItems: 'center',
                  marginTop: 'auto'
                }}>
                  <span style={{ 
                    fontSize: '1.1rem', 
                    fontWeight: '700', 
                    color: '#ff8c00' 
                  }}>
                    ₱{product.price.toFixed(2)}
                  </span>
                  <span style={{ 
                    fontSize: '0.8rem', 
                    color: '#999' 
                  }}>
                    {product.unit}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>
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