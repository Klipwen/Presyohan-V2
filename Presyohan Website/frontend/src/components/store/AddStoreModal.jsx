import React from 'react';

export default function AddStoreModal({ open, onClose, onJoin, onCreate }) {
  return (
    <div className={`modal ${open ? 'active' : ''}`} id="addStoreModal" onClick={(e) => { if (e.target === e.currentTarget) onClose?.(); }}>
      <div className="modal-content">
        <h2 className="modal-title">Add Store</h2>
        <div className="modal-icon placeholder" aria-hidden="true"></div>
        <div className="modal-buttons">
          <button className="modal-btn btn-join" onClick={onJoin}>Join Store</button>
          <button className="modal-btn btn-create" onClick={onCreate}>Create Store</button>
        </div>
      </div>
    </div>
  );
}