import React from 'react';

export default function ProductsGrid({ products }) {
  return (
    <div style={{ flex: 1, padding: '30px', background: '#f5f5f5' }}>
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))',
        gap: '15px'
      }}>
        {products.map((product) => (
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
  );
}