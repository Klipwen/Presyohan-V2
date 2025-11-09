import React, { useState } from 'react';
import authHeaderLogo from '../../assets/presyohan_logo.png';
import { Link, useLocation } from 'react-router-dom';
import '../../styles/AuthHeader.css';

// Responsive header for authentication pages
// Preserves existing design tokens and colors defined in LoginSignUpPage.css
export default function AuthHeader() {
  const [menuOpen, setMenuOpen] = useState(false);
  const location = useLocation();
  const isLanding = location.pathname === '/';
  const currentHash = isLanding ? (location.hash || '#home') : '';

  const toggleMenu = () => setMenuOpen((prev) => !prev);
  const closeMenu = () => setMenuOpen(false);

  return (
    <header className="auth-header">
      <Link to="/#home" className="header-logo" aria-label="Go to Home">
        <img src={authHeaderLogo} alt="Presyohan Logo" />
        <h2>
          <span className="presyo">presyo</span>
          <span className="han">han?</span>
        </h2>
      </Link>

      {/* Desktop navigation */}
      <nav className={`nav-links ${menuOpen ? 'open' : ''}`}>
        <div className="links">
          <Link to="/#home" onClick={closeMenu} className={isLanding && (currentHash === '' || currentHash === '#home') ? 'active' : ''}>Home</Link>
          <Link to="/#about" onClick={closeMenu} className={isLanding && currentHash === '#about' ? 'active' : ''}>About</Link>
          <Link to="/#features" onClick={closeMenu} className={isLanding && currentHash === '#features' ? 'active' : ''}>Features</Link>
          <Link to="/contact" onClick={closeMenu}>Contact Us</Link>
        </div>

        <div className="cta-buttons">
          <Link className={`cta-primary ${isLanding && currentHash === '#download' ? 'active' : ''}`} to="/#download" onClick={closeMenu}>Download App</Link>
          <Link className="cta-secondary" to="/login" onClick={closeMenu}>Login / Sign Up</Link>
        </div>
      </nav>

      {/* Hamburger (mobile) */}
      <button
        className={`hamburger ${menuOpen ? 'is-active' : ''}`}
        aria-label="Toggle navigation"
        aria-expanded={menuOpen}
        onClick={toggleMenu}
        type="button"
      >
        <span className="hamburger-box">
          <span className="hamburger-inner" />
        </span>
      </button>
    </header>
  );
}