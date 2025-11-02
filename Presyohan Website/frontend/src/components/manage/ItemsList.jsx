import React from 'react';

export default function ItemsList({ groupedItems, onEditItem, onDeleteItem, onAddItemToCategory }) {
  return (
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
                      fontSize: '0.82rem',
                      color: item.description ? '#666' : '#aaa',
                      marginBottom: '6px'
                    }}>
                      {item.description?.trim() ? item.description : 'No description'}
                    </div>
                    <div style={{ 
                      display: 'flex',
                      gap: '15px',
                      fontSize: '0.85rem',
                      color: '#555',
                      flexWrap: 'wrap'
                    }}>
                      <span>₱{item.price.toFixed(2)}</span>
                      <span>{item.unit}</span>
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
                    }} onClick={() => onEditItem?.(item)} aria-label={`Edit ${item.name}`}>
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
                    }} onClick={() => onDeleteItem?.(item)} aria-label={`Delete ${item.name}`}>
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
            }} onClick={() => onAddItemToCategory?.(categoryName)}>
              Add Item to {categoryName}
            </button>
          </div>
        );
      })}
    </div>
  );
}