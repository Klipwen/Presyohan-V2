import React from 'react';
import '../../styles/StoreModals.css';

export default function ConfirmationModal({
  open,
  title = 'Confirm',
  message = '',
  confirmText = 'Confirm',
  cancelText = 'Cancel',
  onConfirm,
  onCancel
}) {
  if (!open) return null;
  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" onClick={(e) => { if (e.target === e.currentTarget) onCancel?.(); }}>
      <div className="modal-card" style={{ maxWidth: 460 }}>
        <div className="modal-header-strip">
          <h2>{title}</h2>
          {onCancel && (<button className="close-btn" onClick={onCancel} aria-label="Close" />)}
        </div>
        <div className="modal-form">
          <p style={{ color: '#555', fontSize: '0.95rem' }}>{message}</p>
          <div className="modal-actions" style={{ justifyContent: 'space-between' }}>
            <button className="btn teal" type="button" onClick={onCancel}>{cancelText}</button>
            <button className="btn orange" type="button" onClick={onConfirm}>{confirmText}</button>
          </div>
        </div>
      </div>
    </div>
  );
}