import React, { useState, useEffect } from 'react';
import { useSearchParams, useNavigate, Link } from 'react-router-dom';
import { supabase } from '../config/supabaseClient';
import { createCheckoutSession } from '../services/paymongoService';
import { mergeTierWithDefaults, isPromoActive, getPromoExpirationNotice } from '../components/admin/SubscriptionManagement';
import presyohanLogo from '../assets/ic_launcher.png';
import '../styles/SubscriptionCheckout.css';

// Clean vector SVG icons (Strictly zero emojis)
const Icons = {
  Lock: () => (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
      <path d="M7 11V7a5 5 0 0 1 10 0v4" />
    </svg>
  ),
  ArrowLeft: () => (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
      <line x1="19" y1="12" x2="5" y2="12" />
      <polyline points="12 19 5 12 12 5" />
    </svg>
  ),
  Check: () => (
    <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3.2" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="20 6 9 17 4 12" />
    </svg>
  ),
  Store: () => (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
      <polyline points="9 22 9 12 15 12 15 22" />
    </svg>
  ),
  Users: () => (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
      <circle cx="9" cy="7" r="4" />
      <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
      <path d="M16 3.13a4 4 0 0 1 0 7.75" />
    </svg>
  ),
  CreditCard: () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="1" y="4" width="22" height="16" rx="2" ry="2" />
      <line x1="1" y1="10" x2="23" y2="10" />
    </svg>
  ),
  Phone: () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="5" y="2" width="14" height="20" rx="2" ry="2" />
      <line x1="12" y1="18" x2="12.01" y2="18" />
    </svg>
  ),
  QrCode: () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="3" width="7" height="7" />
      <rect x="14" y="3" width="7" height="7" />
      <rect x="14" y="14" width="7" height="7" />
      <rect x="3" y="14" width="7" height="7" />
    </svg>
  ),
  CheckCircleBig: () => (
    <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
      <polyline points="22 4 12 14.01 9 11.01" />
    </svg>
  ),
  ExternalApp: () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
      <polyline points="15 3 21 3 21 9" />
      <line x1="10" y1="14" x2="21" y2="3" />
    </svg>
  )
};

const DEFAULT_TIERS = [
  {
    tier_id: 'pro',
    name: 'PRO Tier',
    price: 99,
    discount_percent: 0,
    promo_price: null,
    promo_badge: '7 DAYS TRIAL',
    promo_start_at: null,
    promo_end_at: null,
    promo_expiry_label: null,
    billing_period: 'month',
    trial_days: 7,
    max_stores: 10,
    max_items_per_store: 500,
    max_staff_per_store: 20,
    max_categories_per_store: 25,
    max_ai_parses_per_day: 15,
    max_photo_scans_per_day: 10,
    max_suki_partners: 15,
    max_presyohan_stores: 15,
    max_internet_searches_per_day: 15,
    description: 'Ideal for growing single & multi-branch retail stores.',
    merchant_benefits: [
      "Unlocked Customer Pairing",
      "Convert Price lists to PDF",
      "Convert Price lists to Excel",
      "Items per Store Limit: 500 Items",
      "Photo Scans Quota: 10 Scans a day",
      "Daily AI Parser Quota: 15 Parses a day",
      "Category per Store Limit: 25 Categories",
      "Owned Store Limit: Up to 10 Store Branches",
      "Member Limit per Store: 20 Staff Accounts",
      "Store Items Cloning",
      "Convert Price lists as Notes",
      "Import Prices via Excel"
    ],
    customer_benefits: [
      "Suking Tindahan Partners: 15 Partners",
      "Presyohan Store Limit: 15 Stores",
      "Internet Search Quota: 15 Searches / day",
      "Better Experience",
      "House and Lot",
      "Brand New Car"
    ]
  },
  {
    tier_id: 'vip',
    name: 'VIP Tier',
    price: 299,
    discount_percent: 0,
    promo_price: null,
    promo_badge: 'BEST VALUE',
    promo_start_at: null,
    promo_end_at: null,
    promo_expiry_label: null,
    billing_period: 'month',
    trial_days: 0,
    max_stores: 999999,
    max_items_per_store: 999999,
    max_staff_per_store: 999999,
    max_categories_per_store: 999999,
    max_ai_parses_per_day: 50,
    max_photo_scans_per_day: 50,
    max_suki_partners: 999999,
    max_presyohan_stores: 999999,
    max_internet_searches_per_day: 999999,
    description: 'Ideal for high-volume businesses & enterprise managers.',
    merchant_benefits: [
      "Unlimited Stores",
      "Unlimited Items per Store",
      "Unlimited Staff per Store",
      "Unlimited Categories per Store",
      "50 AI Parses / day",
      "50 Photo Scans / day",
      "Unlimited Customer Pairing (suki)",
      "Unlimited PDF & Excel Exports",
      "Unlimited Price Cloning & Export",
      "24/7 VIP Priority Support"
    ],
    customer_benefits: [
      "Unlimited Suking Tindahan Partners",
      "Unlimited Presyohan Stores",
      "Unlimited Internet Search Quota"
    ]
  }
];

export default function SubscriptionCheckout() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  // URL parameters
  const initialTier = searchParams.get('tier')?.toLowerCase() === 'vip' ? 'vip' : 'pro';
  const urlUid = searchParams.get('uid');
  const urlStatus = searchParams.get('status');

  // State
  const [selectedTierId, setSelectedTierId] = useState(initialTier);
  const [tiers, setTiers] = useState(DEFAULT_TIERS);
  const [loadingTiers, setLoadingTiers] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [paymentSuccess, setPaymentSuccess] = useState(urlStatus === 'success');
  const [paymentChannel, setPaymentChannel] = useState('gcash'); // 'gcash' | 'paymaya' | 'qrph' | 'card'

  // User form details
  const [userId, setUserId] = useState(urlUid || '');
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('09');
  const [errorMessage, setErrorMessage] = useState('');

  // 1. Fetch Dynamic Tiers & Authenticated User Context
  useEffect(() => {
    async function loadData() {
      setLoadingTiers(true);
      try {
        // A. Load subscription tiers from Supabase DB
        const { data: dbTiers, error: tierErr } = await supabase
          .from('subscription_tiers')
          .select('*')
          .in('tier_id', ['pro', 'vip']);

        if (!tierErr && dbTiers && dbTiers.length > 0) {
          const dbMap = new Map(dbTiers.map(t => [t.tier_id, t]));
          const merged = DEFAULT_TIERS.map(def => {
            const fromDb = dbMap.get(def.tier_id);
            return mergeTierWithDefaults(def, fromDb);
          });
          setTiers(merged);
        }

        // B. Load logged in user if available
        const { data: { session } } = await supabase.auth.getSession();
        const currentUid = urlUid || session?.user?.id;
        if (currentUid) {
          setUserId(currentUid);
          if (session?.user?.email) setEmail(session.user.email);

          const { data: profile } = await supabase
            .from('app_users')
            .select('full_name, email, phone, subscription_tier')
            .eq('id', currentUid)
            .maybeSingle();

          if (profile) {
            if (profile.full_name) setFullName(profile.full_name);
            if (profile.email) setEmail(profile.email);
            if (profile.phone) setPhone(profile.phone);
          }
        }
      } catch (err) {
        console.error('Error fetching subscription checkout context:', err);
      } finally {
        setLoadingTiers(false);
      }
    }

    loadData();
  }, [urlUid]);

  // 2. Selected Tier Computations (Price, Promo, Expiry)
  const activeTier = tiers.find(t => t.tier_id === selectedTierId) || tiers[0];
  const isVip = selectedTierId === 'vip';

  const basePrice = Number(activeTier.price || 0);
  const isPromo = isPromoActive(activeTier);
  const promoNotice = getPromoExpirationNotice(activeTier);

  let finalPrice = basePrice;
  let hasDiscount = false;
  let discountAmount = 0;

  if (isPromo) {
    if (activeTier.promo_price !== null && activeTier.promo_price !== undefined && activeTier.promo_price !== '') {
      finalPrice = Number(activeTier.promo_price);
      hasDiscount = finalPrice < basePrice;
      discountAmount = Math.max(0, basePrice - finalPrice);
    } else if (activeTier.discount_percent > 0) {
      discountAmount = (basePrice * Number(activeTier.discount_percent)) / 100;
      finalPrice = Math.max(0, basePrice - discountAmount);
      hasDiscount = true;
    }
  }

  // Helper to compute prices for switcher tabs
  const getTabPrice = (tierObj) => {
    const p = Number(tierObj.price || 0);
    if (!isPromoActive(tierObj)) return `₱${p.toFixed(0)}`;
    if (tierObj.promo_price !== null && tierObj.promo_price !== undefined && tierObj.promo_price !== '') {
      return `₱${Number(tierObj.promo_price).toFixed(0)}`;
    }
    if (tierObj.discount_percent > 0) {
      return `₱${Math.max(0, p * (1 - tierObj.discount_percent / 100)).toFixed(0)}`;
    }
    return `₱${p.toFixed(0)}`;
  };

  // 3. Handle PayMongo Checkout Initiation
  const handleProceedCheckout = async (e) => {
    e.preventDefault();
    setErrorMessage('');

    if (!email.trim()) {
      setErrorMessage('Please provide a valid email for your invoice receipt.');
      return;
    }

    setSubmitting(true);
    try {
      const response = await createCheckoutSession({
        userId: userId || null,
        tierId: selectedTierId,
        amount: finalPrice,
        planName: activeTier.name,
        description: `Presyohan ${activeTier.name} (1 Month) - Full Access Unlocked`,
        customerName: fullName.trim() || 'Presyohan User',
        customerEmail: email.trim(),
        customerPhone: phone.trim()
      });

      if (response?.checkoutUrl) {
        window.location.href = response.checkoutUrl;
      } else {
        throw new Error('Unable to generate checkout URL. Please try again.');
      }
    } catch (err) {
      console.error('Checkout error:', err);
      setErrorMessage(err.message || 'Payment initiation failed. Please try again.');
      setSubmitting(false);
    }
  };

  // 4. Render Success Screen
  if (paymentSuccess) {
    return (
      <div className="neu-checkout-body">
        <div className="neu-container">
          <div className="neu-success-card">
            <div className="neu-success-icon-disc">
              <Icons.CheckCircleBig />
            </div>
            <h1 className="neu-success-heading">Payment Confirmed</h1>
            <p className="neu-success-text">
              Your Presyohan <strong>{activeTier.name}</strong> subscription has been successfully activated.
              Your account limits and features are now unlocked for the next 30 days.
            </p>

            <div className="neu-receipt-box">
              <div className="neu-receipt-row">
                <span>Plan Upgraded:</span>
                <strong>{activeTier.name}</strong>
              </div>
              <div className="neu-receipt-row">
                <span>Billing Period:</span>
                <span>1 Month Access</span>
              </div>
              <div className="neu-receipt-row">
                <span>Payment Channel:</span>
                <span>PayMongo (GCash / Maya / Card / QR Ph)</span>
              </div>
              <div className="neu-receipt-row">
                <span>Status:</span>
                <span style={{ color: 'var(--accent-emerald)', fontWeight: 700 }}>Active / Verified</span>
              </div>
              <div className="neu-receipt-row">
                <span>Total Amount Paid:</span>
                <span>₱{finalPrice.toFixed(2)}</span>
              </div>
            </div>

            <a href="presyohan://subscription/success" className="neu-btn-app-return">
              <Icons.ExternalApp />
              <span>RETURN TO PRESYOHAN APP</span>
            </a>

            <div>
              <Link to="/stores" className="neu-btn-web-home">
                Continue to Web Dashboard
              </Link>
            </div>
          </div>
        </div>
      </div>
    );
  }

  // 5. Main Screen: Requested Tier Card Design + Compact Unified Right Console
  return (
    <div className="neu-checkout-body">
      <div className="neu-container">
        {/* Top Navbar: Brand on Left in Balsamiq Sans, Plan Switcher on Right */}
        <header className="neu-nav-header">
          <div className="neu-nav-left">
            <button type="button" onClick={() => navigate(-1)} className="neu-btn-back">
              <Icons.ArrowLeft />
              <span>Back</span>
            </button>

            <Link to="/" className="neu-brand-lockup">
              <img src={presyohanLogo} alt="Presyohan Logo" className="neu-brand-logo-img" />
              <div className="neu-brand-text-col">
                <span className="brand-word-atong">atong</span>
                <span className="brand-word-main">
                  <span className="brand-word-presyo">presyo</span>
                  <span className="brand-word-han">han?</span>
                </span>
              </div>
            </Link>
          </div>

          {/* Plan Switcher Tabs */}
          <div className="neu-plan-switcher-top">
            {tiers.map((tier) => {
              const isSelected = tier.tier_id === selectedTierId;
              const tabPrice = getTabPrice(tier);
              const isVipTab = tier.tier_id === 'vip';
              return (
                <button
                  key={tier.tier_id}
                  type="button"
                  onClick={() => setSelectedTierId(tier.tier_id)}
                  className={`neu-tab-btn ${isSelected ? (isVipTab ? 'active-vip' : 'active-pro') : ''}`}
                >
                  <span>{tier.name}</span>
                  <span className="neu-tab-price-badge">{tabPrice}/mo</span>
                </button>
              );
            })}
          </div>
        </header>

        {/* Main 2-Column Grid Layout */}
        <div className="neu-grid-layout">
          {/* Left Column: Requested Tier Card with Outline Border, Capacity Box & Categorized Feature Lists */}
          <div className={`neu-tier-card ${isVip ? 'vip-card' : ''}`}>
            {/* Header Lockup */}
            <div className="neu-tier-header">
              <div>
                <h3 className="neu-tier-title">{activeTier.name}</h3>
                <div className="neu-tier-pricing-row">
                  <span className="neu-tier-final-price">₱{finalPrice.toFixed(0)}</span>
                  {hasDiscount && (
                    <span className="neu-tier-orig-price">₱{basePrice.toFixed(0)}</span>
                  )}
                  <span className="neu-tier-period">/ {activeTier.billing_period || 'month'}</span>
                </div>
              </div>

              <div className="neu-tier-badge-pill">
                {activeTier.promo_badge || (hasDiscount ? `${activeTier.discount_percent}% OFF` : 'STANDARD PLAN')}
              </div>
            </div>

            {/* 2x2 Capacity Caps & Limits Box */}
            <div className="neu-capacity-box">
              <div className="neu-capacity-label">CAPACITY CAPS &amp; LIMITS</div>
              <div className="neu-capacity-grid">
                <div className="neu-capacity-cell">
                  <div className="neu-capacity-name">STORES</div>
                  <div className="neu-capacity-val">
                    {activeTier.max_stores > 900 ? 'Unlimited ∞' : activeTier.max_stores}
                  </div>
                </div>
                <div className="neu-capacity-cell">
                  <div className="neu-capacity-name">ITEMS / STORE</div>
                  <div className="neu-capacity-val">
                    {activeTier.max_items_per_store > 9000 ? 'Unlimited ∞' : activeTier.max_items_per_store}
                  </div>
                </div>
                <div className="neu-capacity-cell">
                  <div className="neu-capacity-name">STAFF / STORE</div>
                  <div className="neu-capacity-val">
                    {activeTier.max_staff_per_store > 900 ? 'Unlimited ∞' : activeTier.max_staff_per_store}
                  </div>
                </div>
                <div className="neu-capacity-cell">
                  <div className="neu-capacity-name">DAILY AI PARSES</div>
                  <div className="neu-capacity-val">
                    {activeTier.max_ai_parses_per_day} / day
                  </div>
                </div>
              </div>
            </div>

            {/* Store & Merchant Features */}
            <div className="neu-feature-section-header merchant-header">
              <Icons.Store />
              <span>STORE &amp; MERCHANT FEATURES ({(activeTier.merchant_benefits || []).length})</span>
            </div>
            <div className="neu-benefit-list">
              {(activeTier.merchant_benefits || []).map((benefit, index) => {
                const cleanText = benefit.replace(/^[^a-zA-Z0-9]+/, '').trim();
                return (
                  <div key={`m-${index}`} className="neu-benefit-row">
                    <div className={`neu-check-dot ${isVip ? 'vip-dot' : 'pro-dot'}`}>
                      <Icons.Check />
                    </div>
                    <span>{cleanText}</span>
                  </div>
                );
              })}
            </div>

            {/* Customer & Suki Partner Features */}
            <div className="neu-feature-section-header customer-header">
              <Icons.Users />
              <span>CUSTOMER &amp; SUKI PARTNER FEATURES ({(activeTier.customer_benefits || []).length})</span>
            </div>
            <div className="neu-benefit-list">
              {(activeTier.customer_benefits || []).map((benefit, index) => {
                const cleanText = benefit.replace(/^[^a-zA-Z0-9]+/, '').trim();
                return (
                  <div key={`c-${index}`} className="neu-benefit-row">
                    <div className={`neu-check-dot ${isVip ? 'vip-dot' : 'pro-dot'}`}>
                      <Icons.Check />
                    </div>
                    <span>{cleanText}</span>
                  </div>
                );
              })}
            </div>

            {/* Promo Expiration Notice Bar */}
            {isPromo && (
              <div className="neu-promo-notice-box">
                <span>{promoNotice || 'Special offer valid for a limited time'}</span>
                <span className="neu-promo-notice-pct">
                  {activeTier.discount_percent > 0 ? `${activeTier.discount_percent}% OFF` : (activeTier.promo_badge || 'SPECIAL')}
                </span>
              </div>
            )}
          </div>

          {/* Right Column: Unified Checkout Console Card */}
          <div className="neu-checkout-console-card">
            {/* Contact Details */}
            <div className="neu-section-label">Billing Information</div>
            <div className="neu-form-grid">
              <input
                type="text"
                className="neu-input full-span"
                placeholder="Full Name / Merchant Name"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                required
              />
              <input
                type="email"
                className="neu-input"
                placeholder="Email Address"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
              <input
                type="tel"
                className="neu-input"
                placeholder="Mobile (09xxxxxxxxx)"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
              />
            </div>

            {/* Philippine Payment Method */}
            <div className="neu-section-label">Select Philippine Payment Channel</div>
            <div className="neu-channel-grid">
              <button
                type="button"
                className={`neu-channel-btn ${paymentChannel === 'gcash' ? 'selected' : ''}`}
                onClick={() => setPaymentChannel('gcash')}
              >
                <Icons.Phone />
                <span className="neu-channel-name">GCash</span>
                <span className="neu-channel-sub">E-Wallet</span>
              </button>

              <button
                type="button"
                className={`neu-channel-btn ${paymentChannel === 'paymaya' ? 'selected' : ''}`}
                onClick={() => setPaymentChannel('paymaya')}
              >
                <Icons.Phone />
                <span className="neu-channel-name">Maya</span>
                <span className="neu-channel-sub">E-Wallet</span>
              </button>

              <button
                type="button"
                className={`neu-channel-btn ${paymentChannel === 'qrph' ? 'selected' : ''}`}
                onClick={() => setPaymentChannel('qrph')}
              >
                <Icons.QrCode />
                <span className="neu-channel-name">QR Ph</span>
                <span className="neu-channel-sub">Instant QR</span>
              </button>

              <button
                type="button"
                className={`neu-channel-btn ${paymentChannel === 'card' ? 'selected' : ''}`}
                onClick={() => setPaymentChannel('card')}
              >
                <Icons.CreditCard />
                <span className="neu-channel-name">Cards</span>
                <span className="neu-channel-sub">Visa/Master</span>
              </button>
            </div>

            {/* Order Summary Line Items */}
            <div className="neu-summary-box">
              <div className="neu-summary-line">
                <span>{activeTier.name} (1 Month)</span>
                <span>₱{basePrice.toFixed(2)}</span>
              </div>

              {hasDiscount && (
                <div className="neu-summary-line discount-line">
                  <span>Promotional Savings</span>
                  <span>-₱{discountAmount.toFixed(2)}</span>
                </div>
              )}

              <div className="neu-summary-line">
                <span>Processing &amp; VAT</span>
                <span>₱0.00 (Included)</span>
              </div>

              <div className="neu-summary-divider" />

              <div className="neu-total-line">
                <span className="neu-total-title">Total Due:</span>
                <div className={`neu-total-val ${isVip ? 'vip-total' : ''}`}>
                  <span>₱{finalPrice.toFixed(2)}</span>
                  <span className="mo-label">/mo</span>
                </div>
              </div>
            </div>

            {errorMessage && (
              <div style={{
                padding: '8px 12px',
                backgroundColor: '#fef2f2',
                borderLeft: '3px solid #ef4444',
                borderRadius: '8px',
                color: '#b91c1c',
                fontSize: '12px',
                marginBottom: '10px'
              }}>
                {errorMessage}
              </div>
            )}

            {/* Primary Action Button */}
            <button
              type="button"
              onClick={handleProceedCheckout}
              disabled={submitting}
              className={`neu-btn-pay ${isVip ? 'vip-btn' : ''}`}
            >
              <Icons.Lock />
              <span>{submitting ? 'Connecting to PayMongo...' : `PAY ₱${finalPrice.toFixed(2)} VIA PAYMONGO`}</span>
            </button>

            <div className="neu-trust-footer">
              <span>Secured by</span>
              <strong style={{ color: 'var(--slate-800)', letterSpacing: '0.3px' }}>PayMongo Philippines</strong>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
