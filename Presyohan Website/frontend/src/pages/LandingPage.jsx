import React, { useEffect, useLayoutEffect, useRef, useState } from 'react';
import '../styles/LandingPage.css';
import AuthHeader from '../components/layout/AuthHeader';
import Footer from '../components/layout/Footer';
import { useNavigate, useLocation } from 'react-router-dom';
import presyohanLogo from '../assets/presyohan_logo.png';
import { supabase } from '../config/supabaseClient';
import splashImg from '../assets/presyohan app sample/Splash.png';
import loginImg from '../assets/presyohan app sample/Login.png';
import addStoreImg from '../assets/presyohan app sample/Add Store.png';
import storeItemsImg from '../assets/presyohan app sample/Store Items.png';
import productsImg from '../assets/presyohan app sample/Products.png';
import storeMgmtImg from '../assets/presyohan app sample/Store Management.png';

export default function LandingPage() {
  const navigate = useNavigate();
  const location = useLocation();
  // Carousel state: slides per view and current index (bounded)
  const [slidesPerView, setSlidesPerView] = useState(1);
  const [currentSlide, setCurrentSlide] = useState(0);
  const viewportRef = useRef(null);
  const [viewportWidth, setViewportWidth] = useState(0);
  const collageRef = useRef(null);
  const [apkUrl, setApkUrl] = useState(import.meta.env.VITE_DOWNLOAD_APK_URL || '');

  // Temporary orange glow feedback on click for cards (kept inside component)
  const glowTimeoutsRef = useRef(new Map());
  const triggerGlow = (el) => {
    if (!el) return;
    el.classList.add('glow-click');
    const prev = glowTimeoutsRef.current.get(el);
    if (prev) clearTimeout(prev);
    const tid = setTimeout(() => {
      el.classList.remove('glow-click');
      glowTimeoutsRef.current.delete(el);
    }, 600);
    glowTimeoutsRef.current.set(el, tid);
  };
  const handleCardClick = (e) => {
    triggerGlow(e.currentTarget);
  };

  

  // Scroll to section when hash present (e.g., /#about, /#features, /#download)
  useEffect(() => {
    if (location.hash) {
      const id = location.hash.replace('#', '');
      const el = document.getElementById(id);
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    }
  }, [location]);

  useEffect(() => {
    if (!apkUrl) {
      try {
        const { data } = supabase.storage.from('presyohan.apk').getPublicUrl('presyohan.apk');
        if (data?.publicUrl) setApkUrl(data.publicUrl);
      } catch (_) {}
    }
  }, [apkUrl]);
  const comparisonRows = [
    {
      feature: 'Price Updates',
      old: 'Print new sheets, text managers, hope everyone remembers.',
      new: 'Instant Updates: Owners and Managers can update prices in seconds, and the changes appear instantly for all members.'
    },
    {
      feature: 'New Staff Onboarding',
      old: 'Weeks of training on price changes and product locations.',
      new: 'Zero Training Required: New staff instantly see the current, official price list and product information.'
    },
    {
      feature: 'Error Reduction',
      old: 'Human error from misreading a list or forgetting a price change.',
      new: 'Zero Price Confusion: A single, consistent source of truth eliminates arguments and customer disputes.'
    },
    {
      feature: 'Multi-Store Management',
      old: 'Manual spreadsheets and different lists for every branch.',
      new: 'Copy & Scale: Effortlessly copy entire price lists, promotions, and categories across your store branches to maintain standards.'
    },
    {
      feature: 'Bulk Import & Export',
      old: 'Manual updates across spreadsheets; copy-paste errors and rework.',
      new: 'Excel/CSV import with flexible mapping and clean exports for printing — fast and reliable.'
    },
    {
      feature: 'Member Access & Accountability',
      old: 'Shared accounts and uncontrolled edits with no clear audit trail.',
      new: 'Role-based permissions (Owner/Manager/Sale Staff) with audit timestamps to track every change.'
    }
  ];

  const totalSlides = comparisonRows.length;

  // Responsive slides per view: show 3 on large, 2 on tablet, 1 on mobile
  useEffect(() => {
    const updateSPV = () => {
      const w = window.innerWidth;
      if (w >= 1200) setSlidesPerView(3);
      else if (w >= 768) setSlidesPerView(2);
      else setSlidesPerView(1);
    };
    updateSPV();
    window.addEventListener('resize', updateSPV);
    return () => window.removeEventListener('resize', updateSPV);
  }, []);

  // Track viewport width for accurate translate with gap spacing
  useLayoutEffect(() => {
    const readWidth = () => {
      if (viewportRef.current) setViewportWidth(viewportRef.current.offsetWidth);
    };
    readWidth();
    window.addEventListener('resize', readWidth);
    return () => window.removeEventListener('resize', readWidth);
  }, []);

  useEffect(() => {
    const el = collageRef.current;
    if (!el) return;
    let paused = false;
    let rafId;
    const step = () => {
      if (!paused) {
        if (el.scrollTop + el.clientHeight >= el.scrollHeight) {
          el.scrollTo({ top: 0, behavior: 'smooth' });
        } else {
          el.scrollTop += 0.4;
        }
      }
      rafId = requestAnimationFrame(step);
    };
    rafId = requestAnimationFrame(step);
    const onEnter = () => { paused = true; };
    const onLeave = () => { paused = false; };
    el.addEventListener('mouseenter', onEnter);
    el.addEventListener('mouseleave', onLeave);
    return () => {
      cancelAnimationFrame(rafId);
      el.removeEventListener('mouseenter', onEnter);
      el.removeEventListener('mouseleave', onLeave);
    };
  }, []);

  // Number of pages = totalSlides - slidesPerView + 1 (bounded)
  const pageCount = Math.max(1, totalSlides - slidesPerView + 1);
  const prevSlide = () => setCurrentSlide((i) => Math.max(0, i - 1));
  const nextSlide = () => setCurrentSlide((i) => Math.min(pageCount - 1, i + 1));

  // Normal bounded sliding (no loop resets, no autoplay)

  // Autoplay removed as requested

  const roles = [
    {
      icon: '👑',
      title: 'Store Owners',
      subtitle: 'Full Control',
      description: 'Manage members, handle bulk imports/exports, and access all settings.'
    },
    {
      icon: '⚙️',
      title: 'Managers',
      subtitle: 'Day-to-Day Operations',
      description: 'Create, update, and delete products and categories. Keep the store running smoothly.'
    },
    {
      icon: '👁️',
      title: 'Sale Staff',
      subtitle: 'View-Only Access',
      description: 'Securely view the most current prices and product details on the fly.'
    }
  ];

  const feature1Cards = [
    {
      icon: '✏️',
      title: 'Product & Item CRUD',
      description:
        'Easily create, edit, and update product details, including name, price, unit, and description. Every change is instantly visible to all store members.'
    },
    {
      icon: '🗂️',
      title: 'Category Management',
      description:
        'Organize your products with flexible categories. Deleting a category automatically handles associated products to maintain a clean and consistent database.'
    },
    {
      icon: '🔍',
      title: 'Advanced Search & Filter',
      description:
        'Quickly find any product by name, category, or unit. Use sorting and filtering by price range or last updated time to manage large catalogs with ease.'
    }
  ];

  const feature2Cards = [
    {
      icon: '🔐',
      title: 'Role-Based Access',
      description:
        'Enforce strict permissions: Owners have full control, Managers can edit products, and Sale Staff have secure, view-only access.'
    },
    {
      icon: '✉️',
      title: 'Easy Member Invitation',
      description:
        'Invite new Managers or Sale Staff by sending an email invitation or generating a secure, expiring invite code they can redeem to join your store.'
    },
    {
      icon: '🔄',
      title: 'Ownership Transfer',
      description:
        "Securely transfer the Owner role to another Manager when needed, ensuring the store's control remains protected and accountable."
    },
    {
      icon: '🏪',
      title: 'Store Management',
      description:
        'Create new stores and manage settings like name and branch details. Owners can delete a store only after ensuring another owner exists.'
    }
  ];

  const feature3Cards = [
    {
      icon: '📥',
      title: 'Bulk Import from Excel/CSV',
      description:
        'Import hundreds of products instantly. Use our mapping UI to link your spreadsheet columns to database fields, preview changes, and manage conflicts (overwrite or skip).'
    },
    {
      icon: '🖨️',
      title: 'Price List Export & Print',
      description:
        'Generate and export your entire price catalog to an Excel (.xlsx) file, perfect for printing hard copies or internal record-keeping.'
    },
    {
      icon: '📋',
      title: 'Copy Prices Between Stores',
      description:
        'Standardize pricing by copying an entire price list from one branch to another, complete with a conflict resolution preview.'
    },
    {
      icon: '🔔',
      title: 'Activity Notifications',
      description:
        'Stay informed with a simple notification system for important events like new member invitations, role changes, and large data imports.'
    }
  ];

  const collageCards = [
    { cls: 'dark1', label: 'Price Lookup' },
    { cls: 'dark2', label: 'Catalog' },
    { cls: 'dark1', label: 'Categories' },
    { cls: 'dark2', label: 'Promotions' }
  ];

  return (
    <div className="lp-root">
      {/* Global Header */}
      <AuthHeader />
      {/* Hero Section */}
      <section id="home" className="lp-hero">
        <div className="lp-hero-pattern" />
        {/* Hero grid: logo left, content box right */}
        <div className="lp-hero-grid">
          <div className="lp-hero-left">
            <div className="lp-logo-box">
              <img src={presyohanLogo} alt="Presyohan Logo" />
            </div>
          </div>

          {/* Hero content box with professional layout */}
          <div className="lp-hero-right">
            <div className="lp-hero-box">
              <div className="lp-hero-content">
                {/* Logo text: "atong" aligned with "presyohan?" */}
                <div className="lp-logo-text">
                  <span className="lp-logo-eyebrow">atong</span>
                  <h1 className="lp-title">
                    <span className="lp-title-white">presyo</span>
                    <span className="lp-title-accent">han?</span>
                  </h1>
                </div>

                <p className="lp-lead">
                  We make it fast and easy to manage your store's entire price list, guaranteeing consistency across all staff and locations.
                </p>

                {/* Subtitle grouped with Get Started button */}
                <div className="lp-cta-group">
                  <h2 className="lp-subtitle">Wala Kahibaw sa Presyo?</h2>
                  <button className="lp-btn-primary" onClick={() => navigate('/login')}>Get Started</button>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Arrow down to About section with smooth scroll */}
        <a
          href="#about"
          className="lp-scroll-arrow"
          aria-label="Scroll to About section"
          onClick={(e) => {
            e.preventDefault();
            const el = document.getElementById('about');
            if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
          }}
        >
          <svg viewBox="0 0 24 24" width="28" height="28" aria-hidden="true">
            <path d="M6 9l6 6 6-6" stroke="currentColor" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </a>
      </section>

      {/* About Section */}
      <section id="about" className="lp-section lp-about">
        <div className="lp-container">
          <h2 className="lp-section-title">About Presyohan: Your Smart Pricing Command Center</h2>
          <h3 className="lp-section-subtitle">Stop the Price Guessing Game. Start Selling Smarter.</h3>
          <p className="lp-section-text">
            Presyohan is a centralized, role-based platform designed to eliminate pricing errors and confusion in your store. Think of it as the single, authoritative source of truth for every product price, available instantly to everyone on your team—on both web and mobile. We take the stress out of store operations so you and your team can focus on serving your customers.
          </p>

          <h3 className="lp-block-title">How Presyohan Transforms Your Store:</h3>

          <div className="lp-carousel">
            <button
              className={`lp-carousel-arrow left ${currentSlide === 0 ? 'disabled' : ''}`}
              onClick={prevSlide}
              aria-label="Previous"
            >
              <svg viewBox="0 0 24 24" width="22" height="22"><path fill="currentColor" d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z"/></svg>
            </button>
            <div className="lp-carousel-viewport" ref={viewportRef}>
              <div
                className="lp-carousel-track"
                style={{
                  transform: (() => {
                    const GAP_PX = 16; // must match CSS track gap
                    const gapPercent = viewportWidth > 0 ? (GAP_PX * 100) / viewportWidth : 0;
                    const perSlidePercent = 100 / slidesPerView + gapPercent;
                    return `translateX(-${currentSlide * perSlidePercent}%)`;
                  })(),
                  transition: 'transform 320ms ease'
                }}
              >
                {comparisonRows.map((row, index) => (
                  <div
                    key={index}
                    className="lp-card slide"
                    style={{ flex: `0 0 ${100 / slidesPerView}%` }}
                    onClick={handleCardClick}
                  >
                    <div className="lp-card-content">
                      <h4 className="lp-card-title">{row.feature}</h4>
                      <div className="lp-old">
                        <div className="lp-old-label">The Old Way:</div>
                        <p className="lp-old-text">{row.old}</p>
                      </div>
                      <div className="lp-new">
                        <div className="lp-new-label">The Presyohan Way:</div>
                        <p className="lp-new-text">{row.new}</p>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
            {/* Dots navigation */}
            <div className="lp-carousel-dots">
              {Array.from({ length: pageCount }).map((_, i) => (
                <button
                  key={i}
                  className={`dot ${currentSlide === i ? 'active' : ''}`}
                  onClick={() => setCurrentSlide(i)}
                  aria-label={`Go to slide ${i + 1}`}
                />
              ))}
            </div>
            <button
              className={`lp-carousel-arrow right ${currentSlide >= pageCount - 1 ? 'disabled' : ''}`}
              onClick={nextSlide}
              aria-label="Next"
            >
              <svg viewBox="0 0 24 24" width="22" height="22"><path fill="currentColor" d="M8.59 16.59L13.17 12 8.59 7.41 10 6l6 6-6 6z"/></svg>
            </button>
          </div>

          <h3 className="lp-block-title">Powerful Tools for Every Role</h3>

          <div className="lp-grid">
            {roles.map((role, index) => (
              <div key={index} className="lp-role-card" onClick={handleCardClick}>
                <div className="lp-role-icon">{role.icon}</div>
                <h4 className="lp-role-title">{role.title}</h4>
                <div className="lp-role-subtitle">{role.subtitle}</div>
                <p className="lp-role-text">{role.description}</p>
              </div>
            ))}
          </div>

          <p className="lp-info">Presyohan ensures your data is secure, responsive, and always ready for you.</p>
        </div>
      </section>

      {/* Features Section */}
      <section id="features" className="lp-features lp-section">
        <div className="lp-container">
          <h2 className="lp-section-title">Comprehensive Platform Features</h2>
          <p className="lp-section-text lp-section-text-center">
            Presyohan is more than just a price list—it's a comprehensive platform designed to give Store Owners and Managers complete control over pricing, inventory data, and staff access.
          </p>

          {/* Feature 1 */}
          <div className="lp-feature-group">
            <div className="lp-feature-head">
              <div className="lp-icon-box orange">📦</div>
              <h3 className="lp-feature-title">Unified Product &amp; Category Management</h3>
            </div>
            <p className="lp-section-text">Manage your entire product catalog efficiently with robust Create, Read, Update, and Delete (CRUD) functionality.</p>
            <div className="lp-grid cards-3">
              {feature1Cards.map((item, index) => (
                <div key={index} className="lp-mini-card" onClick={handleCardClick}>
                  <div className="lp-mini-icon">{item.icon}</div>
                  <h4 className="lp-mini-title">{item.title}</h4>
                  <p className="lp-mini-text">{item.description}</p>
                </div>
              ))}
            </div>
          </div>

          {/* Feature 2 */}
          <div className="lp-feature-group">
            <div className="lp-feature-head">
              <div className="lp-icon-box teal">👥</div>
              <h3 className="lp-feature-title">Team &amp; Store Control</h3>
            </div>
            <p className="lp-section-text">Maintain security and accountability with precise, role-based controls over who can access and modify store data.</p>
            <div className="lp-grid cards-4">
              {feature2Cards.map((item, index) => (
                <div key={index} className="lp-mini-card" onClick={handleCardClick}>
                  <div className="lp-mini-icon">{item.icon}</div>
                  <h4 className="lp-mini-title">{item.title}</h4>
                  <p className="lp-mini-text">{item.description}</p>
                </div>
              ))}
            </div>
          </div>

          {/* Feature 3 */}
          <div className="lp-feature-group">
            <div className="lp-feature-head">
              <div className="lp-icon-box green">📊</div>
              <h3 className="lp-feature-title">Data Operations &amp; Auditing</h3>
            </div>
            <p className="lp-section-text">Move beyond manual data entry with powerful bulk tools and clear audit trails.</p>
            <div className="lp-grid cards-4">
              {feature3Cards.map((item, index) => (
                <div key={index} className="lp-mini-card" onClick={handleCardClick}>
                  <div className="lp-mini-icon">{item.icon}</div>
                  <h4 className="lp-mini-title">{item.title}</h4>
                  <p className="lp-mini-text">{item.description}</p>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* Download App Section */}
      <section id="download" className="lp-section lp-download">
        <div className="lp-container">
          <h2 className="lp-section-title">Get the Presyohan Mobile App</h2>
          <h3 className="lp-section-subtitle">Easy and Portable!</h3>
          <p className="lp-section-text lp-section-text-center">
            Say goodbye to hassle! Make it fast and easy to check the correct price right on the floor. No more running around or asking colleagues—get the price instantly and serve your customers better. <strong>Download the app now!</strong>
          </p>

          <div className="lp-grid-2col">
            <div>
              <h4 className="lp-subblock-title">Why the Mobile App?</h4>
              <ul className="lp-list">
                <li>Instant price lookup anywhere in your store</li>
                <li>Always synced with the latest official price list</li>
                <li>Works across branches with role-based access</li>
                <li>Simple, fast, and built for busy teams</li>
              </ul>
              <div className="lp-store-row">
                <div className="lp-download-label">Download the app now!</div>
                <a href={apkUrl} className="lp-download-btn" target="_blank" rel="noopener noreferrer">
                  <div>
                    <div className="lp-store-title">presyohan v2.0</div>
                  </div>
                </a>
              </div>
            </div>
            <div>
              <div className="lp-app-collage" ref={collageRef}>
                <div className="lp-app-shot"><img src={splashImg} alt="Splash screen" /><div className="lp-app-caption">Splash</div></div>
                <div className="lp-app-shot"><img src={loginImg} alt="Login screen" /><div className="lp-app-caption">Login</div></div>
                <div className="lp-app-shot"><img src={addStoreImg} alt="Add Store" /><div className="lp-app-caption">Add Store</div></div>
                <div className="lp-app-shot"><img src={storeItemsImg} alt="Store Items" /><div className="lp-app-caption">Store Items</div></div>
                <div className="lp-app-shot"><img src={productsImg} alt="Products" /><div className="lp-app-caption">Products</div></div>
                <div className="lp-app-shot"><img src={storeMgmtImg} alt="Store Management" /><div className="lp-app-caption">Store Management</div></div>
              </div>
              <div className="lp-collage-note">Presyohan app previews aligned to brand design.</div>
            </div>
          </div>
        </div>
      </section>
      
  {/* Global Footer */}
  <Footer />
  </div>
  );
}