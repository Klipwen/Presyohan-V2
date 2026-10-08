import React from 'react';
import { Link } from 'react-router-dom';
import presyohanLogo from '../assets/ic_launcher.png';
import '../styles/PrivacyPolicy.css';

const Icons = {
  Shield: () => (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
    </svg>
  ),
  Lock: () => (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
      <path d="M7 11V7a5 5 0 0 1 10 0v4" />
    </svg>
  ),
  Camera: () => (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z" />
      <circle cx="12" cy="13" r="4" />
    </svg>
  ),
  Server: () => (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="2" y="2" width="20" height="8" rx="2" ry="2" />
      <rect x="2" y="14" width="20" height="8" rx="2" ry="2" />
      <line x1="6" y1="6" x2="6.01" y2="6" />
      <line x1="6" y1="18" x2="6.01" y2="18" />
    </svg>
  ),
  UserCheck: () => (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
      <circle cx="8.5" cy="7" r="4" />
      <polyline points="17 11 19 13 23 9" />
    </svg>
  ),
  Trash: () => (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="3 6 5 6 21 6" />
      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
    </svg>
  ),
  ArrowLeft: () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
      <line x1="19" y1="12" x2="5" y2="12" />
      <polyline points="12 19 5 12 12 5" />
    </svg>
  ),
  ChevronRight: () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="9 18 15 12 9 6" />
    </svg>
  )
};

export default function PrivacyPolicyPage() {
  return (
    <div className="privacy-page-body">
      <div className="privacy-container">
        {/* Header */}
        <header className="privacy-header">
          <Link to="/" className="privacy-brand">
            <img src={presyohanLogo} alt="Presyohan Logo" className="privacy-logo" />
            <div>
              <h1 className="privacy-brand-title">Presyohan</h1>
              <p className="privacy-brand-subtitle">Smart Sari-Sari Store POS & Price Transparency</p>
            </div>
          </Link>
          <Link to="/" className="privacy-back-btn">
            <Icons.ArrowLeft />
            <span>Back to Home</span>
          </Link>
        </header>

        {/* Hero Card */}
        <div className="privacy-hero-card">
          <div className="privacy-badge">
            <Icons.Shield />
            <span>Privacy & Data Protection</span>
          </div>
          <h2 className="privacy-hero-title">Privacy Policy</h2>
          <p className="privacy-hero-meta">
            <strong>Effective Date:</strong> October 8, 2026 &bull; <strong>App:</strong> Presyohan (com.presyohan.app) &bull; <strong>Developer:</strong> SpennyWise
          </p>
        </div>

        {/* Section 1: Introduction & Overview */}
        <section className="privacy-content-card">
          <h3 className="privacy-section-title">
            <span className="privacy-section-icon"><Icons.Shield /></span>
            1. Introduction & Overview
          </h3>
          <p className="privacy-text">
            Welcome to <strong>Presyohan</strong> ("we," "our," or "us"), developed by <strong>SpennyWise</strong>. We are committed to protecting your privacy and ensuring you have a positive experience when using our mobile application (<strong>Presyohan Mobile</strong>) and web platform (<strong>presyohan.onrender.com</strong>).
          </p>
          <p className="privacy-text">
            This Privacy Policy explains what personal data we collect, how we process and protect it, your data deletion and access rights, and how we comply with applicable data protection laws, including the <strong>Philippine Data Privacy Act of 2012 (Republic Act No. 10173)</strong> and Google Play Data Safety requirements.
          </p>
        </section>

        {/* Section 2: Information We Collect */}
        <section className="privacy-content-card">
          <h3 className="privacy-section-title">
            <span className="privacy-section-icon"><Icons.Lock /></span>
            2. Information We Collect
          </h3>
          <p className="privacy-text">We collect the following categories of information to provide and improve our services:</p>
          <ul className="privacy-list">
            <li>
              <strong>Account & Profile Information:</strong> When you register or log in, we collect your email address, full name, profile avatar, role (Merchant or Customer), and unique authentication identifier (`auth.users.id`).
            </li>
            <li>
              <strong>Store & Inventory Data:</strong> For merchants, we store your store name, address/location, item catalogs, unit prices, barcodes, categories, and stock quantities.
            </li>
            <li>
              <strong>Suki & Customer Connections:</strong> Suki partner links, price search queries, and store pairing requests made by customers.
            </li>
            <li>
              <strong>Device & Diagnostic Data:</strong> App version, operating system version, and anonymous crash/error logs to maintain app stability.
            </li>
          </ul>
        </section>

        {/* Section 3: Camera & Device Permissions */}
        <section className="privacy-content-card">
          <h3 className="privacy-section-title">
            <span className="privacy-section-icon"><Icons.Camera /></span>
            3. Device Permissions & Camera Usage
          </h3>
          <p className="privacy-text">
            Presyohan requests access to your device's <strong>Camera</strong> (<code style={{ background: '#e2e8f0', padding: '2px 6px', borderRadius: '4px' }}>android.permission.CAMERA</code>) solely for the following user-initiated features:
          </p>
          <ul className="privacy-list">
            <li><strong>Barcode & QR Scanning:</strong> Scanning product barcodes via ML Kit to instantly look up or assign items.</li>
            <li><strong>AI Invoice & Receipt Digitization:</strong> Capturing photos of physical store price sheets or receipts for ephemeral text extraction.</li>
          </ul>
          <div className="privacy-highlight-box">
            <p>
              <strong>Important:</strong> Camera access is strictly active only while the scanner viewfinder or photo capture dialog is open. We never access your camera in the background or record video without your explicit interaction.
            </p>
          </div>
        </section>

        {/* Section 4: AI Processing & Third-Party Services */}
        <section className="privacy-content-card">
          <h3 className="privacy-section-title">
            <span className="privacy-section-icon"><Icons.Server /></span>
            4. Third-Party Services & AI Sub-Processors
          </h3>
          <p className="privacy-text">We partner with secure, reputable infrastructure providers to operate the application:</p>
          <ul className="privacy-list">
            <li>
              <strong>Supabase:</strong> Hosted backend database and authentication located in AWS Singapore (ap-southeast-1). All data is encrypted in transit via TLS 1.3 and at rest with AES-256.
            </li>
            <li>
              <strong>Google Gemini API:</strong> Used for ephemeral optical character recognition (OCR) and structured parsing of receipt photos. Images sent for AI parsing are processed in real-time and are <em>not retained</em> by Google to train foundation models.
            </li>
            <li>
              <strong>PayMongo:</strong> PCI-DSS Level 1 compliant payment processor handling web checkout transactions (GCash, Maya, Cards, QR Ph). Presyohan never stores or sees your raw credit card numbers or e-wallet PINs.
            </li>
          </ul>
        </section>

        {/* Section 5: Data Retention & Account Deletion Rights */}
        <section className="privacy-content-card">
          <h3 className="privacy-section-title">
            <span className="privacy-section-icon"><Icons.Trash /></span>
            5. Data Retention & Account Deletion Rights
          </h3>
          <p className="privacy-text">
            You retain full ownership of your data. In full compliance with Google Play User Data policies and RA 10173, you have the absolute right to delete your account and all associated personal data at any time:
          </p>
          <ul className="privacy-list">
            <li>
              <strong>In-App Deletion:</strong> Navigate to <em>Settings &rarr; Account Security &rarr; Delete Account</em> inside the Presyohan Mobile app.
            </li>
            <li>
              <strong>Web Deletion Portal:</strong> Visit our dedicated public deletion page at <Link to="/delete-account" style={{ color: 'var(--presyo-orange)', fontWeight: 700 }}>presyohan.onrender.com/delete-account</Link> without needing the mobile app installed.
            </li>
          </ul>
          <p className="privacy-text">
            Initiating account deletion permanently purges your credentials, profile, store memberships, suki connections, notifications, and activity logs from our active databases.
          </p>
        </section>

        {/* Section 6: Children's Privacy */}
        <section className="privacy-content-card">
          <h3 className="privacy-section-title">
            <span className="privacy-section-icon"><Icons.UserCheck /></span>
            6. Children's Privacy
          </h3>
          <p className="privacy-text">
            Presyohan is intended for store owners, merchants, and general retail shoppers. We do not knowingly collect or solicit personal information from children under the age of 13. If we learn that we have collected personal information from a child under 13, we will promptly delete that information.
          </p>
        </section>

        {/* Section 7: Quick Links & Contact */}
        <section className="privacy-content-card">
          <h3 className="privacy-section-title">
            <span className="privacy-section-icon"><Icons.Shield /></span>
            7. Contact Us & Related Resources
          </h3>
          <p className="privacy-text">
            If you have questions, privacy inquiries, or data requests, please contact our Data Protection team:
          </p>
          <p className="privacy-text">
            <strong>Email:</strong> <a href="mailto:support@presyohan.com" style={{ color: 'var(--presyo-teal)', fontWeight: 600 }}>support@presyohan.com</a> / <a href="mailto:spennywise.dev@gmail.com" style={{ color: 'var(--presyo-teal)', fontWeight: 600 }}>spennywise.dev@gmail.com</a><br />
            <strong>Location:</strong> Cebu, Philippines
          </p>

          <div className="privacy-links-grid">
            <Link to="/delete-account" className="privacy-link-card">
              <span>Account Deletion Request</span>
              <Icons.ChevronRight />
            </Link>
            <Link to="/contact" className="privacy-link-card">
              <span>Contact Support</span>
              <Icons.ChevronRight />
            </Link>
          </div>
        </section>

        {/* Footer */}
        <footer className="privacy-footer">
          <p>&copy; {new Date().getFullYear()} Presyohan by SpennyWise. All rights reserved.</p>
        </footer>
      </div>
    </div>
  );
}
