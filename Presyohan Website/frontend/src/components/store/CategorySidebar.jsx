import React from 'react';

export default function CategorySidebar({ categories, selectedCategory, setSelectedCategory }) {
  return (
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
      {/* Pricelist button */}
      <div style={{ marginBottom: '12px' }}>
        <button
          onClick={() => setSelectedCategory('PRICELIST')}
          style={{
            width: '100%',
            padding: '16px 20px',
            border: selectedCategory === 'PRICELIST' ? 'none' : '1px solid #f0f0f0',
            borderRadius: '30px',
            background: selectedCategory === 'PRICELIST' ? 'linear-gradient(135deg, #ffb800 0%, #ff8c00 100%)' : 'white',
            color: selectedCategory === 'PRICELIST' ? 'white' : '#555',
            fontSize: '0.9rem',
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
  );
}