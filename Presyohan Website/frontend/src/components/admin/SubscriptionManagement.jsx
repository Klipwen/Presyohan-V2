import React, { useState, useEffect } from 'react';
import { supabase } from '../../config/supabaseClient';

export default function SubscriptionManagement() {
  const [subTab, setSubTab] = useState('config'); // 'config' | 'overrides' | 'usage'
  const [tiers, setTiers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [editingTier, setEditingTier] = useState(null);

  // Manual Override State
  const [searchQuery, setSearchQuery] = useState('');
  const [tierFilter, setTierFilter] = useState('all'); // 'all' | 'free' | 'pro' | 'vip'
  const [users, setUsers] = useState([]);
  const [stores, setStores] = useState([]);
  const [selectedEntity, setSelectedEntity] = useState(null); // { type: 'user' | 'store', item: obj }
  const [overrideTier, setOverrideTier] = useState('pro');
  const [overrideDuration, setOverrideDuration] = useState('30'); // '7' | '30' | '90' | '365' | 'permanent'
  const [mutating, setMutating] = useState(false);

  // New Benefit Item Inputs in Edit Modal
  const [newMerchantBenefit, setNewMerchantBenefit] = useState('');
  const [newCustomerBenefit, setNewCustomerBenefit] = useState('');

  useEffect(() => {
    loadTiersAndData();
  }, []);

  const loadTiersAndData = async () => {
    setLoading(true);
    try {
      // Load Tiers
      const { data: tierData, error: tierErr } = await supabase
        .from('subscription_tiers')
        .select('*')
        .order('price', { ascending: true });

      if (tierErr || !tierData || tierData.length === 0) {
        console.warn('Could not load subscription_tiers table, using defaults:', tierErr);
        setTiers([
          {
            tier_id: 'free',
            name: 'Free Tier',
            price: 0,
            billing_period: 'forever',
            trial_days: 0,
            max_stores: 1,
            max_items_per_store: 50,
            max_staff_per_store: 1,
            max_ai_parses_per_day: 2,
            max_photo_scans_per_day: 2,
            max_suki_partners: 5,
            has_excel_export: false,
            has_pdf_export: false,
            has_price_cloning: false,
            has_priority_support: false,
            merchant_benefits: ["1 Store Branch", "50 Items / Store", "1 Staff Account", "2 AI Parses / day"],
            customer_benefits: ["5 Suking Tindahan Partners", "Basic Price Search"]
          },
          {
            tier_id: 'pro',
            name: 'PRO Tier',
            price: 100,
            billing_period: 'month',
            trial_days: 7,
            max_stores: 10,
            max_items_per_store: 500,
            max_staff_per_store: 10,
            max_ai_parses_per_day: 10,
            max_photo_scans_per_day: 10,
            max_suki_partners: 15,
            has_excel_export: true,
            has_pdf_export: true,
            has_price_cloning: true,
            has_priority_support: false,
            merchant_benefits: ["Up to 10 Stores", "500 items / store branch", "10 staffs / store branch", "10 AI parses / day", "10 photo scans / day"],
            customer_benefits: ["15 partner stores", "15 Presyohan Stores", "AI Online Price Search"]
          },
          {
            tier_id: 'vip',
            name: 'VIP Tier',
            price: 299,
            billing_period: 'month',
            trial_days: 0,
            max_stores: 999,
            max_items_per_store: 9999,
            max_staff_per_store: 9999,
            max_ai_parses_per_day: 50,
            max_photo_scans_per_day: 50,
            max_suki_partners: 9999,
            has_excel_export: true,
            has_pdf_export: true,
            has_price_cloning: true,
            has_priority_support: true,
            merchant_benefits: ["Unlimited Stores", "Unlimited items / store", "Unlimited staff / store", "50 AI parses / day"],
            customer_benefits: ["Unlimited partner stores", "Unlimited Presyohan Stores", "AI Online Price Search + Priority"]
          }
        ]);
      } else {
        setTiers(tierData || []);
      }

      // Load Users & Stores for overrides
      const { data: userData } = await supabase.from('app_users').select('*').limit(200);
      setUsers(userData || []);

      const { data: storeData } = await supabase.from('stores').select('*').limit(200);
      setStores(storeData || []);

    } catch (err) {
      console.error('Subscription management load error:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleSaveTier = async (e) => {
    e.preventDefault();
    if (!editingTier) return;
    setMutating(true);
    try {
      const { error } = await supabase
        .from('subscription_tiers')
        .upsert(editingTier);

      if (error) throw error;
      alert(`🎉 Success! Updated ${editingTier.name} subscription parameters.`);
      setEditingTier(null);
      loadTiersAndData();
    } catch (err) {
      console.error('Failed to update tier:', err);
      alert('Error updating tier: ' + err.message);
    } finally {
      setMutating(false);
    }
  };

  const handleAddMerchantBenefit = () => {
    if (!newMerchantBenefit.trim() || !editingTier) return;
    const updated = {
      ...editingTier,
      merchant_benefits: [...(editingTier.merchant_benefits || []), newMerchantBenefit.trim()]
    };
    setEditingTier(updated);
    setNewMerchantBenefit('');
  };

  const handleRemoveMerchantBenefit = (index) => {
    if (!editingTier) return;
    const updated = {
      ...editingTier,
      merchant_benefits: (editingTier.merchant_benefits || []).filter((_, i) => i !== index)
    };
    setEditingTier(updated);
  };

  const handleAddCustomerBenefit = () => {
    if (!newCustomerBenefit.trim() || !editingTier) return;
    const updated = {
      ...editingTier,
      customer_benefits: [...(editingTier.customer_benefits || []), newCustomerBenefit.trim()]
    };
    setEditingTier(updated);
    setNewCustomerBenefit('');
  };

  const handleRemoveCustomerBenefit = (index) => {
    if (!editingTier) return;
    const updated = {
      ...editingTier,
      customer_benefits: (editingTier.customer_benefits || []).filter((_, i) => i !== index)
    };
    setEditingTier(updated);
  };

  const handleApplyOverride = async () => {
    if (!selectedEntity) return;
    setMutating(true);

    let expiresAt = null;
    if (overrideDuration !== 'permanent') {
      const days = parseInt(overrideDuration, 10);
      const d = new Date();
      d.setDate(d.getDate() + days);
      expiresAt = d.toISOString();
    }

    try {
      const targetUserId = selectedEntity.item.id;
      
      const { error: rpcErr } = await supabase.rpc('override_subscription_tier', {
        target_user_id: targetUserId,
        new_tier: overrideTier,
        expires_at: expiresAt
      });

      if (rpcErr) {
        console.warn('RPC override_subscription_tier fallback to direct update:', rpcErr);
        const { error: userErr } = await supabase
          .from('app_users')
          .update({
            subscription_tier: overrideTier,
            subscription_expires_at: expiresAt
          })
          .eq('id', targetUserId);

        if (userErr) throw userErr;

        await supabase
          .from('stores')
          .update({
            subscription_tier: overrideTier,
            subscription_expires_at: expiresAt
          })
          .eq('owner_id', targetUserId);
      }

      alert(`🎉 Granted '${overrideTier.toUpperCase()}' tier override to ${selectedEntity.item.name || selectedEntity.item.email}!`);
      setSelectedEntity(null);
      loadTiersAndData();
    } catch (err) {
      console.error('Failed to apply subscription override:', err);
      alert('Error applying tier override: ' + err.message);
    } finally {
      setMutating(false);
    }
  };

  if (loading) {
    return (
      <div style={{ padding: '80px 20px', textAlign: 'center', color: '#64748b' }}>
        <div style={{ fontSize: '2.5rem', marginBottom: '12px', animation: 'spin 2s linear infinite' }}>⚙️</div>
        <div style={{ fontWeight: 700, fontSize: '1.1rem', color: '#0F172A' }}>Loading Subscriptions &amp; Tiers Engine...</div>
        <div style={{ fontSize: '0.85rem', marginTop: '4px' }}>Fetching pricing tiers, quota rules, and active account statistics</div>
      </div>
    );
  }

  // Filter accounts
  const filteredUsers = users.filter(u => {
    const matchesSearch = (u.name || '').toLowerCase().includes(searchQuery.toLowerCase()) ||
                          (u.email || '').toLowerCase().includes(searchQuery.toLowerCase());
    const userTier = u.subscription_tier || 'free';
    const matchesTier = tierFilter === 'all' || userTier === tierFilter;
    return matchesSearch && matchesTier;
  });

  const filteredStores = stores.filter(s => {
    const matchesSearch = (s.name || '').toLowerCase().includes(searchQuery.toLowerCase()) ||
                          (s.display_id || '').toLowerCase().includes(searchQuery.toLowerCase());
    const storeTier = s.subscription_tier || 'free';
    const matchesTier = tierFilter === 'all' || storeTier === tierFilter;
    return matchesSearch && matchesTier;
  });

  // Calculate metrics
  const proCount = users.filter(u => u.subscription_tier === 'pro').length;
  const vipCount = users.filter(u => u.subscription_tier === 'vip').length;
  const freeCount = Math.max(0, users.length - proCount - vipCount);
  const proPrice = tiers.find(t => t.tier_id === 'pro')?.price || 100;
  const vipPrice = tiers.find(t => t.tier_id === 'vip')?.price || 299;
  const estimatedMRR = (proCount * proPrice) + (vipCount * vipPrice);

  return (
    <div style={{ fontFamily: "'Outfit', sans-serif" }}>
      {/* Header Banner */}
      <div style={{ 
        display: 'flex', 
        justifyContent: 'space-between', 
        alignItems: 'center', 
        marginBottom: '28px',
        flexWrap: 'wrap',
        gap: '16px'
      }}>
        <div>
          <h2 style={{ margin: 0, fontSize: '1.6rem', fontWeight: 800, color: '#0F172A', letterSpacing: '-0.5px' }}>
            Subscriptions &amp; Tiers Control Center
          </h2>
          <p style={{ margin: '4px 0 0 0', color: '#64748B', fontSize: '0.9rem' }}>
            Manage pricing tiers, capacity caps, feature flags, and grant merchant subscription overrides.
          </p>
        </div>

        {/* Quick Stat Pill */}
        <div style={{ 
          display: 'flex', 
          alignItems: 'center', 
          gap: '16px',
          backgroundColor: '#FFFFFF',
          padding: '10px 20px',
          borderRadius: '16px',
          boxShadow: '0 4px 14px rgba(0,0,0,0.03)',
          border: '1px solid rgba(0,0,0,0.04)'
        }}>
          <div>
            <div style={{ fontSize: '0.72rem', fontWeight: 700, color: '#64748B', textTransform: 'uppercase' }}>Est. MRR</div>
            <div style={{ fontSize: '1.25rem', fontWeight: 800, color: '#00897B' }}>₱{estimatedMRR.toLocaleString()}</div>
          </div>
          <div style={{ width: '1dp', height: '28dp', backgroundColor: '#E2E8F0' }} />
          <div>
            <div style={{ fontSize: '0.72rem', fontWeight: 700, color: '#64748B', textTransform: 'uppercase' }}>Active Paid</div>
            <div style={{ fontSize: '1.25rem', fontWeight: 800, color: '#FB8500' }}>{proCount + vipCount} Accounts</div>
          </div>
        </div>
      </div>

      {/* Sleek Sub-Tab Navigation Bar */}
      <div style={{ 
        display: 'inline-flex', 
        gap: '6px', 
        backgroundColor: '#F1F5F9', 
        padding: '6px', 
        borderRadius: '16px', 
        marginBottom: '28px',
        boxShadow: 'inset 0 2px 4px rgba(0,0,0,0.02)'
      }}>
        <button
          className={`admin-menu-item ${subTab === 'config' ? 'active' : ''}`}
          onClick={() => setSubTab('config')}
          style={{
            padding: '10px 22px',
            borderRadius: '12px',
            fontWeight: 700,
            fontSize: '0.9rem',
            backgroundColor: subTab === 'config' ? '#FFFFFF' : 'transparent',
            color: subTab === 'config' ? '#0F172A' : '#64748B',
            boxShadow: subTab === 'config' ? '0 4px 14px rgba(0,0,0,0.06)' : 'none',
            transition: 'all 0.2s ease',
            border: 'none',
            cursor: 'pointer'
          }}
        >
          ⚙️ Plan Tier Config
        </button>

        <button
          className={`admin-menu-item ${subTab === 'overrides' ? 'active' : ''}`}
          onClick={() => setSubTab('overrides')}
          style={{
            padding: '10px 22px',
            borderRadius: '12px',
            fontWeight: 700,
            fontSize: '0.9rem',
            backgroundColor: subTab === 'overrides' ? '#FFFFFF' : 'transparent',
            color: subTab === 'overrides' ? '#0F172A' : '#64748B',
            boxShadow: subTab === 'overrides' ? '0 4px 14px rgba(0,0,0,0.06)' : 'none',
            transition: 'all 0.2s ease',
            border: 'none',
            cursor: 'pointer'
          }}
        >
          👑 Manual Tier Overrides
        </button>

        <button
          className={`admin-menu-item ${subTab === 'usage' ? 'active' : ''}`}
          onClick={() => setSubTab('usage')}
          style={{
            padding: '10px 22px',
            borderRadius: '12px',
            fontWeight: 700,
            fontSize: '0.9rem',
            backgroundColor: subTab === 'usage' ? '#FFFFFF' : 'transparent',
            color: subTab === 'usage' ? '#0F172A' : '#64748B',
            boxShadow: subTab === 'usage' ? '0 4px 14px rgba(0,0,0,0.06)' : 'none',
            transition: 'all 0.2s ease',
            border: 'none',
            cursor: 'pointer'
          }}
        >
          📊 Quotas &amp; Analytics
        </button>
      </div>

      {/* TAB 1: PLAN CONFIGURATION */}
      {subTab === 'config' && (
        <div>
          <div style={{ 
            display: 'grid', 
            gridTemplateColumns: 'repeat(auto-fit, minmax(330px, 1fr))', 
            gap: '28px' 
          }}>
            {tiers.map((tier) => {
              const isVip = tier.tier_id === 'vip';
              const isPro = tier.tier_id === 'pro';

              return (
                <div 
                  key={tier.tier_id} 
                  className="admin-card" 
                  style={{ 
                    padding: '0', 
                    overflow: 'hidden',
                    border: isVip ? '2px solid #00897B' : isPro ? '2px solid #FB8500' : '1px solid #E2E8F0',
                    boxShadow: isVip ? '0 16px 36px -8px rgba(0, 137, 123, 0.16)' : isPro ? '0 16px 36px -8px rgba(251, 133, 0, 0.16)' : '0 6px 20px rgba(0,0,0,0.03)',
                    borderRadius: '24px',
                    transition: 'transform 0.25s ease, box-shadow 0.25s ease',
                    position: 'relative'
                  }}
                >
                  {/* Card Top Accent Banner */}
                  <div style={{
                    padding: '28px 24px 24px 24px',
                    background: isVip 
                      ? 'linear-gradient(135deg, #E6FFFA 0%, #B2F5EA 100%)' 
                      : isPro 
                      ? 'linear-gradient(135deg, #FFF5EB 0%, #FFEDD5 100%)' 
                      : '#F8FAFC',
                    borderBottom: '1px solid rgba(0,0,0,0.06)'
                  }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                      <span style={{ 
                        fontSize: '0.78rem', 
                        fontWeight: 800, 
                        letterSpacing: '0.6px',
                        textTransform: 'uppercase',
                        padding: '5px 12px',
                        borderRadius: '20px',
                        backgroundColor: isVip ? '#00897B' : isPro ? '#FB8500' : '#64748B',
                        color: '#FFFFFF',
                        boxShadow: isVip ? '0 4px 10px rgba(0,137,123,0.3)' : isPro ? '0 4px 10px rgba(251,133,0,0.3)' : 'none'
                      }}>
                        {isVip ? 'VIP TIER 💎' : isPro ? 'PRO TIER ⭐' : 'FREE TIER 🎁'}
                      </span>

                      {tier.trial_days > 0 && (
                        <span style={{ fontSize: '0.78rem', color: '#D97706', fontWeight: 700, backgroundColor: '#FEF3C7', padding: '4px 10px', borderRadius: '12px' }}>
                          ⚡ {tier.trial_days}-Day Trial Included
                        </span>
                      )}
                    </div>

                    <h3 style={{ margin: '0 0 6px 0', fontSize: '1.45rem', fontWeight: 800, color: isVip ? '#064E3B' : isPro ? '#9A3412' : '#0F172A' }}>
                      {tier.name}
                    </h3>

                    <div style={{ display: 'flex', alignItems: 'baseline', gap: '6px', marginTop: '10px' }}>
                      <span style={{ fontSize: '2.4rem', fontWeight: 900, color: isVip ? '#00897B' : isPro ? '#FB8500' : '#0F172A', lineHeight: 1 }}>
                        ₱{tier.price}
                      </span>
                      <span style={{ fontSize: '0.9rem', color: '#64748B', fontWeight: 600 }}>
                        / {tier.billing_period}
                      </span>
                    </div>
                  </div>

                  {/* Limits & Feature List Body */}
                  <div style={{ padding: '24px' }}>
                    <div style={{ fontSize: '0.75rem', fontWeight: 800, color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '12px' }}>
                      Capacity Caps &amp; Limits
                    </div>

                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px', marginBottom: '20px' }}>
                      <div style={{ backgroundColor: '#F8FAFC', padding: '12px', borderRadius: '14px', border: '1px solid #F1F5F9' }}>
                        <div style={{ fontSize: '0.72rem', color: '#64748B', fontWeight: 700 }}>STORE BRANCHES</div>
                        <div style={{ fontSize: '1rem', fontWeight: 800, color: '#0F172A', marginTop: '2px' }}>
                          {tier.max_stores > 999 ? 'Unlimited ∞' : `${tier.max_stores} Store${tier.max_stores > 1 ? 's' : ''}`}
                        </div>
                      </div>

                      <div style={{ backgroundColor: '#F8FAFC', padding: '12px', borderRadius: '14px', border: '1px solid #F1F5F9' }}>
                        <div style={{ fontSize: '0.72rem', color: '#64748B', fontWeight: 700 }}>ITEMS PER STORE</div>
                        <div style={{ fontSize: '1rem', fontWeight: 800, color: '#0F172A', marginTop: '2px' }}>
                          {tier.max_items_per_store > 9999 ? 'Unlimited ∞' : `${tier.max_items_per_store} items`}
                        </div>
                      </div>

                      <div style={{ backgroundColor: '#F8FAFC', padding: '12px', borderRadius: '14px', border: '1px solid #F1F5F9' }}>
                        <div style={{ fontSize: '0.72rem', color: '#64748B', fontWeight: 700 }}>STAFF ACCOUNTS</div>
                        <div style={{ fontSize: '1rem', fontWeight: 800, color: '#0F172A', marginTop: '2px' }}>
                          {tier.max_staff_per_store > 9999 ? 'Unlimited ∞' : `${tier.max_staff_per_store} staff`}
                        </div>
                      </div>

                      <div style={{ backgroundColor: '#F8FAFC', padding: '12px', borderRadius: '14px', border: '1px solid #F1F5F9' }}>
                        <div style={{ fontSize: '0.72rem', color: '#64748B', fontWeight: 700 }}>DAILY AI PARSES</div>
                        <div style={{ fontSize: '1rem', fontWeight: 800, color: '#0F172A', marginTop: '2px' }}>
                          {tier.max_ai_parses_per_day} / day
                        </div>
                      </div>
                    </div>

                    {/* Merchant & Customer Benefits Preview */}
                    <div style={{ fontSize: '0.75rem', fontWeight: 800, color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '10px' }}>
                      Key Features &amp; Perks
                    </div>

                    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '20px' }}>
                      {(tier.merchant_benefits || []).slice(0, 4).map((b, i) => (
                        <div key={i} style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.85rem', color: '#334155', fontWeight: 500 }}>
                          <span style={{ color: isVip ? '#00897B' : isPro ? '#FB8500' : '#64748B', fontWeight: 800 }}>✓</span>
                          <span>{b}</span>
                        </div>
                      ))}
                    </div>

                    {/* Feature Toggles Badges */}
                    <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', marginBottom: '24px' }}>
                      <span style={{ fontSize: '0.75rem', padding: '4px 10px', borderRadius: '8px', backgroundColor: tier.has_excel_export ? '#ECFDF5' : '#F1F5F9', color: tier.has_excel_export ? '#047857' : '#94A3B8', fontWeight: 700 }}>
                        {tier.has_excel_export ? '✓ Excel Export' : '✗ Excel Export'}
                      </span>
                      <span style={{ fontSize: '0.75rem', padding: '4px 10px', borderRadius: '8px', backgroundColor: tier.has_pdf_export ? '#ECFDF5' : '#F1F5F9', color: tier.has_pdf_export ? '#047857' : '#94A3B8', fontWeight: 700 }}>
                        {tier.has_pdf_export ? '✓ PDF Export' : '✗ PDF Export'}
                      </span>
                      <span style={{ fontSize: '0.75rem', padding: '4px 10px', borderRadius: '8px', backgroundColor: tier.has_price_cloning ? '#ECFDF5' : '#F1F5F9', color: tier.has_price_cloning ? '#047857' : '#94A3B8', fontWeight: 700 }}>
                        {tier.has_price_cloning ? '✓ Price Cloning' : '✗ Price Cloning'}
                      </span>
                    </div>

                    <button
                      className="admin-btn-action"
                      style={{ 
                        width: '100%', 
                        padding: '14px', 
                        backgroundColor: isVip ? '#00897B' : isPro ? '#FB8500' : '#475569', 
                        color: '#FFFFFF', 
                        borderRadius: '14px',
                        fontWeight: 800,
                        fontSize: '0.9rem',
                        border: 'none',
                        cursor: 'pointer',
                        boxShadow: isVip ? '0 6px 16px rgba(0, 137, 123, 0.25)' : isPro ? '0 6px 16px rgba(251, 133, 0, 0.25)' : '0 4px 12px rgba(0,0,0,0.1)',
                        transition: 'all 0.2s ease'
                      }}
                      onClick={() => setEditingTier({ ...tier })}
                    >
                      ✏️ Edit Plan Parameters &amp; Features
                    </button>
                  </div>
                </div>
              );
            })}
          </div>

          {/* EDIT TIER MODAL */}
          {editingTier && (
            <div style={{ 
              position: 'fixed', 
              top: 0, 
              left: 0, 
              right: 0, 
              bottom: 0, 
              backgroundColor: 'rgba(15, 23, 42, 0.65)', 
              backdropFilter: 'blur(8px)',
              display: 'flex', 
              alignItems: 'center', 
              justifyContent: 'center', 
              zIndex: 1000,
              padding: '20px'
            }}>
              <div style={{ 
                backgroundColor: '#FFFFFF', 
                borderRadius: '24px', 
                width: '640px', 
                maxWidth: '96vw', 
                maxHeight: '92vh', 
                overflowY: 'auto', 
                padding: '32px',
                boxShadow: '0 25px 50px -12px rgba(0,0,0,0.25)'
              }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
                  <div>
                    <h3 style={{ margin: 0, fontSize: '1.4rem', color: '#0F172A', fontWeight: 800 }}>
                      Edit Tier Config: <span style={{ color: '#FB8500' }}>{editingTier.name}</span>
                    </h3>
                    <p style={{ margin: '2px 0 0 0', fontSize: '0.85rem', color: '#64748B' }}>
                      Updates apply instantly to all live app paywalls and capacity checks.
                    </p>
                  </div>
                  <button 
                    onClick={() => setEditingTier(null)}
                    style={{ background: '#F1F5F9', border: 'none', width: '36px', height: '36px', borderRadius: '50%', fontSize: '1.1rem', cursor: 'pointer', color: '#64748B', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                  >
                    ✕
                  </button>
                </div>

                <form onSubmit={handleSaveTier}>
                  {/* Pricing & Trial */}
                  <div style={{ backgroundColor: '#F8FAFC', padding: '16px', borderRadius: '16px', marginBottom: '16px' }}>
                    <div style={{ fontSize: '0.8rem', fontWeight: 800, color: '#475569', marginBottom: '12px', textTransform: 'uppercase' }}>
                      💰 Pricing &amp; Billing Cycle
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
                      <div>
                        <label style={{ fontSize: '0.8rem', fontWeight: 600, color: '#475569' }}>Monthly Price (₱)</label>
                        <input
                          type="number"
                          className="admin-search-input"
                          style={{ width: '100%', marginTop: '4px' }}
                          value={editingTier.price}
                          onChange={(e) => setEditingTier({ ...editingTier, price: parseFloat(e.target.value) || 0 })}
                        />
                      </div>
                      <div>
                        <label style={{ fontSize: '0.8rem', fontWeight: 600, color: '#475569' }}>Trial Duration (Days)</label>
                        <input
                          type="number"
                          className="admin-search-input"
                          style={{ width: '100%', marginTop: '4px' }}
                          value={editingTier.trial_days}
                          onChange={(e) => setEditingTier({ ...editingTier, trial_days: parseInt(e.target.value, 10) || 0 })}
                        />
                      </div>
                    </div>
                  </div>

                  {/* Capacity Limits */}
                  <div style={{ backgroundColor: '#F8FAFC', padding: '16px', borderRadius: '16px', marginBottom: '16px' }}>
                    <div style={{ fontSize: '0.8rem', fontWeight: 800, color: '#475569', marginBottom: '12px', textTransform: 'uppercase' }}>
                      🏢 Merchant Capacity Caps
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px', marginBottom: '14px' }}>
                      <div>
                        <label style={{ fontSize: '0.8rem', fontWeight: 600, color: '#475569' }}>Max Store Branches</label>
                        <input
                          type="number"
                          className="admin-search-input"
                          style={{ width: '100%', marginTop: '4px' }}
                          value={editingTier.max_stores}
                          onChange={(e) => setEditingTier({ ...editingTier, max_stores: parseInt(e.target.value, 10) || 1 })}
                        />
                      </div>
                      <div>
                        <label style={{ fontSize: '0.8rem', fontWeight: 600, color: '#475569' }}>Max Items / Store</label>
                        <input
                          type="number"
                          className="admin-search-input"
                          style={{ width: '100%', marginTop: '4px' }}
                          value={editingTier.max_items_per_store}
                          onChange={(e) => setEditingTier({ ...editingTier, max_items_per_store: parseInt(e.target.value, 10) || 50 })}
                        />
                      </div>
                    </div>

                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
                      <div>
                        <label style={{ fontSize: '0.8rem', fontWeight: 600, color: '#475569' }}>Max Staff Accounts / Store</label>
                        <input
                          type="number"
                          className="admin-search-input"
                          style={{ width: '100%', marginTop: '4px' }}
                          value={editingTier.max_staff_per_store}
                          onChange={(e) => setEditingTier({ ...editingTier, max_staff_per_store: parseInt(e.target.value, 10) || 1 })}
                        />
                      </div>
                      <div>
                        <label style={{ fontSize: '0.8rem', fontWeight: 600, color: '#475569' }}>Max Suki Partner Stores</label>
                        <input
                          type="number"
                          className="admin-search-input"
                          style={{ width: '100%', marginTop: '4px' }}
                          value={editingTier.max_suki_partners}
                          onChange={(e) => setEditingTier({ ...editingTier, max_suki_partners: parseInt(e.target.value, 10) || 5 })}
                        />
                      </div>
                    </div>
                  </div>

                  {/* AI Quotas */}
                  <div style={{ backgroundColor: '#F8FAFC', padding: '16px', borderRadius: '16px', marginBottom: '16px' }}>
                    <div style={{ fontSize: '0.8rem', fontWeight: 800, color: '#475569', marginBottom: '12px', textTransform: 'uppercase' }}>
                      🤖 AI Quotas &amp; Image Scans
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
                      <div>
                        <label style={{ fontSize: '0.8rem', fontWeight: 600, color: '#475569' }}>Daily AI Parses / day</label>
                        <input
                          type="number"
                          className="admin-search-input"
                          style={{ width: '100%', marginTop: '4px' }}
                          value={editingTier.max_ai_parses_per_day}
                          onChange={(e) => setEditingTier({ ...editingTier, max_ai_parses_per_day: parseInt(e.target.value, 10) || 2 })}
                        />
                      </div>
                      <div>
                        <label style={{ fontSize: '0.8rem', fontWeight: 600, color: '#475569' }}>Daily Photo Scans / day</label>
                        <input
                          type="number"
                          className="admin-search-input"
                          style={{ width: '100%', marginTop: '4px' }}
                          value={editingTier.max_photo_scans_per_day}
                          onChange={(e) => setEditingTier({ ...editingTier, max_photo_scans_per_day: parseInt(e.target.value, 10) || 2 })}
                        />
                      </div>
                    </div>
                  </div>

                  {/* Feature Toggles */}
                  <div style={{ backgroundColor: '#F8FAFC', padding: '16px', borderRadius: '16px', marginBottom: '20px' }}>
                    <div style={{ fontSize: '0.8rem', fontWeight: 800, color: '#475569', marginBottom: '12px', textTransform: 'uppercase' }}>
                      ⚡ Feature Flags &amp; Toggles
                    </div>
                    <div style={{ display: 'flex', gap: '20px', flexWrap: 'wrap' }}>
                      <label style={{ fontSize: '0.85rem', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
                        <input
                          type="checkbox"
                          checked={editingTier.has_excel_export}
                          onChange={(e) => setEditingTier({ ...editingTier, has_excel_export: e.target.checked })}
                        /> Excel Export
                      </label>
                      <label style={{ fontSize: '0.85rem', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
                        <input
                          type="checkbox"
                          checked={editingTier.has_pdf_export}
                          onChange={(e) => setEditingTier({ ...editingTier, has_pdf_export: e.target.checked })}
                        /> PDF Export
                      </label>
                      <label style={{ fontSize: '0.85rem', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
                        <input
                          type="checkbox"
                          checked={editingTier.has_price_cloning}
                          onChange={(e) => setEditingTier({ ...editingTier, has_price_cloning: e.target.checked })}
                        /> Store Price Cloning
                      </label>
                      <label style={{ fontSize: '0.85rem', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
                        <input
                          type="checkbox"
                          checked={editingTier.has_priority_support}
                          onChange={(e) => setEditingTier({ ...editingTier, has_priority_support: e.target.checked })}
                        /> 24/7 VIP Support
                      </label>
                    </div>
                  </div>

                  {/* Dynamic Merchant Benefits Editor */}
                  <div style={{ backgroundColor: '#FFF7ED', padding: '16px', borderRadius: '16px', marginBottom: '20px', border: '1px solid #FFEDD5' }}>
                    <div style={{ fontSize: '0.8rem', fontWeight: 800, color: '#C2410C', marginBottom: '10px', textTransform: 'uppercase' }}>
                      📋 Mobile Paywall Benefit Bullet Points
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '12px' }}>
                      {(editingTier.merchant_benefits || []).map((b, idx) => (
                        <div key={idx} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', backgroundColor: '#FFFFFF', padding: '8px 12px', borderRadius: '10px', border: '1px solid #FFEDD5' }}>
                          <span style={{ fontSize: '0.85rem', color: '#0F172A', fontWeight: 500 }}>✓ {b}</span>
                          <button
                            type="button"
                            onClick={() => handleRemoveMerchantBenefit(idx)}
                            style={{ background: 'none', border: 'none', color: '#EF4444', fontWeight: 700, cursor: 'pointer' }}
                          >
                            ✕
                          </button>
                        </div>
                      ))}
                    </div>
                    <div style={{ display: 'flex', gap: '8px' }}>
                      <input
                        type="text"
                        className="admin-search-input"
                        placeholder="Add new benefit (e.g. 'Unlimited Store Locations')..."
                        value={newMerchantBenefit}
                        onChange={(e) => setNewMerchantBenefit(e.target.value)}
                        style={{ flex: 1 }}
                      />
                      <button
                        type="button"
                        onClick={handleAddMerchantBenefit}
                        style={{ backgroundColor: '#FB8500', color: '#FFFFFF', border: 'none', padding: '8px 16px', borderRadius: '10px', fontWeight: 700, cursor: 'pointer' }}
                      >
                        + Add Bullet
                      </button>
                    </div>
                  </div>

                  {/* Actions */}
                  <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '28px' }}>
                    <button
                      type="button"
                      className="admin-btn-action"
                      style={{ backgroundColor: '#F1F5F9', color: '#475569', padding: '12px 20px', borderRadius: '12px', fontWeight: 700 }}
                      onClick={() => setEditingTier(null)}
                    >
                      Cancel
                    </button>
                    <button
                      type="submit"
                      className="admin-btn-action"
                      style={{ backgroundColor: '#FB8500', color: '#FFFFFF', padding: '12px 24px', borderRadius: '12px', fontWeight: 800, border: 'none', cursor: 'pointer', boxShadow: '0 4px 14px rgba(251, 133, 0, 0.3)' }}
                      disabled={mutating}
                    >
                      {mutating ? 'Saving Settings...' : 'Save Plan Changes'}
                    </button>
                  </div>
                </form>
              </div>
            </div>
          )}
        </div>
      )}

      {/* TAB 2: MANUAL TIER OVERRIDES */}
      {subTab === 'overrides' && (
        <div>
          {/* Info Banner */}
          <div style={{ 
            backgroundColor: '#EFF6FF', 
            border: '1px solid #BFDBFE', 
            borderRadius: '20px', 
            padding: '18px 24px', 
            marginBottom: '28px',
            display: 'flex',
            alignItems: 'center',
            gap: '16px',
            boxShadow: '0 4px 12px rgba(59, 130, 246, 0.05)'
          }}>
            <div style={{ fontSize: '1.8rem' }}>👑</div>
            <div style={{ fontSize: '0.9rem', color: '#1E40AF', lineHeight: '1.5' }}>
              <strong>Subscription Hierarchy:</strong> Subscriptions are bound to <strong>Merchant User Accounts (Store Owners)</strong>. Granting a PRO or VIP override to an owner automatically upgrades all current and future store branches owned by that user account.
            </div>
          </div>

          {/* Controls Bar: Search + Filter Chips */}
          <div style={{ 
            display: 'flex', 
            justifyContent: 'space-between', 
            alignItems: 'center', 
            marginBottom: '24px',
            flexWrap: 'wrap',
            gap: '16px'
          }}>
            <div className="admin-search-wrapper" style={{ maxWidth: '420px', width: '100%', margin: 0 }}>
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
              <input
                type="text"
                className="admin-search-input"
                placeholder="Search merchant name, email, or store title..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>

            {/* Filter Chips */}
            <div style={{ display: 'flex', gap: '8px' }}>
              {['all', 'free', 'pro', 'vip'].map((tf) => (
                <button
                  key={tf}
                  onClick={() => setTierFilter(tf)}
                  style={{
                    padding: '8px 16px',
                    borderRadius: '20px',
                    fontSize: '0.82rem',
                    fontWeight: 700,
                    textTransform: 'uppercase',
                    border: 'none',
                    cursor: 'pointer',
                    backgroundColor: tierFilter === tf ? (tf === 'vip' ? '#00897B' : tf === 'pro' ? '#FB8500' : '#0F172A') : '#F1F5F9',
                    color: tierFilter === tf ? '#FFFFFF' : '#64748B',
                    transition: 'all 0.2s ease'
                  }}
                >
                  {tf === 'all' ? 'All Plans' : tf === 'pro' ? 'PRO ⭐' : tf === 'vip' ? 'VIP 💎' : 'Free 🎁'}
                </button>
              ))}
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '28px' }}>
            {/* User Search Results */}
            <div className="admin-card" style={{ padding: '24px', borderRadius: '24px' }}>
              <h4 style={{ margin: '0 0 18px 0', fontSize: '1.1rem', color: '#0F172A', fontWeight: 800, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span>👤 Merchant Accounts</span>
                <span style={{ fontSize: '0.78rem', backgroundColor: '#F1F5F9', color: '#64748B', padding: '4px 10px', borderRadius: '14px', fontWeight: 700 }}>
                  {filteredUsers.length} Users Found
                </span>
              </h4>

              <div className="admin-table-container" style={{ maxHeight: '420px', overflowY: 'auto' }}>
                <table className="admin-table">
                  <thead>
                    <tr>
                      <th>Account Owner</th>
                      <th>Active Plan</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredUsers.length === 0 ? (
                      <tr>
                        <td colSpan={3} style={{ textAlign: 'center', padding: '30px', color: '#94A3B8' }}>
                          No matching merchant accounts found
                        </td>
                      </tr>
                    ) : (
                      filteredUsers.slice(0, 15).map((u) => {
                        const tier = u.subscription_tier || 'free';
                        return (
                          <tr key={u.id}>
                            <td>
                              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                                <div style={{ 
                                  width: '36px', 
                                  height: '36px', 
                                  borderRadius: '10px', 
                                  backgroundColor: tier === 'vip' ? '#CCFBF1' : tier === 'pro' ? '#FFEDD5' : '#F1F5F9',
                                  color: tier === 'vip' ? '#00897B' : tier === 'pro' ? '#FB8500' : '#64748B',
                                  fontWeight: 800,
                                  display: 'flex',
                                  alignItems: 'center',
                                  justifyContent: 'center',
                                  fontSize: '0.85rem'
                                }}>
                                  {(u.name || u.email || 'M').charAt(0).toUpperCase()}
                                </div>
                                <div>
                                  <div style={{ fontSize: '0.88rem', fontWeight: 700, color: '#0F172A' }}>{u.name || 'Merchant User'}</div>
                                  <div style={{ fontSize: '0.75rem', color: '#64748B' }}>{u.email}</div>
                                </div>
                              </div>
                            </td>
                            <td>
                              <span className="admin-badge active" style={{ 
                                textTransform: 'uppercase',
                                fontWeight: 800,
                                padding: '4px 10px',
                                borderRadius: '12px',
                                backgroundColor: tier === 'vip' ? '#ECFDF5' : tier === 'pro' ? '#FFF7ED' : '#F1F5F9',
                                color: tier === 'vip' ? '#00897B' : tier === 'pro' ? '#FB8500' : '#64748B'
                              }}>
                                {tier === 'vip' ? 'VIP 💎' : tier === 'pro' ? 'PRO ⭐' : 'FREE'}
                              </span>
                            </td>
                            <td>
                              <button
                                className="admin-btn-action"
                                style={{ 
                                  backgroundColor: '#FB8500', 
                                  color: '#FFFFFF', 
                                  fontSize: '0.78rem', 
                                  padding: '8px 14px',
                                  borderRadius: '10px',
                                  border: 'none',
                                  fontWeight: 800,
                                  cursor: 'pointer',
                                  boxShadow: '0 4px 10px rgba(251, 133, 0, 0.2)'
                                }}
                                onClick={() => setSelectedEntity({ type: 'user', item: u })}
                              >
                                Override Tier
                              </button>
                            </td>
                          </tr>
                        );
                      })
                    )}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Store Directory with Owner Lookup */}
            <div className="admin-card" style={{ padding: '24px', borderRadius: '24px' }}>
              <h4 style={{ margin: '0 0 18px 0', fontSize: '1.1rem', color: '#0F172A', fontWeight: 800, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span>🏬 Retail Store Branches</span>
                <span style={{ fontSize: '0.78rem', backgroundColor: '#F1F5F9', color: '#64748B', padding: '4px 10px', borderRadius: '14px', fontWeight: 700 }}>
                  {filteredStores.length} Stores Found
                </span>
              </h4>

              <div className="admin-table-container" style={{ maxHeight: '420px', overflowY: 'auto' }}>
                <table className="admin-table">
                  <thead>
                    <tr>
                      <th>Store Name</th>
                      <th>Owner Account</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredStores.length === 0 ? (
                      <tr>
                        <td colSpan={3} style={{ textAlign: 'center', padding: '30px', color: '#94A3B8' }}>
                          No matching retail stores found
                        </td>
                      </tr>
                    ) : (
                      filteredStores.slice(0, 15).map((s) => {
                        const owner = users.find(u => u.id === s.owner_id || u.id === s.billing_owner_id);
                        const storeTier = (owner ? owner.subscription_tier : s.subscription_tier) || 'free';

                        return (
                          <tr key={s.id}>
                            <td>
                              <div style={{ fontSize: '0.88rem', fontWeight: 700, color: '#0F172A' }}>{s.name}</div>
                              <div style={{ fontSize: '0.75rem', color: '#64748B' }}>ID: {s.display_id || s.id.slice(0, 8)}</div>
                            </td>
                            <td>
                              <div style={{ fontSize: '0.8rem', color: '#334155', fontWeight: 700 }}>
                                {owner ? (owner.name || owner.email) : 'Owner Account'}
                              </div>
                              <span className="admin-badge active" style={{ 
                                fontSize: '0.68rem', 
                                fontWeight: 800,
                                textTransform: 'uppercase',
                                marginTop: '4px',
                                backgroundColor: storeTier === 'vip' ? '#ECFDF5' : storeTier === 'pro' ? '#FFF7ED' : '#F1F5F9',
                                color: storeTier === 'vip' ? '#00897B' : storeTier === 'pro' ? '#FB8500' : '#64748B'
                              }}>
                                {storeTier === 'vip' ? 'VIP 💎' : storeTier === 'pro' ? 'PRO ⭐' : 'FREE'}
                              </span>
                            </td>
                            <td>
                              <button
                                className="admin-btn-action"
                                style={{ 
                                  backgroundColor: '#00897B', 
                                  color: '#FFFFFF', 
                                  fontSize: '0.78rem', 
                                  padding: '8px 14px',
                                  borderRadius: '10px',
                                  border: 'none',
                                  fontWeight: 800,
                                  cursor: 'pointer',
                                  boxShadow: '0 4px 10px rgba(0, 137, 123, 0.2)'
                                }}
                                onClick={() => setSelectedEntity({ type: 'user', item: owner || { id: s.owner_id, name: s.name, email: s.name } })}
                              >
                                Override Owner
                              </button>
                            </td>
                          </tr>
                        );
                      })
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </div>

          {/* OVERRIDE CONFIRMATION MODAL */}
          {selectedEntity && (
            <div style={{ 
              position: 'fixed', 
              top: 0, 
              left: 0, 
              right: 0, 
              bottom: 0, 
              backgroundColor: 'rgba(15, 23, 42, 0.65)', 
              backdropFilter: 'blur(8px)',
              display: 'flex', 
              alignItems: 'center', 
              justifyContent: 'center', 
              zIndex: 1000,
              padding: '20px'
            }}>
              <div style={{ 
                backgroundColor: '#FFFFFF', 
                borderRadius: '24px', 
                width: '480px', 
                maxWidth: '94vw',
                padding: '32px',
                boxShadow: '0 25px 50px -12px rgba(0,0,0,0.25)'
              }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                  <h3 style={{ marginTop: 0, marginBottom: 0, fontSize: '1.35rem', color: '#0F172A', fontWeight: 800 }}>
                    👑 Grant Plan Override
                  </h3>
                  <button 
                    onClick={() => setSelectedEntity(null)}
                    style={{ background: '#F1F5F9', border: 'none', width: '32px', height: '32px', borderRadius: '50%', cursor: 'pointer', color: '#64748B' }}
                  >
                    ✕
                  </button>
                </div>

                <div style={{ backgroundColor: '#F8FAFC', padding: '14px', borderRadius: '14px', marginBottom: '20px', border: '1px solid #F1F5F9' }}>
                  <div style={{ fontSize: '0.78rem', color: '#64748B', fontWeight: 700, textTransform: 'uppercase' }}>TARGET ACCOUNT OWNER</div>
                  <div style={{ fontSize: '1rem', fontWeight: 800, color: '#0F172A', marginTop: '2px' }}>
                    {selectedEntity.item.name || selectedEntity.item.email}
                  </div>
                  <div style={{ fontSize: '0.78rem', color: '#64748B' }}>{selectedEntity.item.email}</div>
                </div>

                <div style={{ marginBottom: '18px' }}>
                  <label style={{ fontSize: '0.8rem', fontWeight: 700, color: '#475569', display: 'block', marginBottom: '6px' }}>
                    Select Target Plan Tier
                  </label>
                  <select
                    className="admin-select"
                    style={{ width: '100%', height: '46px', fontWeight: 700 }}
                    value={overrideTier}
                    onChange={(e) => setOverrideTier(e.target.value)}
                  >
                    <option value="free">Free Tier (₱0 / mo)</option>
                    <option value="pro">Presyohan PRO ⭐ (₱99 / mo)</option>
                    <option value="vip">Presyohan VIP 💎 (₱299 / mo)</option>
                  </select>
                </div>

                <div style={{ marginBottom: '28px' }}>
                  <label style={{ fontSize: '0.8rem', fontWeight: 700, color: '#475569', display: 'block', marginBottom: '6px' }}>
                    Override Access Duration
                  </label>
                  <select
                    className="admin-select"
                    style={{ width: '100%', height: '46px', fontWeight: 700 }}
                    value={overrideDuration}
                    onChange={(e) => setOverrideDuration(e.target.value)}
                  >
                    <option value="7">7 Days (Trial Extension)</option>
                    <option value="30">30 Days (1 Month Access)</option>
                    <option value="90">90 Days (3 Months Access)</option>
                    <option value="365">365 Days (1 Year Access)</option>
                    <option value="permanent">Permanent / Lifetime VIP Access</option>
                  </select>
                </div>

                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
                  <button
                    className="admin-btn-action"
                    style={{ backgroundColor: '#F1F5F9', color: '#475569', padding: '12px 20px', borderRadius: '12px', fontWeight: 700 }}
                    onClick={() => setSelectedEntity(null)}
                  >
                    Cancel
                  </button>
                  <button
                    className="admin-btn-action"
                    style={{ backgroundColor: '#FB8500', color: '#FFFFFF', padding: '12px 24px', borderRadius: '12px', fontWeight: 800, border: 'none', cursor: 'pointer', boxShadow: '0 4px 14px rgba(251, 133, 0, 0.3)' }}
                    disabled={mutating}
                    onClick={handleApplyOverride}
                  >
                    {mutating ? 'Applying Override...' : 'Apply Tier Override'}
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* TAB 3: AI QUOTAS & SUBSCRIPTION ANALYTICS */}
      {subTab === 'usage' && (
        <div>
          <div style={{ marginBottom: '24px' }}>
            <h4 style={{ margin: '0 0 6px 0', fontSize: '1.2rem', fontWeight: 800, color: '#0F172A' }}>
              Subscription Tier Analytics &amp; Daily AI Quotas
            </h4>
            <p style={{ color: '#64748b', fontSize: '0.9rem', margin: 0 }}>
              Live distribution monitor for registered merchant accounts, daily AI parsing utilization rates, and revenue breakdown.
            </p>
          </div>

          {/* Metric Cards Grid */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '24px', marginBottom: '32px' }}>
            <div className="admin-card" style={{ padding: '24px', borderRadius: '24px', backgroundColor: '#FFFFFF' }}>
              <div style={{ fontSize: '0.78rem', fontWeight: 800, color: '#64748B', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                TOTAL CLIENT ACCOUNTS
              </div>
              <div style={{ fontSize: '2.5rem', fontWeight: 900, color: '#0F172A', marginTop: '8px' }}>{users.length}</div>
              <div style={{ fontSize: '0.8rem', color: '#94A3B8', marginTop: '4px', fontWeight: 500 }}>
                Registered Store Owners
              </div>
            </div>

            <div className="admin-card" style={{ padding: '24px', borderRadius: '24px', backgroundColor: '#FFF7ED', border: '1px solid #FFEDD5' }}>
              <div style={{ fontSize: '0.78rem', fontWeight: 800, color: '#C2410C', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                ACTIVE PRO ACCOUNTS ⭐
              </div>
              <div style={{ fontSize: '2.5rem', fontWeight: 900, color: '#FB8500', marginTop: '8px' }}>
                {proCount}
              </div>
              <div style={{ fontSize: '0.8rem', color: '#EA580C', marginTop: '4px', fontWeight: 600 }}>
                ₱{proCount * 99} / mo revenue
              </div>
            </div>

            <div className="admin-card" style={{ padding: '24px', borderRadius: '24px', backgroundColor: '#ECFDF5', border: '1px solid #CCFBF1' }}>
              <div style={{ fontSize: '0.78rem', fontWeight: 800, color: '#047857', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                ACTIVE VIP ACCOUNTS 💎
              </div>
              <div style={{ fontSize: '2.5rem', fontWeight: 900, color: '#00897B', marginTop: '8px' }}>
                {vipCount}
              </div>
              <div style={{ fontSize: '0.8rem', color: '#059669', marginTop: '4px', fontWeight: 600 }}>
                ₱{vipCount * 299} / mo revenue
              </div>
            </div>
          </div>

          {/* Tier Adoption Visual Bar */}
          <div className="admin-card" style={{ padding: '28px', borderRadius: '24px', marginBottom: '28px' }}>
            <h4 style={{ margin: '0 0 14px 0', fontSize: '1.05rem', color: '#0F172A', fontWeight: 800 }}>
              Subscription Tier Adoption Share
            </h4>

            {/* Visual Multi-Segment Bar */}
            <div style={{ 
              height: '24px', 
              width: '100%', 
              backgroundColor: '#F1F5F9', 
              borderRadius: '12px', 
              overflow: 'hidden', 
              display: 'flex',
              marginBottom: '16px'
            }}>
              <div style={{ width: `${(freeCount / (users.length || 1)) * 100}%`, backgroundColor: '#64748B', title: 'Free Tier' }} />
              <div style={{ width: `${(proCount / (users.length || 1)) * 100}%`, backgroundColor: '#FB8500', title: 'PRO Tier' }} />
              <div style={{ width: `${(vipCount / (users.length || 1)) * 100}%`, backgroundColor: '#00897B', title: 'VIP Tier' }} />
            </div>

            <div style={{ display: 'flex', gap: '24px', flexWrap: 'wrap' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.85rem', color: '#475569', fontWeight: 600 }}>
                <span style={{ width: '12px', height: '12px', borderRadius: '50%', backgroundColor: '#64748B' }} />
                <span>Free Tier: {freeCount} ({Math.round((freeCount / (users.length || 1)) * 100)}%)</span>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.85rem', color: '#FB8500', fontWeight: 700 }}>
                <span style={{ width: '12px', height: '12px', borderRadius: '50%', backgroundColor: '#FB8500' }} />
                <span>PRO Tier: {proCount} ({Math.round((proCount / (users.length || 1)) * 100)}%)</span>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.85rem', color: '#00897B', fontWeight: 700 }}>
                <span style={{ width: '12px', height: '12px', borderRadius: '50%', backgroundColor: '#00897B' }} />
                <span>VIP Tier: {vipCount} ({Math.round((vipCount / (users.length || 1)) * 100)}%)</span>
              </div>
            </div>
          </div>

          {/* Daily Quotas Info Box */}
          <div className="admin-card" style={{ padding: '28px', borderRadius: '24px' }}>
            <h4 style={{ margin: '0 0 8px 0', fontSize: '1.05rem', color: '#0F172A', fontWeight: 800 }}>
              🤖 AI Parser Quota Rule Matrix
            </h4>
            <p style={{ color: '#64748B', fontSize: '0.88rem', margin: '0 0 20px 0' }}>
              Daily quotas reset automatically at midnight PST (00:00 UTC+8) across all active merchant client devices.
            </p>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '16px' }}>
              <div style={{ backgroundColor: '#F8FAFC', padding: '16px', borderRadius: '16px', border: '1px solid #F1F5F9' }}>
                <div style={{ fontSize: '0.85rem', fontWeight: 800, color: '#64748B' }}>FREE PLAN QUOTA</div>
                <div style={{ fontSize: '1.4rem', fontWeight: 800, color: '#0F172A', margin: '6px 0' }}>2 AI Scans / day</div>
                <div style={{ fontSize: '0.78rem', color: '#94A3B8' }}>Restricted to single-branch micro vendors.</div>
              </div>

              <div style={{ backgroundColor: '#FFF7ED', padding: '16px', borderRadius: '16px', border: '1px solid #FFEDD5' }}>
                <div style={{ fontSize: '0.85rem', fontWeight: 800, color: '#C2410C' }}>PRO PLAN QUOTA</div>
                <div style={{ fontSize: '1.4rem', fontWeight: 800, color: '#FB8500', margin: '6px 0' }}>10 AI Scans / day</div>
                <div style={{ fontSize: '0.78rem', color: '#EA580C' }}>Suitable for active multi-branch retailers.</div>
              </div>

              <div style={{ backgroundColor: '#ECFDF5', padding: '16px', borderRadius: '16px', border: '1px solid #CCFBF1' }}>
                <div style={{ fontSize: '0.85rem', fontWeight: 800, color: '#047857' }}>VIP PLAN QUOTA</div>
                <div style={{ fontSize: '1.4rem', fontWeight: 800, color: '#00897B', margin: '6px 0' }}>50 AI Scans / day</div>
                <div style={{ fontSize: '0.78rem', color: '#059669' }}>High capacity fair-use allocation for enterprise.</div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
