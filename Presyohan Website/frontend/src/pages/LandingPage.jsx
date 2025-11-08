import React, { useEffect } from 'react';
import '../styles/LandingPage.css';
import AuthHeader from '../components/layout/AuthHeader';
import Footer from '../components/layout/Footer';
import { useNavigate, useLocation } from 'react-router-dom';
import presyohanLogo from '../assets/presyohan_logo.png';

export default function LandingPage() {
  const navigate = useNavigate();
  const location = useLocation();

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
    }
  ];

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
        <div className="lp-hero-content">
          <div className="lp-logo-box">
            <img src={presyohanLogo} alt="Presyohan Logo" />
          </div>

          <h3 className="lp-eyebrow">Atong</h3>

          <h1 className="lp-title">
            <span className="lp-title-white">presyo</span>
            <span className="lp-title-accent">han?</span>
          </h1>

          <h2 className="lp-subtitle">Wala Kahibaw sa Presyo?</h2>

          <p className="lp-lead">
            We make it fast and easy to manage your store's entire price list, guaranteeing consistency across all staff and locations.
          </p>

            <button className="lp-btn-primary" onClick={() => navigate('/login')}>Get Started</button>
        </div>
      </section>

      {/* About Section */}
      <section id="about" className="lp-section lp-about">
        <div className="lp-container">
          <h2 className="lp-section-title">About Presyohan: Your Smart Pricing Command Center</h2>
          <h3 className="lp-section-subtitle">Stop the Price Guessing Game. Start Selling Smarter.</h3>
          <p className="lp-section-text">
            Presyohan is a centralized, role-based platform designed to eliminate pricing errors and confusion in your store. Think of it as the single, authoritative source of truth for every product price, available instantly to everyone on your team—on both web and mobile. We take the stress out of store operations so you and your team can focus on serving your customers.
          </p>

          <h3 className="lp-block-title">How Presyohan Transforms Your Store</h3>

          <div className="lp-grid">
            {comparisonRows.map((row, index) => (
              <div key={index} className="lp-card">
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
            ))}
          </div>

          <h3 className="lp-block-title">Powerful Tools for Every Role</h3>

          <div className="lp-grid">
            {roles.map((role, index) => (
              <div key={index} className="lp-role-card">
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
                <div key={index} className="lp-mini-card">
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
                <div key={index} className="lp-mini-card">
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
                <div key={index} className="lp-mini-card">
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
                <a href="#" className="lp-store-badge apple">
                  <span className="lp-store-icon"></span>
                  <div>
                    <div className="lp-store-sub">Download on the</div>
                    <div className="lp-store-title">App Store</div>
                  </div>
                </a>
                <a href="#" className="lp-store-badge android">
                  <span className="lp-store-icon">🤖</span>
                  <div>
                    <div className="lp-store-sub">Get it on</div>
                    <div className="lp-store-title">Google Play</div>
                  </div>
                </a>
              </div>
            </div>
            <div>
              <div className="lp-mobile-collage">
                {collageCards.map((card, i) => (
                  <div key={i} className={`lp-mobile-card ${card.cls}`}>
                    <div className="lp-mobile-dots">• • •</div>
                    <div className="lp-mobile-label">{card.label}</div>
                    <div className="lp-mobile-shimmer" />
                  </div>
                ))}
              </div>
              <div className="lp-collage-note">Mobile collage placeholder — aligns with brand design.</div>
            </div>
          </div>
        </div>
      </section>
      
      {/* Global Footer */}
      <Footer />
    </div>
  );
}