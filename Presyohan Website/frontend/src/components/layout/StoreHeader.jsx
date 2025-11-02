import React, { useState } from 'react';
import storeHeaderLogo from '../../assets/presyohan_logo.png';
import { Link } from 'react-router-dom';
import { supabase } from '../../config/supabaseClient';
import '../../styles/StoreHeader.css';
import storeIcon from '../../assets/icon_store.png';

// StoreHeader with side-menu layout
export default function StoreHeader({ userName = 'User Name', userEmail = 'email@example.com', stores = [], onLogout }) {
  const [sideMenuOpen, setSideMenuOpen] = useState(false);
  const [storesExpanded, setStoresExpanded] = useState(false);

  const toggleSideMenu = () => setSideMenuOpen((v) => !v);
  const closeSideMenu = () => setSideMenuOpen(false);

  return (
    <>
      {/* Top header */}
      <header>
        <div style={{ display: 'flex', alignItems: 'center', gap: '15px' }}>
          <button className="hamburger-btn" onClick={toggleSideMenu} aria-label="Open menu">
            <span></span>
            <span></span>
            <span></span>
          </button>
          <div className="header-logo">
            <img src={storeHeaderLogo} alt="Presyohan Logo" />
            <h2><span className="presyo">presyo</span><span className="han">han?</span></h2>
          </div>
        </div>
        <div className="header-icons">
          <button className="icon-btn" title="Notifications">
            <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"/>
            </svg>
          </button>
          <button className="icon-btn" title="Profile">
            <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"/>
            </svg>
          </button>
        </div>
      </header>

      {/* Side menu overlay and drawer */}
      <div className={`side-menu-overlay ${sideMenuOpen ? 'active' : ''}`} onClick={closeSideMenu}></div>
      <div className={`side-menu ${sideMenuOpen ? 'active' : ''}`} role="dialog" aria-modal="true">
        {/* Menu Header */}
        <div className="side-menu-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: '15px' }}>
            {/* Profile pic placeholder */}
            <div style={{
              width: '50px', height: '50px', borderRadius: '50%',
              background: 'rgba(255,255,255,0.25)', display: 'flex',
              alignItems: 'center', justifyContent: 'center', color: 'white',
              fontWeight: 700
            }}>U</div>
            <div>
              <h3 style={{ margin: 0, fontSize: '1.1rem' }}>{userName}</h3>
              <p style={{ margin: '4px 0 6px', fontSize: '0.9rem', opacity: 0.9 }}>{userEmail}</p>
              <a href="#" style={{ color: 'white', textDecoration: 'underline', fontSize: '0.9rem' }}>View Profile</a>
            </div>
          </div>
        </div>

        {/* Menu Items */}
        <div className="side-menu-items">
          {/* Stores with dropdown */}
          <button className="menu-item" onClick={() => setStoresExpanded((v) => !v)} style={{ width: '100%', background: 'none', border: 'none', textAlign: 'left' }}>
            <svg fill="currentColor" viewBox="0 0 24 24">
              <path d="M3 21h18v-2H3v2zm0-4h18v-2H3v2zm0-4h18v-2H3v2zm0-4h18V7H3v2zm0-6v2h18V3H3z"/>
            </svg>
            <span>Stores</span>
            <svg className={`submenu-toggle-caret ${storesExpanded ? 'expanded' : ''}`} width="18" height="18" viewBox="0 0 24 24" fill="currentColor" style={{ marginLeft: 'auto' }}>
              <path d="M7 10l5 5 5-5H7z"/>
            </svg>
          </button>
          {storesExpanded && (
            <div className="submenu">
              {Array.isArray(stores) && stores.length > 0 ? (
                stores.map((s) => (
                  <Link
                    key={s.id ?? s.name}
                    to={`/store?id=${encodeURIComponent(s.id ?? '')}`}
                    className="submenu-item"
                    onClick={closeSideMenu}
                  >
                    <img src={storeIcon} alt="Store" className="submenu-store-icon" />
                    <span>{s.name}{s.branch ? ` — ${s.branch}` : ''}</span>
                  </Link>
                ))
              ) : (
                <>
                  <Link to="/stores" className="submenu-item" onClick={closeSideMenu}>
                    <img src={storeIcon} alt="Stores" className="submenu-store-icon" />
                    <span>All Stores</span>
                  </Link>
                  <Link to="/store" className="submenu-item" onClick={closeSideMenu}>
                    <img src={storeIcon} alt="Shop" className="submenu-store-icon" />
                    <span>Shop Products</span>
                  </Link>
                </>
              )}
            </div>
          )}

          {/* Notifications */}
          <a href="#" className="menu-item">
            <svg fill="currentColor" viewBox="0 0 24 24">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"/>
            </svg>
            <span>Notifications</span>
          </a>

          {/* Settings */}
          <a href="#" className="menu-item">
            <svg fill="currentColor" viewBox="0 0 24 24">
              <path d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.07.62-.07.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17 .47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22 .08 .47 0 .59-.22l1.92-3.32c.12-.22 .07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"/>
            </svg>
            <span>Settings</span>
          </a>

          {/* Support */}
          <a href="#" className="menu-item">
            <svg fill="currentColor" viewBox="0 0 24 24">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"/>
            </svg>
            <span>Support</span>
          </a>

          <div className="menu-divider"></div>

          {/* Logout */}
          <a
            href="#"
            className="menu-item"
            onClick={async (e) => {
              e.preventDefault();
              try {
                if (onLogout) {
                  await onLogout();
                } else {
                  await supabase.auth.signOut();
                  window.location.href = '/login';
                }
              } catch (err) {
                console.error('Logout failed', err);
                // Fallback redirect even if signOut fails
                window.location.href = '/login';
              }
            }}
          >
            <svg fill="currentColor" viewBox="0 0 24 24">
              <path d="M17 7l-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5zM4 5h8V3H4c-1.1 0-2 .9-2 2v14c0 1.1 .9 2 2 2h8v-2H4V5z"/>
            </svg>
            <span>Logout</span>
          </a>
        </div>
      </div>
    </>
  );
}