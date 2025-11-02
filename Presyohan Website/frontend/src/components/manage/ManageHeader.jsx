import React from 'react';

export default function ManageHeader({ onBack, onDone }) {
  return (
    <div style={{
      background: 'white',
      padding: '15px 20px',
      borderBottom: '1px solid #e0e0e0',
      position: 'sticky',
      top: 0,
      zIndex: 100,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between'
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '15px' }}>
        <button onClick={onBack} style={{
          background: 'transparent',
          border: 'none',
          cursor: 'pointer',
          padding: '5px',
          color: '#ff8c00'
        }}>
          <svg width="24" height="24" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 19l-7-7 7-7"/>
          </svg>
        </button>
        <h1 style={{ 
          fontSize: '1.2rem', 
          fontWeight: '600', 
          color: '#ff8c00',
          margin: 0 
        }}>
          Manage Items
        </h1>
      </div>
      <button onClick={onDone} style={{
        background: '#ff8c00',
        color: 'white',
        border: 'none',
        padding: '8px 24px',
        borderRadius: '20px',
        fontSize: '0.9rem',
        fontWeight: '600',
        cursor: 'pointer'
      }}>
        DONE
      </button>
    </div>
  );
}