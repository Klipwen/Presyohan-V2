import React from 'react';

export default function StoreSearchBar({ searchQuery, setSearchQuery }) {
  return (
    <div style={{ padding: '20px 30px', background: 'white', borderBottom: '1px solid #e0e0e0' }}>
      <div style={{
        display: 'flex',
        alignItems: 'center',
        background: '#f5f5f5',
        borderRadius: '25px',
        padding: '10px 20px',
        gap: '10px'
      }}>
        <svg width="20" height="20" fill="none" stroke="#999" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/>
        </svg>
        <input 
          type="text" 
          placeholder="Search item" 
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          style={{
            flex: 1,
            border: 'none',
            background: 'transparent',
            outline: 'none',
            fontSize: '0.95rem',
            color: '#333'
          }}
        />
        <button style={{
          width: '40px',
          height: '40px',
          background: '#00bcd4',
          border: 'none',
          borderRadius: '50%',
          cursor: 'pointer',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center'
        }}>
          <svg width="18" height="18" fill="none" stroke="white" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/>
          </svg>
        </button>
      </div>
    </div>
  );
}