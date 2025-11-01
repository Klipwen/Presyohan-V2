import React from 'react';

export default function MobileCategorySelect({ categories, selectedCategory, setSelectedCategory, showMobileCategories, setShowMobileCategories }) {
  return (
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
  );
}