import React from 'react';
import '../../styles/StoreModals.css';

export default function ImageDetailModal({ open, onClose, item }) {
  if (!open || !item) return null;

  // Generate a placeholder image URL based on item name
  const getImageUrl = (itemName) => {
    // For now, we'll use a placeholder service that generates images based on text
    // In a real app, this would be the actual item image URL from storage
    const encodedName = encodeURIComponent(itemName);
    return `https://via.placeholder.com/400x300/ff8c00/ffffff?text=${encodedName}`;
  };

  return (
    <div 
      className="modal-overlay" 
      role="dialog" 
      aria-modal="true" 
      onClick={(e) => { if (e.target === e.currentTarget) onClose?.(); }}
    >
      <div className="modal-card" style={{ maxWidth: '500px' }}>
        <div className="modal-header-strip">
          <h2>Item Image</h2>
          {onClose && (
            <button className="close-btn" onClick={onClose} aria-label="Close" />
          )}
        </div>
        
        <div className="modal-form" style={{ textAlign: 'center' }}>
          {/* Item Image */}
          <div style={{ 
            marginBottom: '20px',
            borderRadius: '12px',
            overflow: 'hidden',
            boxShadow: '0 4px 12px rgba(0,0,0,0.1)'
          }}>
            <img 
              src={getImageUrl(item.name)} 
              alt={item.name}
              style={{ 
                width: '100%', 
                height: '300px', 
                objectFit: 'cover',
                display: 'block'
              }}
              onError={(e) => {
                // Fallback to a different placeholder if the first one fails
                e.target.src = `https://via.placeholder.com/400x300/cccccc/666666?text=No+Image`;
              }}
            />
          </div>
          
          {/* Item Details */}
          <div style={{ textAlign: 'left', marginBottom: '20px' }}>
            <h3 style={{ 
              margin: '0 0 8px 0', 
              fontSize: '1.3rem', 
              fontWeight: '600', 
              color: '#333' 
            }}>
              {item.name}
            </h3>
            
            {item.description && (
              <p style={{ 
                margin: '0 0 12px 0', 
                fontSize: '1rem', 
                color: '#666',
                lineHeight: '1.5'
              }}>
                {item.description}
              </p>
            )}
            
            <div style={{ 
              display: 'flex', 
              justifyContent: 'space-between', 
              alignItems: 'center',
              padding: '12px 0',
              borderTop: '1px solid #eee'
            }}>
              <span style={{ 
                fontSize: '1.4rem', 
                fontWeight: '700', 
                color: '#ff8c00' 
              }}>
                ₱{item.price?.toFixed(2) || '0.00'}
              </span>
              <span style={{ 
                fontSize: '1rem', 
                color: '#999',
                background: '#f5f5f5',
                padding: '6px 12px',
                borderRadius: '16px'
              }}>
                per {item.unit}
              </span>
            </div>
            
            {item.category && (
              <div style={{ marginTop: '12px' }}>
                <span style={{ 
                  fontSize: '0.9rem', 
                  color: '#666',
                  background: '#e3f2fd',
                  padding: '4px 10px',
                  borderRadius: '12px',
                  border: '1px solid #bbdefb'
                }}>
                  Category: {item.category}
                </span>
              </div>
            )}
          </div>
          
          {/* Close button */}
          <div className="modal-actions" style={{ justifyContent: 'center' }}>
            <button className="btn teal" onClick={onClose}>
              Close
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}