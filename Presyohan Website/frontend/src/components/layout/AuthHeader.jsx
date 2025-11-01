import React from 'react';
import authHeaderLogo from '../../assets/ic_launcher.png';
import { Link } from 'react-router-dom';

// Header component specifically for authentication pages (login, verify)
// Simple header with logo and navigation links
export default function AuthHeader({ theme }) {
  return (
    <header>
      <div className="header-logo">
        <img src={authHeaderLogo} alt="Presyohan Logo" />
        <h2><span className="presyo">presyo</span><span className="han">han?</span></h2>
      </div>
      <nav className="header-nav">
        <Link to="/login">Login</Link>
        <Link to="/stores">Stores</Link>
        <Link to="/verify-email">Verify Email</Link>
      </nav>
    </header>
  );
}