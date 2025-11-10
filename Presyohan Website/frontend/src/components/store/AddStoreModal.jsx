import React, { useEffect, useState } from 'react';
import AddStore from './AddStore';
import JoinStore from './JoinStore';
// ... existing code ...
import storeIcon from '../../assets/icon_store.png';

export default function AddStoreModal({ open, onClose, onJoin, onCreate, joinStatus }) {
  const [view, setView] = useState('choose'); // choose | create | join

  useEffect(() => {
    if (!open) setView('choose');
  }, [open]);

  const closeAndReset = () => {
    setView('choose');
    onClose?.();
  };

  if (!open) return null;

  if (view === 'create') {
    return (
      <AddStore
        onCreate={(payload) => {
          onCreate?.(payload);
          closeAndReset();
        }}
        onDismiss={() => setView('choose')}
      />
    );
  }

  if (view === 'join') {
    return (
      <JoinStore
        onJoin={(code) => {
          onJoin?.(code);
          // Keep modal open so we can show inline status labels
        }}
        actionStatus={joinStatus}
        onDismiss={() => setView('choose')}
      />
    );
  }

  return (
    <div className={`modal ${open ? 'active' : ''}`} id="addStoreModal" onClick={(e) => { if (e.target === e.currentTarget) closeAndReset(); }}>
      <div className="modal-content">
        <h2 className="modal-title">Add Store</h2>
        <div className="modal-icon">
          <img src={storeIcon} alt="Store" />
        </div>
        <div className="modal-buttons">
          <button className="modal-btn btn-join" onClick={() => setView('join')}>Join Store</button>
          <button className="modal-btn btn-create" onClick={() => setView('create')}>Create Store</button>
        </div>
      </div>
    </div>
  );
}