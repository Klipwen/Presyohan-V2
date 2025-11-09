import React from 'react';
import '../../styles/StoreModals.css';

export default function ConfirmModal({ open, title, message, confirmLabel = 'Confirm', cancelLabel = 'Cancel', onConfirm, onCancel }) {
  if (!open) return null;
  return (
    <div className="modal-overlay" onClick={onCancel}>
      <div className="modal-card" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header-strip">
          <h2>{title}</h2>
          <button className="close-btn" onClick={onCancel} aria-label="Close" />
        </div>
        <div style={{ padding: '18px 20px' }}>
          <p style={{ color: '#333', marginBottom: '20px' }}>{message}</p>
          <div className="modal-actions">
            <button className="btn" onClick={onCancel}>{cancelLabel}</button>
            <button className="btn orange" onClick={onConfirm}>{confirmLabel}</button>
          </div>
        </div>
      </div>
    </div>
  );
}