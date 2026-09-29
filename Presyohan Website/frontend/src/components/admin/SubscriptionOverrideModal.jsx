import React, { useState, useEffect } from 'react';
import { supabase } from '../../config/supabaseClient';

export default function SubscriptionOverrideModal({ user, isOpen, onClose, onSuccess }) {
  const [selectedTier, setSelectedTier] = useState('pro');
  const [durationPreset, setDurationPreset] = useState('7days');
  const [customDateTime, setCustomDateTime] = useState('');
  const [adminNote, setAdminNote] = useState('');
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  useEffect(() => {
    if (user) {
      const currentTier = (user.subscription_tier || 'free').toLowerCase();
      setSelectedTier(currentTier);

      if (user.subscription_expires_at) {
        const expDate = new Date(user.subscription_expires_at);
        const now = new Date();
        const diffDays = Math.ceil((expDate - now) / (1000 * 60 * 60 * 24));

        if (diffDays === 7) setDurationPreset('7days');
        else if (diffDays === 14) setDurationPreset('14days');
        else if (diffDays === 30) setDurationPreset('30days');
        else if (diffDays === 90) setDurationPreset('90days');
        else if (diffDays === 365) setDurationPreset('1year');
        else {
          setDurationPreset('custom');
          // Format to YYYY-MM-DDTHH:mm for datetime-local
          const pad = (n) => (n < 10 ? '0' + n : n);
          const formatted = `${expDate.getFullYear()}-${pad(expDate.getMonth() + 1)}-${pad(expDate.getDate())}T${pad(expDate.getHours())}:${pad(expDate.getMinutes())}`;
          setCustomDateTime(formatted);
        }
      } else {
        // No expiration: either lifetime or free
        if (currentTier === 'free') {
          setDurationPreset('7days');
        } else {
          setDurationPreset('lifetime');
        }
      }
      setErrorMessage('');
    }
  }, [user]);

  if (!isOpen || !user) return null;

  // Compute calculated expiration date based on durationPreset
  const getCalculatedExpiration = () => {
    if (selectedTier === 'free') return null;
    if (durationPreset === 'lifetime') return null;

    const now = new Date();
    if (durationPreset === '7days') {
      const d = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000);
      return d.toISOString();
    }
    if (durationPreset === '14days') {
      const d = new Date(now.getTime() + 14 * 24 * 60 * 60 * 1000);
      return d.toISOString();
    }
    if (durationPreset === '30days') {
      const d = new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000);
      return d.toISOString();
    }
    if (durationPreset === '90days') {
      const d = new Date(now.getTime() + 90 * 24 * 60 * 60 * 1000);
      return d.toISOString();
    }
    if (durationPreset === '1year') {
      const d = new Date(now.getTime() + 365 * 24 * 60 * 60 * 1000);
      return d.toISOString();
    }
    if (durationPreset === 'custom') {
      if (!customDateTime) return null;
      const d = new Date(customDateTime);
      return isNaN(d.getTime()) ? null : d.toISOString();
    }
    return null;
  };

  const handleApplyOverride = async () => {
    setLoading(true);
    setErrorMessage('');

    try {
      const calculatedExpiry = getCalculatedExpiration();

      if (selectedTier !== 'free' && durationPreset === 'custom' && !calculatedExpiry) {
        throw new Error('Please select a valid custom expiration date and time.');
      }

      // Try RPC first for atomic update of app_users and stores
      let rpcSuccess = false;
      try {
        const { error: rpcError } = await supabase.rpc('override_subscription_tier', {
          target_user_id: user.id,
          new_tier: selectedTier,
          expires_at: calculatedExpiry
        });
        if (!rpcError) {
          rpcSuccess = true;
        } else {
          console.warn('RPC override failed, falling back to direct table updates:', rpcError.message);
        }
      } catch (rpcErr) {
        console.warn('RPC execution exception, falling back to direct table updates:', rpcErr);
      }

      // If RPC was not used or failed, update tables directly
      if (!rpcSuccess) {
        const { error: userUpdateError } = await supabase
          .from('app_users')
          .update({
            subscription_tier: selectedTier,
            subscription_expires_at: calculatedExpiry
          })
          .eq('id', user.id);

        if (userUpdateError) throw userUpdateError;

        // Synchronize stores owned by this user
        const { data: ownedStoreRows } = await supabase
          .from('store_members')
          .select('store_id')
          .eq('user_id', user.id);

        const storeIds = (ownedStoreRows || []).map(r => r.store_id);

        if (storeIds.length > 0) {
          const { error: storeUpdateError } = await supabase
            .from('stores')
            .update({
              subscription_tier: selectedTier,
              subscription_expires_at: calculatedExpiry,
              updated_at: new Date().toISOString()
            })
            .in('id', storeIds);

          if (storeUpdateError) {
            console.warn('Note: Could not update stores table:', storeUpdateError.message);
          }
        }
      }

      const updatedUser = {
        ...user,
        subscription_tier: selectedTier,
        subscription_expires_at: calculatedExpiry
      };

      if (onSuccess) {
        onSuccess(updatedUser);
      }
      onClose();
    } catch (err) {
      console.error('Failed to override subscription:', err);
      setErrorMessage(err.message || 'An error occurred while saving the subscription override.');
    } finally {
      setLoading(false);
    }
  };

  const calculatedExpiry = getCalculatedExpiration();
  const currentTier = (user.subscription_tier || 'free').toLowerCase();

  const formatDateTimeDisplay = (isoString) => {
    if (!isoString) return 'Lifetime Access (Never Expires)';
    const date = new Date(isoString);
    return date.toLocaleString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
      hour12: true
    });
  };

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.65)',
        backdropFilter: 'blur(8px)',
        WebkitBackdropFilter: 'blur(8px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 9999,
        padding: '20px'
      }}
      onClick={(e) => {
        if (e.target === e.currentTarget && !loading) onClose();
      }}
    >
      <div
        style={{
          backgroundColor: '#FFFFFF',
          borderRadius: '24px',
          width: '100%',
          maxWidth: '640px',
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
          border: '1px solid rgba(226, 232, 240, 0.8)',
          overflow: 'hidden',
          display: 'flex',
          flexDirection: 'column',
          maxHeight: '90vh'
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Modal Header */}
        <div
          style={{
            padding: '24px 28px',
            borderBottom: '1px solid #F1F5F9',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            background: 'linear-gradient(180deg, #FFFFFF 0%, #FAFBFD 100%)'
          }}
        >
          <div>
            <h3
              style={{
                margin: 0,
                fontSize: '1.25rem',
                fontWeight: 800,
                color: '#0F172A',
                letterSpacing: '-0.3px',
                display: 'flex',
                alignItems: 'center',
                gap: '8px'
              }}
            >
              <svg
                width="22"
                height="22"
                viewBox="0 0 24 24"
                fill="none"
                stroke="#FF8C00"
                strokeWidth="2.2"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z" />
              </svg>
              Subscription Override
            </h3>
            <p style={{ margin: '4px 0 0', fontSize: '0.85rem', color: '#64748B' }}>
              Manually configure subscription tier and access period for this account.
            </p>
          </div>

          <button
            type="button"
            onClick={onClose}
            disabled={loading}
            style={{
              background: '#F8FAFC',
              border: '1px solid #E2E8F0',
              color: '#64748B',
              width: '36px',
              height: '36px',
              borderRadius: '12px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              cursor: 'pointer',
              transition: 'all 0.2s ease'
            }}
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>

        {/* Modal Scrollable Body */}
        <div style={{ padding: '24px 28px', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '22px' }}>
          {/* Target User Info Card */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              backgroundColor: '#F8FAFC',
              border: '1px solid #E2E8F0',
              borderRadius: '16px',
              padding: '14px 18px'
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <div
                style={{
                  width: '42px',
                  height: '42px',
                  borderRadius: '12px',
                  backgroundColor: 'rgba(255, 140, 0, 0.1)',
                  color: '#FF8C00',
                  fontWeight: 800,
                  fontSize: '0.95rem',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  backgroundImage: user.avatar_url ? `url(${user.avatar_url})` : 'none',
                  backgroundSize: 'cover',
                  backgroundPosition: 'center'
                }}
              >
                {!user.avatar_url && (user.name || user.email || 'U').slice(0, 2).toUpperCase()}
              </div>
              <div>
                <div style={{ fontWeight: 700, color: '#0F172A', fontSize: '0.95rem' }}>
                  {user.name || 'Anonymous User'}
                </div>
                <div style={{ fontSize: '0.8rem', color: '#64748B' }}>{user.email}</div>
              </div>
            </div>

            <div style={{ textAlign: 'right' }}>
              <div style={{ fontSize: '0.72rem', color: '#94A3B8', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                Current Status
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginTop: '2px' }}>
                <span
                  style={{
                    padding: '3px 10px',
                    borderRadius: '20px',
                    fontSize: '0.75rem',
                    fontWeight: 800,
                    textTransform: 'uppercase',
                    backgroundColor:
                      currentTier === 'vip'
                        ? 'rgba(255, 140, 0, 0.12)'
                        : currentTier === 'pro'
                        ? 'rgba(0, 188, 212, 0.12)'
                        : 'rgba(100, 116, 139, 0.1)',
                    color:
                      currentTier === 'vip'
                        ? '#EA580C'
                        : currentTier === 'pro'
                        ? '#0891B2'
                        : '#475569'
                  }}
                >
                  {currentTier.toUpperCase()}
                </span>
              </div>
            </div>
          </div>

          {/* Section 1: Select Subscription Tier */}
          <div>
            <label
              style={{
                display: 'block',
                fontSize: '0.82rem',
                fontWeight: 700,
                color: '#475569',
                textTransform: 'uppercase',
                letterSpacing: '0.5px',
                marginBottom: '10px'
              }}
            >
              1. Select Subscription Tier
            </label>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '12px' }}>
              {/* Free Tier Card */}
              <div
                onClick={() => setSelectedTier('free')}
                style={{
                  border: selectedTier === 'free' ? '2px solid #64748B' : '1px solid #E2E8F0',
                  borderRadius: '16px',
                  padding: '16px 14px',
                  backgroundColor: selectedTier === 'free' ? '#F8FAFC' : '#FFFFFF',
                  cursor: 'pointer',
                  transition: 'all 0.2s ease',
                  boxShadow: selectedTier === 'free' ? '0 4px 14px rgba(100, 116, 139, 0.12)' : 'none',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '6px'
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <span style={{ fontWeight: 800, fontSize: '0.95rem', color: '#334155' }}>Free</span>
                  <div
                    style={{
                      width: '18px',
                      height: '18px',
                      borderRadius: '50%',
                      border: selectedTier === 'free' ? '5px solid #64748B' : '1.5px solid #CBD5E1',
                      backgroundColor: '#FFFFFF'
                    }}
                  />
                </div>
                <div style={{ fontSize: '0.78rem', color: '#64748B', lineHeight: '1.4' }}>
                  Standard tier (1 store, 100 items, default limits).
                </div>
              </div>

              {/* PRO Tier Card */}
              <div
                onClick={() => setSelectedTier('pro')}
                style={{
                  border: selectedTier === 'pro' ? '2px solid #00BCD4' : '1px solid #E2E8F0',
                  borderRadius: '16px',
                  padding: '16px 14px',
                  backgroundColor: selectedTier === 'pro' ? 'rgba(0, 188, 212, 0.04)' : '#FFFFFF',
                  cursor: 'pointer',
                  transition: 'all 0.2s ease',
                  boxShadow: selectedTier === 'pro' ? '0 4px 14px rgba(0, 188, 212, 0.15)' : 'none',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '6px'
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <span style={{ fontWeight: 800, fontSize: '0.95rem', color: '#0891B2' }}>PRO Tier</span>
                  <div
                    style={{
                      width: '18px',
                      height: '18px',
                      borderRadius: '50%',
                      border: selectedTier === 'pro' ? '5px solid #00BCD4' : '1.5px solid #CBD5E1',
                      backgroundColor: '#FFFFFF'
                    }}
                  />
                </div>
                <div style={{ fontSize: '0.78rem', color: '#64748B', lineHeight: '1.4' }}>
                  10 stores, 500 items, cloning, Excel &amp; PDF exports.
                </div>
              </div>

              {/* VIP Tier Card */}
              <div
                onClick={() => setSelectedTier('vip')}
                style={{
                  border: selectedTier === 'vip' ? '2px solid #FF8C00' : '1px solid #E2E8F0',
                  borderRadius: '16px',
                  padding: '16px 14px',
                  backgroundColor: selectedTier === 'vip' ? 'rgba(255, 140, 0, 0.05)' : '#FFFFFF',
                  cursor: 'pointer',
                  transition: 'all 0.2s ease',
                  boxShadow: selectedTier === 'vip' ? '0 4px 14px rgba(255, 140, 0, 0.18)' : 'none',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '6px'
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <span style={{ fontWeight: 800, fontSize: '0.95rem', color: '#EA580C' }}>VIP Tier</span>
                  <div
                    style={{
                      width: '18px',
                      height: '18px',
                      borderRadius: '50%',
                      border: selectedTier === 'vip' ? '5px solid #FF8C00' : '1.5px solid #CBD5E1',
                      backgroundColor: '#FFFFFF'
                    }}
                  />
                </div>
                <div style={{ fontSize: '0.78rem', color: '#64748B', lineHeight: '1.4' }}>
                  Unlimited stores &amp; items, 50 AI scans/day, VIP priority.
                </div>
              </div>
            </div>
          </div>

          {/* Section 2: Access Duration & Expiration */}
          {selectedTier !== 'free' ? (
            <div>
              <label
                style={{
                  display: 'block',
                  fontSize: '0.82rem',
                  fontWeight: 700,
                  color: '#475569',
                  textTransform: 'uppercase',
                  letterSpacing: '0.5px',
                  marginBottom: '10px'
                }}
              >
                2. Select Access Duration
              </label>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '10px', marginBottom: '12px' }}>
                {[
                  { id: '7days', label: '7 Days Access', desc: 'Trial / Short-term promo' },
                  { id: '14days', label: '14 Days Access', desc: '2-week extended pass' },
                  { id: '30days', label: '30 Days Access', desc: '1 month subscription' },
                  { id: '90days', label: '90 Days Access', desc: 'Quarterly access' },
                  { id: '1year', label: '1 Year Access', desc: 'Annual access' },
                  { id: 'lifetime', label: 'Lifetime Access', desc: 'Permanent / No expiry' }
                ].map((preset) => (
                  <button
                    key={preset.id}
                    type="button"
                    onClick={() => setDurationPreset(preset.id)}
                    style={{
                      padding: '12px 14px',
                      borderRadius: '12px',
                      border:
                        durationPreset === preset.id
                          ? '2px solid #FF8C00'
                          : '1px solid #E2E8F0',
                      backgroundColor:
                        durationPreset === preset.id
                          ? 'rgba(255, 140, 0, 0.06)'
                          : '#FFFFFF',
                      color: durationPreset === preset.id ? '#C2410C' : '#334155',
                      fontWeight: 700,
                      fontSize: '0.85rem',
                      textAlign: 'left',
                      cursor: 'pointer',
                      transition: 'all 0.15s ease',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '3px'
                    }}
                  >
                    <span>{preset.label}</span>
                    <span style={{ fontSize: '0.72rem', color: '#64748B', fontWeight: 500 }}>
                      {preset.desc}
                    </span>
                  </button>
                ))}
              </div>

              {/* Custom Date Option Toggle */}
              <div style={{ marginTop: '8px' }}>
                <button
                  type="button"
                  onClick={() => setDurationPreset('custom')}
                  style={{
                    padding: '8px 16px',
                    borderRadius: '10px',
                    border:
                      durationPreset === 'custom'
                        ? '1.5px solid #FF8C00'
                        : '1px dashed #CBD5E1',
                    backgroundColor:
                      durationPreset === 'custom'
                        ? 'rgba(255, 140, 0, 0.05)'
                        : '#FAFBFD',
                    color: durationPreset === 'custom' ? '#C2410C' : '#64748B',
                    fontWeight: 700,
                    fontSize: '0.82rem',
                    cursor: 'pointer',
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '6px'
                  }}
                >
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                    <rect x="3" y="4" width="18" height="18" rx="2" ry="2" />
                    <line x1="16" y1="2" x2="16" y2="6" />
                    <line x1="8" y1="2" x2="8" y2="6" />
                    <line x1="3" y1="10" x2="21" y2="10" />
                  </svg>
                  Custom Expiration Date &amp; Time
                </button>

                {durationPreset === 'custom' && (
                  <div style={{ marginTop: '10px', display: 'flex', gap: '10px', alignItems: 'center' }}>
                    <input
                      type="datetime-local"
                      className="admin-search-input"
                      value={customDateTime}
                      onChange={(e) => setCustomDateTime(e.target.value)}
                      style={{
                        padding: '10px 14px',
                        fontSize: '0.88rem',
                        fontWeight: 600,
                        maxWidth: '280px',
                        border: '1.5px solid #FF8C00'
                      }}
                    />
                    <span style={{ fontSize: '0.78rem', color: '#64748B' }}>
                      Exact expiration timestamp in local timezone.
                    </span>
                  </div>
                )}
              </div>
            </div>
          ) : (
            <div
              style={{
                backgroundColor: '#F8FAFC',
                border: '1px dashed #CBD5E1',
                borderRadius: '14px',
                padding: '14px 18px',
                color: '#64748B',
                fontSize: '0.85rem'
              }}
            >
              Free tier does not have an expiration date. User will remain on standard free quota limits indefinitely.
            </div>
          )}

          {/* Section 3: Summary of Changes */}
          <div
            style={{
              backgroundColor: '#FAFBFD',
              border: '1px solid #E2E8F0',
              borderRadius: '16px',
              padding: '16px 20px',
              display: 'flex',
              flexDirection: 'column',
              gap: '10px'
            }}
          >
            <div style={{ fontSize: '0.78rem', fontWeight: 800, color: '#475569', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
              Override Summary
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '120px 1fr', gap: '8px', fontSize: '0.88rem' }}>
              <span style={{ color: '#64748B', fontWeight: 600 }}>Applied Tier:</span>
              <span style={{ fontWeight: 800, color: selectedTier === 'vip' ? '#EA580C' : selectedTier === 'pro' ? '#0891B2' : '#334155' }}>
                {selectedTier === 'vip' ? 'VIP Tier (Full Unlimited Access)' : selectedTier === 'pro' ? 'PRO Tier (Enhanced Limits & Exports)' : 'Free Tier (Default Limits)'}
              </span>

              <span style={{ color: '#64748B', fontWeight: 600 }}>Access Validity:</span>
              <span style={{ fontWeight: 700, color: '#0F172A' }}>
                {selectedTier === 'free'
                  ? 'Standard Ongoing Access'
                  : formatDateTimeDisplay(calculatedExpiry)}
              </span>

              <span style={{ color: '#64748B', fontWeight: 600 }}>Scope:</span>
              <span style={{ color: '#475569', fontSize: '0.82rem' }}>
                Account &amp; all merchant store branches linked to this user.
              </span>
            </div>
          </div>

          {errorMessage && (
            <div
              style={{
                backgroundColor: '#FEF2F2',
                border: '1px solid #FCA5A5',
                color: '#DC2626',
                padding: '12px 16px',
                borderRadius: '12px',
                fontSize: '0.85rem',
                fontWeight: 600
              }}
            >
              {errorMessage}
            </div>
          )}
        </div>

        {/* Modal Footer Actions */}
        <div
          style={{
            padding: '18px 28px',
            borderTop: '1px solid #F1F5F9',
            backgroundColor: '#FFFFFF',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center'
          }}
        >
          <div>
            {currentTier !== 'free' && selectedTier !== 'free' && (
              <button
                type="button"
                onClick={() => {
                  setSelectedTier('free');
                  setDurationPreset('lifetime');
                }}
                disabled={loading}
                style={{
                  background: 'none',
                  border: 'none',
                  color: '#DC2626',
                  fontSize: '0.82rem',
                  fontWeight: 700,
                  cursor: 'pointer',
                  padding: '6px 0',
                  textDecoration: 'underline'
                }}
              >
                Reset to Free Tier
              </button>
            )}
          </div>

          <div style={{ display: 'flex', gap: '12px' }}>
            <button
              type="button"
              onClick={onClose}
              disabled={loading}
              style={{
                padding: '10px 20px',
                borderRadius: '12px',
                border: '1px solid #CBD5E1',
                backgroundColor: '#FFFFFF',
                color: '#475569',
                fontWeight: 700,
                fontSize: '0.9rem',
                cursor: 'pointer',
                transition: 'all 0.15s ease'
              }}
            >
              Cancel
            </button>

            <button
              type="button"
              onClick={handleApplyOverride}
              disabled={loading}
              style={{
                padding: '10px 24px',
                borderRadius: '12px',
                border: 'none',
                background: 'linear-gradient(135deg, #FFB800 0%, #FF8C00 100%)',
                color: '#FFFFFF',
                fontWeight: 800,
                fontSize: '0.9rem',
                cursor: loading ? 'not-allowed' : 'pointer',
                boxShadow: '0 4px 12px rgba(255, 140, 0, 0.25)',
                transition: 'all 0.15s ease',
                display: 'flex',
                alignItems: 'center',
                gap: '8px'
              }}
            >
              {loading ? (
                <>
                  <span style={{ display: 'inline-block', width: '14px', height: '14px', border: '2px solid #FFFFFF', borderTopColor: 'transparent', borderRadius: '50%', animation: 'spin 0.8s linear infinite' }} />
                  Saving...
                </>
              ) : (
                'Save & Apply Override'
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
