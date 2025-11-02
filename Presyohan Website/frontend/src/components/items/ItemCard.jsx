import React, { useState } from 'react';
import ConfirmationModal from '../common/ConfirmationModal';
import EditItemModal from './EditItemModal';
// Removed ImageDetailModal per design alignment

export default function ItemCard({ 
  item, 
  categories = [], 
  onEdit, 
  onDelete, 
  onAddCategory 
}) {
  const [showMenu, setShowMenu] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);

  const handleEdit = async (updatedItem) => {
    try {
      await onEdit?.(updatedItem);
      setShowEditModal(false);
      // Refresh the page to reflect updates
      window.location.reload();
    } catch (_) {
      // Errors are handled upstream
    }
  };

  const handleDelete = async () => {
    try {
      await onDelete?.(item.id);
      setShowDeleteConfirm(false);
      window.location.reload();
    } catch (_) {
      // Errors are handled upstream
    }
  };

  const toggleMenu = (e) => {
    e.stopPropagation();
    setShowMenu(!showMenu);
  };

  const closeMenu = () => {
    setShowMenu(false);
  };

  return (
    <>
      <div 
        style={{
          background: '#fff',
          borderRadius: '16px',
          padding: '16px',
          boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
          position: 'relative',
          cursor: 'pointer'
        }}
        onClick={closeMenu}
      >
        {/* Three dots menu button */}
        <div 
          style={{
            position: 'absolute',
            top: '12px',
            right: '12px',
            width: '24px',
            height: '24px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'pointer',
            borderRadius: '50%',
            background: showMenu ? '#f5f5f5' : 'transparent'
          }}
          onClick={toggleMenu}
        >
          <span style={{ 
            fontSize: '16px', 
            fontWeight: 'bold', 
            color: '#666',
            lineHeight: 1,
            transform: 'rotate(90deg)'
          }}>
            ⋯
          </span>
        </div>

        {/* Dropdown menu */}
        {showMenu && (
          <div 
            style={{
              position: 'absolute',
              top: '36px',
              right: '12px',
              background: '#fff',
              borderRadius: '8px',
              boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
              zIndex: 10,
              minWidth: '140px',
              overflow: 'hidden'
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <button
              style={{
                width: '100%',
                padding: '12px 16px',
                border: 'none',
                background: 'transparent',
                textAlign: 'left',
                cursor: 'pointer',
                fontSize: '14px',
                color: '#219EBC'
              }}
              onMouseEnter={(e) => e.target.style.background = '#f5f5f5'}
              onMouseLeave={(e) => e.target.style.background = 'transparent'}
              onClick={() => {
                setShowEditModal(true);
                closeMenu();
              }}
            >
              Edit Item
            </button>
            <button
              style={{
                width: '100%',
                padding: '12px 16px',
                border: 'none',
                background: 'transparent',
                textAlign: 'left',
                cursor: 'pointer',
                fontSize: '14px',
                color: '#e53935'
              }}
              onMouseEnter={(e) => e.target.style.background = '#ffebee'}
              onMouseLeave={(e) => e.target.style.background = 'transparent'}
              onClick={() => {
                setShowDeleteConfirm(true);
                closeMenu();
              }}
            >
              Delete Item
            </button>
          </div>
        )}

        {/* Item content */}
        <div style={{ marginRight: '32px', position: 'relative' }}> {/* Add position: relative here */}
          <h3 style={{ 
            margin: '0 0 8px 0', 
            fontSize: '1.1rem', 
            fontWeight: '600', 
            color: '#219EBC' 
          }}>
            {item.name}
          </h3>
          
          {item.description && (
            <p style={{ 
              margin: '0 0 12px 0', 
              fontSize: '0.9rem', 
              color: '#666',
              lineHeight: '1.4'
            }}>
              {item.description}
            </p>
          )}
          
          <div style={{ 
            display: 'flex', 
            justifyContent: 'space-between', 
            alignItems: 'center',
            marginTop: '12px'
          }}>
            <span style={{ 
              fontSize: '1.2rem', 
              fontWeight: '700', 
              color: '#ff8c00' 
            }}>
              ₱{item.price?.toFixed(2) || '0.00'}
            </span>
            {/* The unit span is now removed from here and placed outside the flex container */}
          </div>

          {/* Place the unit span directly inside the main container with absolute positioning */}
          <span style={{ 
            fontSize: '0.85rem', 
            color: '#999',
            padding: '6px 8px',
            position: 'absolute', /* Use absolute positioning */
            bottom: '0',        /* Align to the bottom of the parent */
            right: '0',         /* Align to the right of the parent */
          }}>
            {item.unit}
          </span>
        </div>
      </div>

      {/* Edit Modal */}
      <EditItemModal
        open={showEditModal}
        onClose={() => setShowEditModal(false)}
        item={item}
        categories={categories}
        onSave={handleEdit}
        onAddCategory={onAddCategory}
      />

      {/* View Image removed per request */}

      {/* Delete Confirmation Modal */}
      <ConfirmationModal
        open={showDeleteConfirm}
        title="Delete Item"
        message={`Are you sure you want to delete "${item.name}"? This action cannot be undone.`}
        confirmText="Delete"
        cancelText="Cancel"
        onConfirm={handleDelete}
        onCancel={() => setShowDeleteConfirm(false)}
      />
    </>
  );
}