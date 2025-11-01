import React from 'react';
import { useNavigate } from 'react-router-dom';

export default function StoreHeader({ storeName, storeBranch, onBack }) {
  const navigate = useNavigate();
  return (
    <div style={{
      background: 'white',
      padding: '20px 30px',
      borderBottom: '1px solid #e0e0e0'
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '15px' }}>
        <button
          onClick={() => {
            if (onBack) {
              onBack();
            } else {
              navigate('/stores');
            }
          }}
          style={{
          width: '40px',
          height: '40px',
          background: 'transparent',
          border: 'none',
          cursor: 'pointer',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: '#ff8c00'
        }}>
          <svg width="24" height="24" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 19l-7-7 7-7"/>
          </svg>
        </button>
        <div style={{
          width: '45px',
          height: '45px',
          background: '#ff8c00',
          borderRadius: '8px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center'
        }}>
          <svg width="28" height="28" fill="white" viewBox="0 0 24 24">
            <path d="M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 14H4v-6h16v6zm0-10H4V6h16v2z"/>
          </svg>
        </div>
        <div style={{ flex: 1 }}>
          <h1 style={{ fontSize: '1.3rem', fontWeight: '700', margin: 0, color: '#ff8c00' }}>{storeName}</h1>
          <p style={{ fontSize: '0.85rem', color: '#999', margin: 0 }}>| {storeBranch}</p>
        </div>
        <button style={{
          width: '40px',
          height: '40px',
          background: 'transparent',
          border: 'none',
          cursor: 'pointer',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: '#ff8c00'
        }}>
          <svg width="24" height="24" fill="currentColor" viewBox="0 0 24 24">
            <path d="M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"/>
          </svg>
        </button>
      </div>
    </div>
  );
}