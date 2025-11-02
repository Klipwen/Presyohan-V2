import React from 'react';

export default function CategorySidebar({ categories, selectedCategory, setSelectedCategory, totalItems }) {
  return (
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
        {/* Pricelist button */}
        <div style={{ marginBottom: '10px' }}>
          <button
            onClick={() => setSelectedCategory('PRICELIST')}
            style={{
              width: '100%',
              padding: '14px 18px',
              border: selectedCategory === 'PRICELIST' ? 'none' : '1px solid #f0f0f0',
              borderRadius: '25px',
              background: selectedCategory === 'PRICELIST' ? '#ff8c00' : 'white',
              color: selectedCategory === 'PRICELIST' ? 'white' : '#555',
              fontSize: '0.85rem',
              fontWeight: selectedCategory === 'PRICELIST' ? '700' : '500',
              cursor: 'pointer',
              textAlign: 'left',
              transition: 'all 0.3s ease',
              boxShadow: selectedCategory === 'PRICELIST' ? '0 4px 12px rgba(255, 140, 0, 0.3)' : '0 1px 3px rgba(0,0,0,0.05)'
            }}
          >
            Pricelist
          </button>
        </div>
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

      {/* Total Items - stationary bottom-left */}
      <div style={{
        position: 'fixed',
        left: '20px',
        bottom: '20px',
        padding: '15px',
        background: '#f8f9fa',
        borderRadius: '12px',
        display: 'flex',
        alignItems: 'center',
        gap: '10px',
        boxShadow: '0 2px 8px rgba(0,0,0,0.1)'
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
  );
}