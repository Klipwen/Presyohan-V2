import React, { useState } from 'react';

export default function ManageItemsPage() {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('All Items');
  const [showMobileCategories, setShowMobileCategories] = useState(false);
  
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

  return (
    <div style={{ 
      minHeight: '100vh', 
      background: '#f5f5f5', 
      fontFamily: 'system-ui, -apple-system, sans-serif',
      paddingBottom: '80px'
    }}>
      {/* Header */}
      <div style={{
        background: 'white',
        padding: '15px 20px',
        borderBottom: '1px solid #e0e0e0',
        position: 'sticky',
        top: 0,
        zIndex: 100,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '15px' }}>
          <button style={{
            background: 'transparent',
            border: 'none',
            cursor: 'pointer',
            padding: '5px',
            color: '#ff8c00'
          }}>
            <svg width="24" height="24" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 19l-7-7 7-7"/>
            </svg>
          </button>
          <h1 style={{ 
            fontSize: '1.2rem', 
            fontWeight: '600', 
            color: '#ff8c00',
            margin: 0 
          }}>
            Manage Items
          </h1>
        </div>
        <button style={{
          background: '#ff8c00',
          color: 'white',
          border: 'none',
          padding: '8px 24px',
          borderRadius: '20px',
          fontSize: '0.9rem',
          fontWeight: '600',
          cursor: 'pointer'
        }}>
          DONE
        </button>
      </div>

      {/* Main Content */}
      <div style={{ 
        display: 'flex', 
        flexDirection: 'row',
        gap: '0',
        minHeight: 'calc(100vh - 70px)'
      }}>
        {/* Categories Sidebar - Desktop */}
        <div className="sidebar" style={{ 
          width: '280px', 
          background: 'white', 
          borderRight: '1px solid #e0e0e0',
          padding: '20px',
          display: 'flex',
          flexDirection: 'column',
          gap: '20px'
        }}>
          <div>
            <h3 style={{ 
              fontSize: '0.75rem', 
              fontWeight: '700', 
              color: '#999', 
              letterSpacing: '1px',
              marginBottom: '15px'
            }}>
              Categories
            </h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              {categories.map((cat) => {
                const isSelected = selectedCategory === cat.name;
                const isAllItems = cat.name === 'All Items';
                return (
                  <div key={cat.id} style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                    <button
                      onClick={() => setSelectedCategory(cat.name)}
                      onMouseEnter={(e) => {
                        if (!isSelected) {
                          e.currentTarget.style.background = 'linear-gradient(135deg, #e0f7fa 0%, #b2ebf2 100%)';
                        }
                      }}
                      onMouseLeave={(e) => {
                        if (!isSelected) {
                          e.currentTarget.style.background = 'white';
                        }
                      }}
                      style={{
                        flex: 1,
                        padding: '14px 18px',
                        border: 'none',
                        borderRadius: '25px',
                        background: isSelected ? 
                          (isAllItems ? '#ff8c00' : '#00bcd4') : 
                          'white',
                        color: isSelected ? 'white' : '#555',
                        fontSize: '0.85rem',
                        fontWeight: isSelected ? '600' : '500',
                        cursor: 'pointer',
                        textAlign: 'left',
                        transition: 'all 0.3s ease',
                        boxShadow: isSelected ? 
                          '0 4px 12px rgba(0, 188, 212, 0.3)' : 
                          '0 1px 3px rgba(0,0,0,0.05)'
                      }}
                    >
                      {cat.name}
                    </button>
                    {!isAllItems && (
                      <button
                        style={{
                          width: '36px',
                          height: '36px',
                          background: 'white',
                          border: '1px solid #e0e0e0',
                          borderRadius: '50%',
                          cursor: 'pointer',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          color: '#666',
                          transition: 'all 0.2s ease'
                        }}
                        onMouseEnter={(e) => {
                          e.currentTarget.style.background = '#f5f5f5';
                        }}
                        onMouseLeave={(e) => {
                          e.currentTarget.style.background = 'white';
                        }}
                      >
                        <svg width="16" height="16" fill="currentColor" viewBox="0 0 24 24">
                          <path d="M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"/>
                        </svg>
                      </button>
                    )}
                  </div>
                );
              })}
            </div>
          </div>

          {/* Total Items */}
          <div style={{
            marginTop: 'auto',
            padding: '15px',
            background: '#f8f9fa',
            borderRadius: '12px',
            display: 'flex',
            alignItems: 'center',
            gap: '10px'
          }}>
            <svg width="20" height="20" fill="#666" viewBox="0 0 24 24">
              <path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-5 14H7v-2h7v2zm3-4H7v-2h10v2zm0-4H7V7h10v2z"/>
            </svg>
            <div>
              <div style={{ fontSize: '0.7rem', color: '#999', fontWeight: '500' }}>Total Items</div>
              <div style={{ fontSize: '1.3rem', fontWeight: '700', color: '#333' }}>{totalItems}</div>
            </div>
          </div>
        </div>

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
          <div className="mobile-category-select" style={{ 
            padding: '15px 20px', 
            background: 'white',
            borderBottom: '1px solid #e0e0e0'
          }}>
            <button 
              onClick={() => setShowMobileCategories(!showMobileCategories)}
              style={{
                width: '100%',
                padding: '12px 18px',
                border: '1px solid #e0e0e0',
                borderRadius: '25px',
                background: 'white',
                color: '#333',
                fontSize: '0.9rem',
                fontWeight: '600',
                cursor: 'pointer',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center'
              }}
            >
              <span>{selectedCategory}</span>
              <svg width="20" height="20" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 9l-7 7-7-7"/>
              </svg>
            </button>
            
            {showMobileCategories && (
              <div style={{
                marginTop: '10px',
                background: 'white',
                border: '1px solid #e0e0e0',
                borderRadius: '12px',
                overflow: 'hidden'
              }}>
                {categories.map((cat) => (
                  <button
                    key={cat.id}
                    onClick={() => {
                      setSelectedCategory(cat.name);
                      setShowMobileCategories(false);
                    }}
                    style={{
                      width: '100%',
                      padding: '14px 18px',
                      border: 'none',
                      borderBottom: '1px solid #f0f0f0',
                      background: selectedCategory === cat.name ? '#f0f8ff' : 'white',
                      color: selectedCategory === cat.name ? '#00bcd4' : '#555',
                      fontSize: '0.9rem',
                      fontWeight: selectedCategory === cat.name ? '600' : '500',
                      cursor: 'pointer',
                      textAlign: 'left'
                    }}
                  >
                    {cat.name}
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* Items List by Category */}
          <div style={{ padding: '20px' }}>
            {Object.entries(groupedItems).map(([categoryName, categoryItems]) => {
              const itemCount = categoryItems.length;
              return (
                <div key={categoryName} style={{ marginBottom: '30px' }}>
                  <div style={{ 
                    display: 'flex', 
                    justifyContent: 'space-between', 
                    alignItems: 'center',
                    marginBottom: '15px',
                    padding: '0 10px'
                  }}>
                    <h2 style={{
                      fontSize: '0.9rem',
                      fontWeight: '700',
                      color: '#ff8c00',
                      margin: 0,
                      textTransform: 'uppercase'
                    }}>
                      {categoryName}
                    </h2>
                    <span style={{
                      fontSize: '0.85rem',
                      color: '#999',
                      fontWeight: '500'
                    }}>
                      {itemCount} {itemCount === 1 ? 'item' : 'items'}
                    </span>
                  </div>

                  {/* Items */}
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                    {categoryItems.map((item, index) => (
                      <div key={item.id} style={{
                        background: 'white',
                        borderRadius: '12px',
                        padding: '15px',
                        boxShadow: '0 1px 3px rgba(0, 0, 0, 0.1)',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '15px',
                        flexWrap: 'wrap'
                      }}>
                        <div style={{
                          fontSize: '0.85rem',
                          fontWeight: '600',
                          color: '#999',
                          minWidth: '30px'
                        }}>
                          #{index + 1}
                        </div>
                        
                        <div style={{ flex: 1, minWidth: '200px' }}>
                          <div style={{ 
                            fontSize: '0.95rem', 
                            fontWeight: '600', 
                            color: '#333',
                            marginBottom: '5px'
                          }}>
                            {item.name}
                          </div>
                          <div style={{ 
                            display: 'flex',
                            gap: '15px',
                            fontSize: '0.8rem',
                            color: '#999',
                            flexWrap: 'wrap'
                          }}>
                            <span>Price: ₱{item.price.toFixed(2)}</span>
                            <span>Unit: {item.unit}</span>
                          </div>
                        </div>

                        <div style={{ display: 'flex', gap: '10px' }}>
                          <button style={{
                            width: '36px',
                            height: '36px',
                            background: 'transparent',
                            border: '1px solid #e0e0e0',
                            borderRadius: '50%',
                            cursor: 'pointer',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            color: '#666'
                          }}>
                            <svg width="16" height="16" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z"/>
                            </svg>
                          </button>
                          <button style={{
                            width: '36px',
                            height: '36px',
                            background: '#ff4444',
                            border: 'none',
                            borderRadius: '50%',
                            cursor: 'pointer',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            color: 'white'
                          }}>
                            <svg width="16" height="16" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/>
                            </svg>
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>

                  {/* Add Item Button for Category */}
                  <button style={{
                    width: '100%',
                    marginTop: '15px',
                    background: '#ff8c00',
                    color: 'white',
                    border: 'none',
                    padding: '14px',
                    borderRadius: '25px',
                    fontSize: '0.9rem',
                    fontWeight: '600',
                    cursor: 'pointer'
                  }}>
                    Add Item to {categoryName}
                  </button>
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {/* Responsive Styles */}
      <style>{`
        @media (max-width: 768px) {
          .sidebar {
            display: none !important;
          }
          .mobile-category-select {
            display: block !important;
          }
        }
        @media (min-width: 769px) {
          .mobile-category-select {
            display: none !important;
          }
        }
      `}</style>
    </div>
  );
}