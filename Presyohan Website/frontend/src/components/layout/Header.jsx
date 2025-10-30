import React from 'react';
import authHeaderLogo from '../../assets/ic_launcher.png';
import storeHeaderLogo from '../../assets/presyohan_logo.png';
import { Link } from 'react-router-dom';

// Header with two variants: default (logo + nav) and store (hamburger + logo + icons)
// Optional theme "white" makes the default header background white (login, verify pages).
export default function Header({ variant = 'default', onToggleSideMenu, theme }) {
  if (variant === 'store') {
    return (
      <header>
        <div style={{ display: 'flex', alignItems: 'center', gap: '15px' }}>
          <button className="hamburger-btn" onClick={onToggleSideMenu}>
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
    );
  }

  return (
    <header>
      <div className="header-logo">
        <img src={authHeaderLogo} alt="Presyohan Logo" />
        <h2><span className="presyo">presyo</span><span className="han">han?</span></h2>
      </div>
      <nav className="header-nav">
        <Link to="/login">Login</Link>
        <Link to="/store">Store</Link>
        <Link to="/verify-email">Verify Email</Link>
      </nav>
    </header>
  );
}