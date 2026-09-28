import React, { useState, useEffect } from 'react';
import { supabase } from '../../config/supabaseClient';

export const isPromoActive = (tier) => {
  if (!tier) return false;
  const hasPromo = (tier.discount_percent > 0) || (tier.promo_price !== null && tier.promo_price !== undefined && tier.promo_price !== '') || (tier.trial_days > 0) || Boolean(tier.promo_badge);
  if (!hasPromo) return false;

  const now = new Date().getTime();
  const startMs = tier.promo_start_at ? new Date(tier.promo_start_at).getTime() : 0;
  const endMs = tier.promo_end_at ? new Date(tier.promo_end_at).getTime() : Infinity;

  return now >= startMs && now <= endMs;
};

export const getPromoScheduleStatus = (tier) => {
  if (!tier) return { label: 'No Schedule', status: 'none', color: '#64748B', bg: '#F1F5F9' };
  const hasPromo = (tier.discount_percent > 0) || (tier.promo_price !== null && tier.promo_price !== undefined && tier.promo_price !== '') || (tier.trial_days > 0) || Boolean(tier.promo_badge);
  if (!hasPromo) return { label: 'No Promo Active', status: 'none', color: '#64748B', bg: '#F1F5F9' };

  const now = new Date().getTime();
  const startMs = tier.promo_start_at ? new Date(tier.promo_start_at).getTime() : 0;
  const endMs = tier.promo_end_at ? new Date(tier.promo_end_at).getTime() : Infinity;

  if (now < startMs) {
    const startDate = new Date(tier.promo_start_at).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
    return { label: `Scheduled (Starts ${startDate})`, status: 'scheduled', color: '#D97706', bg: '#FEF3C7' };
  }

  if (now > endMs) {
    const endDate = new Date(tier.promo_end_at).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
    return { label: `Expired on ${endDate}`, status: 'expired', color: '#DC2626', bg: '#FEF2F2' };
  }

  if (tier.promo_end_at) {
    const endDate = new Date(tier.promo_end_at).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
    return { label: `Active (Ends ${endDate})`, status: 'active', color: '#16A34A', bg: '#DCFCE7' };
  }

  return { label: 'Active (No Expiration)', status: 'active', color: '#16A34A', bg: '#DCFCE7' };
};

export const getPromoExpirationNotice = (info) => {
  if (!info) return null;
  const active = isPromoActive(info);
  if (!active) return null;
  if (info.promo_expiry_label && info.promo_expiry_label.trim() !== '') {
    return info.promo_expiry_label.trim();
  }
  if (!info.promo_end_at) return null;
  const endDate = new Date(info.promo_end_at).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
  return `Special offer valid until ${endDate}`;
};

const toDatetimeLocal = (isoStr) => {
  if (!isoStr) return '';
  const date = new Date(isoStr);
  if (isNaN(date.getTime())) return '';
  const pad = (n) => (n < 10 ? '0' + n : n);
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
};

export const mergeTierWithDefaults = (def, fromDb) => {
  if (!fromDb) return { ...def };

  const val = (dbVal, defaultVal) => {
    if (dbVal !== undefined && dbVal !== null && dbVal !== '') return dbVal;
    return defaultVal;
  };

  const result = { ...def };

  if (fromDb.tier_id) result.tier_id = fromDb.tier_id;
  if (fromDb.name) result.name = fromDb.name;
  if (fromDb.price !== undefined && fromDb.price !== null) result.price = Number(fromDb.price);
  if (fromDb.discount_percent !== undefined && fromDb.discount_percent !== null) result.discount_percent = Number(fromDb.discount_percent);
  if (fromDb.billing_period) result.billing_period = fromDb.billing_period;
  if (fromDb.trial_days !== undefined && fromDb.trial_days !== null) result.trial_days = Number(fromDb.trial_days);
  if (fromDb.display_order !== undefined && fromDb.display_order !== null) result.display_order = Number(fromDb.display_order);

  result.promo_price = fromDb.promo_price !== undefined ? fromDb.promo_price : def.promo_price;
  result.promo_badge = val(fromDb.promo_badge, def.promo_badge);
  result.promo_start_at = fromDb.promo_start_at !== undefined ? fromDb.promo_start_at : def.promo_start_at;
  result.promo_end_at = fromDb.promo_end_at !== undefined ? fromDb.promo_end_at : def.promo_end_at;
  result.promo_expiry_label = fromDb.promo_expiry_label !== undefined ? fromDb.promo_expiry_label : def.promo_expiry_label;
  result.cta_button_text = val(fromDb.cta_button_text, def.cta_button_text);
  result.description = val(fromDb.description, def.description);

  [
    'max_stores', 'max_items_per_store', 'max_staff_per_store', 'max_categories_per_store',
    'max_ai_parses_per_day', 'max_photo_scans_per_day', 'max_suki_partners',
    'max_presyohan_stores', 'max_internet_searches_per_day'
  ].forEach(key => {
    if (fromDb[key] !== undefined && fromDb[key] !== null) {
      result[key] = Number(fromDb[key]);
    }
  });

  [
    'has_excel_export', 'has_pdf_export', 'has_notes_export',
    'has_price_cloning', 'has_customer_pairing', 'has_priority_support'
  ].forEach(key => {
    if (fromDb[key] !== undefined && fromDb[key] !== null) {
      result[key] = Boolean(fromDb[key]);
    }
  });

  if (Array.isArray(fromDb.merchant_benefits) && fromDb.merchant_benefits.length > 0) {
    result.merchant_benefits = fromDb.merchant_benefits;
  }
  if (Array.isArray(fromDb.customer_benefits) && fromDb.customer_benefits.length > 0) {
    result.customer_benefits = fromDb.customer_benefits;
  }

  return result;
};

const DEFAULT_TIERS = [
  {
    tier_id: 'free',
    name: 'Free Tier',
    price: 0,
    discount_percent: 0,
    promo_price: null,
    promo_badge: 'BASIC PLAN',
    promo_start_at: null,
    promo_end_at: null,
    promo_expiry_label: null,
    cta_button_text: 'CURRENT ACTIVE PLAN',
    billing_period: 'forever',
    trial_days: 0,
    display_order: 1,
    max_stores: 1,
    max_items_per_store: 100,
    max_staff_per_store: 3,
    max_categories_per_store: 10,
    max_ai_parses_per_day: 3,
    max_photo_scans_per_day: 3,
    max_suki_partners: 5,
    max_presyohan_stores: 5,
    max_internet_searches_per_day: 3,
    has_excel_export: false,
    has_pdf_export: false,
    has_notes_export: true,
    has_price_cloning: false,
    has_customer_pairing: false,
    has_priority_support: false,
    merchant_benefits: ["1 Store Branch", "3 Staffs / Store", "10 Categories / Store", "100 Items / Store", "3 AI Parses / day", "3 Photo Scans / day", "Convert as Notes"],
    customer_benefits: ["5 Suking Tindahan", "5 Presyohan Stores", "3 Internet Searches / day"]
  },
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
    cta_button_text: 'UPGRADE TO PRO (₱99/MO)',
    billing_period: 'month',
    trial_days: 7,
    display_order: 2,
    max_stores: 10,
    max_items_per_store: 500,
    max_staff_per_store: 10,
    max_categories_per_store: 25,
    max_ai_parses_per_day: 10,
    max_photo_scans_per_day: 10,
    max_suki_partners: 15,
    max_presyohan_stores: 15,
    max_internet_searches_per_day: 15,
    has_excel_export: true,
    has_pdf_export: true,
    has_notes_export: true,
    has_price_cloning: true,
    has_customer_pairing: true,
    has_priority_support: false,
    merchant_benefits: ["Up to 10 Stores", "10 Staffs / Store", "25 Categories / Store", "500 Items / Store", "10 AI Parses / day", "10 Photo Scans / day", "Store Items Cloning", "Customer Pairing", "Convert to Excel & PDF"],
    customer_benefits: ["15 Suking Tindahan", "15 Presyohan Stores", "15 Internet Searches / day"]
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
    cta_button_text: 'UPGRADE TO VIP (₱299/MO)',
    billing_period: 'month',
    trial_days: 0,
    display_order: 3,
    max_stores: 999999,
    max_items_per_store: 999999,
    max_staff_per_store: 999999,
    max_categories_per_store: 999999,
    max_ai_parses_per_day: 50,
    max_photo_scans_per_day: 50,
    max_suki_partners: 999999,
    max_presyohan_stores: 999999,
    max_internet_searches_per_day: 999999,
    has_excel_export: true,
    has_pdf_export: true,
    has_notes_export: true,
    has_price_cloning: true,
    has_customer_pairing: true,
    has_priority_support: true,
    merchant_benefits: ["Unlimited Stores", "Unlimited Staff / Store", "Unlimited Categories / Store", "Unlimited Items / Store", "50 AI Parses / day", "50 Photo Scans / day", "Unlimited Cloning & Export", "Unlimited Customer Pairing"],
    customer_benefits: ["Unlimited Suking Tindahan", "Unlimited Presyohan Stores", "Unlimited Internet Search"]
  }
];

export default function SubscriptionManagement() {
  const [subTab, setSubTab] = useState('config'); // 'config' | 'overrides' | 'usage'
  const [tiers, setTiers] = useState(DEFAULT_TIERS);
  const [loading, setLoading] = useState(true);
  const [editingTier, setEditingTier] = useState(null);
  const [showMobilePreview, setShowMobilePreview] = useState(true);

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

  useEffect(() => {
    loadTiersAndData();
  }, []);

  const loadTiersAndData = async () => {
    setLoading(true);
    try {
      // Load Tiers from DB
      const { data: tierData, error: tierErr } = await supabase
        .from('subscription_tiers')
        .select('*');

      if (tierErr) {
        console.warn('Could not load subscription_tiers table, using defaults:', tierErr);
      }

      // Create a map from fetched DB rows by tier_id
      const dbMap = new Map((tierData || []).map(t => [t.tier_id, t]));

      // Merge DB records into DEFAULT_TIERS so ALL 3 TIERS (free, pro, vip) ALWAYS REMAIN PRESENT and intact
      const mergedTiers = DEFAULT_TIERS.map(def => {
        const fromDb = dbMap.get(def.tier_id);
        return mergeTierWithDefaults(def, fromDb);
      });

      // Include any additional custom DB tiers if present
      (tierData || []).forEach(t => {
        if (!mergedTiers.some(m => m.tier_id === t.tier_id)) {
          mergedTiers.push(t);
        }
      });

      // Sort by fixed order: free (1), pro (2), vip (3)
      const orderMap = { free: 1, pro: 2, vip: 3 };
      mergedTiers.sort((a, b) => (orderMap[a.tier_id] || (a.display_order ?? 99)) - (orderMap[b.tier_id] || (b.display_order ?? 99)));

      setTiers(mergedTiers);

      // Load Users & Stores for overrides console
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
      const dbPayload = {
        tier_id: editingTier.tier_id,
        name: editingTier.name,
        price: Number(editingTier.price || 0),
        discount_percent: Number(editingTier.discount_percent || 0),
        promo_price: editingTier.promo_price !== undefined ? editingTier.promo_price : null,
        promo_badge: editingTier.promo_badge || null,
        promo_start_at: editingTier.promo_start_at || null,
        promo_end_at: editingTier.promo_end_at || null,
        promo_expiry_label: editingTier.promo_expiry_label || null,
        cta_button_text: editingTier.cta_button_text || null,
        billing_period: editingTier.billing_period || 'month',
        trial_days: Number(editingTier.trial_days || 0),
        display_order: Number(editingTier.display_order || 1),
        max_stores: Number(editingTier.max_stores || 1),
        max_items_per_store: Number(editingTier.max_items_per_store || 100),
        max_staff_per_store: Number(editingTier.max_staff_per_store || 1),
        max_categories_per_store: Number(editingTier.max_categories_per_store || 10),
        max_ai_parses_per_day: Number(editingTier.max_ai_parses_per_day || 3),
        max_photo_scans_per_day: Number(editingTier.max_photo_scans_per_day || 3),
        max_suki_partners: Number(editingTier.max_suki_partners || 5),
        max_presyohan_stores: Number(editingTier.max_presyohan_stores || 5),
        max_internet_searches_per_day: Number(editingTier.max_internet_searches_per_day || 3),
        has_excel_export: Boolean(editingTier.has_excel_export),
        has_pdf_export: Boolean(editingTier.has_pdf_export),
        has_notes_export: Boolean(editingTier.has_notes_export),
        has_price_cloning: Boolean(editingTier.has_price_cloning),
        has_customer_pairing: Boolean(editingTier.has_customer_pairing),
        has_priority_support: Boolean(editingTier.has_priority_support),
        merchant_benefits: editingTier.merchant_benefits || [],
        customer_benefits: editingTier.customer_benefits || [],
        description: editingTier.description || null,
        updated_at: new Date().toISOString()
      };

      const { error } = await supabase
        .from('subscription_tiers')
        .upsert(dbPayload, { onConflict: 'tier_id' });

      if (error) throw error;

      // Update local state immediately so all tiers stay present without flickering or disappearing
      setTiers(prev => {
        const updated = prev.map(t => t.tier_id === editingTier.tier_id ? { ...t, ...editingTier } : t);
        const orderMap = { free: 1, pro: 2, vip: 3 };
        return updated.sort((a, b) => (orderMap[a.tier_id] || (a.display_order ?? 99)) - (orderMap[b.tier_id] || (b.display_order ?? 99)));
      });

      alert(`Success! Updated ${editingTier.name} subscription parameters.`);
      setEditingTier(null);
      await loadTiersAndData();
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

  const applyPresetDiscount = (pct, badgeLabel) => {
    if (!editingTier) return;
    setEditingTier({
      ...editingTier,
      discount_percent: pct,
      promo_price: null,
      promo_badge: badgeLabel
    });
  };

  const setSchedule7Days = () => {
    if (!editingTier) return;
    const end = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000);
    setEditingTier({
      ...editingTier,
      promo_start_at: editingTier.promo_start_at || new Date().toISOString(),
      promo_end_at: end.toISOString()
    });
  };

  const setSchedule30Days = () => {
    if (!editingTier) return;
    const end = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000);
    setEditingTier({
      ...editingTier,
      promo_start_at: editingTier.promo_start_at || new Date().toISOString(),
      promo_end_at: end.toISOString()
    });
  };

  const setScheduleEndOfMonth = () => {
    if (!editingTier) return;
    const now = new Date();
    const lastDay = new Date(now.getFullYear(), now.getMonth() + 1, 0, 23, 59, 59);
    setEditingTier({
      ...editingTier,
      promo_start_at: editingTier.promo_start_at || new Date().toISOString(),
      promo_end_at: lastDay.toISOString()
    });
  };

  const clearPromoSchedule = () => {
    if (!editingTier) return;
    setEditingTier({
      ...editingTier,
      promo_start_at: null,
      promo_end_at: null
    });
  };

  const renderMobileAppPreview = (tierList, currentEditing) => {
    // Merge live editing tier changes into tier list for live real-time response
    const effectiveTiers = tierList.map(t => {
      if (currentEditing && currentEditing.tier_id === t.tier_id) {
        return currentEditing;
      }
      return t;
    });

    const proInfo = effectiveTiers.find(t => t.tier_id === 'pro') || {};
    const vipInfo = effectiveTiers.find(t => t.tier_id === 'vip') || {};
    const freeInfo = effectiveTiers.find(t => t.tier_id === 'free') || {};

    const computePrices = (tierObj) => {
      const basePrice = tierObj.price || 0;
      const active = isPromoActive(tierObj);
      if (!active) {
        return {
          finalPrice: basePrice,
          basePrice,
          hasDiscount: false,
          priceFormatted: basePrice % 1 === 0 ? `₱${basePrice}` : `₱${basePrice.toFixed(2)}`,
          isActive: false
        };
      }
      const discPct = tierObj.discount_percent || 0;
      const manualPrice = tierObj.promo_price;
      const autoPrice = discPct > 0 ? basePrice * (1 - discPct / 100) : basePrice;
      const finalPrice = (manualPrice !== null && manualPrice !== undefined && manualPrice !== '') ? parseFloat(manualPrice) : autoPrice;
      const hasDiscount = finalPrice < basePrice;
      const priceFormatted = finalPrice % 1 === 0 ? `₱${finalPrice}` : `₱${finalPrice.toFixed(2)}`;
      return { finalPrice, basePrice, hasDiscount, priceFormatted, isActive: true };
    };

    const getEffectiveMobileBadge = (info) => {
      const active = isPromoActive(info);
      if (!active) return info.tier_id === 'vip' ? 'BEST VALUE' : info.tier_id === 'pro' ? 'STANDARD PLAN' : 'BASIC PLAN';
      return info.promo_badge || (info.discount_percent > 0 ? `${info.discount_percent}% OFF` : info.trial_days > 0 ? `${info.trial_days} DAYS TRIAL` : 'SPECIAL OFFER');
    };



    const proPricing = computePrices(proInfo);
    const vipPricing = computePrices(vipInfo);
    const freePricing = computePrices(freeInfo);

    const activeEditId = currentEditing?.tier_id;

    return (
      <div style={{
        width: '350px',
        maxWidth: '100%',
        backgroundColor: '#0F172A',
        borderRadius: '36px',
        padding: '12px',
        boxShadow: '0 25px 60px -15px rgba(0,0,0,0.35), 0 0 0 1px rgba(255,255,255,0.1)',
        fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
        boxSizing: 'border-box'
      }}>
        {/* Phone Notch & Speaker Bezel */}
        <div style={{
          display: 'flex',
          justify: 'center',
          marginBottom: '8px',
          position: 'relative'
        }}>
          <div style={{
            width: '90px',
            height: '14px',
            backgroundColor: '#000000',
            borderBottomLeftRadius: '10px',
            borderBottomRightRadius: '10px',
            display: 'flex',
            justify: 'center',
            alignItems: 'center',
            gap: '8px'
          }}>
            <div style={{ width: '28px', height: '3px', backgroundColor: '#334155', borderRadius: '2px' }} />
            <div style={{ width: '6px', height: '6px', backgroundColor: '#1E293B', borderRadius: '50%' }} />
          </div>
        </div>

        {/* Screen Content Wrapper - ONLY 3 CARDS WITHOUT ACTIVE SUB / USAGE STATS */}
        <div style={{
          backgroundColor: '#F4F6F9',
          borderRadius: '26px',
          overflow: 'hidden',
          maxHeight: '580px',
          overflowY: 'auto',
          position: 'relative',
          fontSize: '12px',
          color: '#1F2937'
        }}>
          {/* Android Status Bar */}
          <div style={{
            padding: '6px 14px 2px 14px',
            display: 'flex',
            justify: 'space-between',
            alignItems: 'center',
            fontSize: '10px',
            fontWeight: 700,
            color: '#475569'
          }}>
            <span>14:00</span>
            <div style={{ display: 'flex', gap: '4px', alignItems: 'center' }}>
              <span>📶</span>
              <span>🔋 95%</span>
            </div>
          </div>

          {/* Mobile Header Bar */}
          <div style={{ padding: '8px 12px' }}>
            <div style={{
              backgroundColor: '#FFB703',
              background: 'linear-gradient(90deg, #FFB703 0%, #FB8500 100%)',
              borderRadius: '18px',
              padding: '8px 12px',
              display: 'flex',
              alignItems: 'center',
              color: '#FFFFFF',
              boxShadow: '0 4px 10px rgba(251, 133, 0, 0.2)'
            }}>
              <div style={{ fontSize: '13px', fontWeight: 800, marginRight: '8px' }}>❮</div>
              <div style={{ flex: 1, textAlign: 'center', fontWeight: 800, fontSize: '12px', letterSpacing: '0.2px' }}>
                Subscriptions &amp; Plans
              </div>
            </div>
          </div>

          <div style={{ padding: '0 10px 14px 10px' }}>
            {/* Section Divider */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', margin: '8px 0 12px 0' }}>
              <div style={{ flex: 1, height: '1px', backgroundColor: '#CBD5E1' }} />
              <span style={{ fontSize: '9px', fontWeight: 800, color: '#64748B' }}>Choose Presyohan Plan</span>
              <div style={{ flex: 1, height: '1px', backgroundColor: '#CBD5E1' }} />
            </div>

            {/* ONLY THE 3 PLAN CARDS ORDERED: PRO -> VIP -> FREE */}

            {/* 1. PRO TIER CARD */}
            <div style={{
              backgroundColor: '#FFFFFF',
              borderRadius: '14px',
              padding: '14px',
              marginBottom: '10px',
              border: activeEditId === 'pro' ? '3px solid #FB8500' : '2px solid #FB8500',
              boxShadow: activeEditId === 'pro' ? '0 0 0 4px rgba(251, 133, 0, 0.25), 0 6px 20px rgba(251, 133, 0, 0.2)' : '0 4px 14px rgba(251, 133, 0, 0.12)',
              position: 'relative',
              transition: 'all 0.2s ease'
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span style={{ fontSize: '18px' }}>⭐</span>
                  <div>
                    <div style={{ fontSize: '14px', fontWeight: 800, color: '#FB8500' }}>
                      {proInfo.name || 'PRO Tier'}
                    </div>
                    <div style={{ display: 'flex', alignItems: 'baseline', gap: '4px' }}>
                      <span style={{ fontSize: '17px', fontWeight: 800, color: '#FB8500' }}>
                        {proPricing.priceFormatted}
                      </span>
                      {proPricing.hasDiscount && (
                        <span style={{ fontSize: '10px', color: '#94A3B8', textDecoration: 'line-through', fontWeight: 600 }}>
                          ₱{proPricing.basePrice}
                        </span>
                      )}
                      <span style={{ fontSize: '9px', color: '#6B7280', fontWeight: 600 }}>/ {proInfo.billing_period || 'month'}</span>
                    </div>
                    {getPromoExpirationNotice(proInfo) && (
                      <div style={{
                        fontSize: '9px',
                        fontWeight: 700,
                        color: '#6B7280',
                        marginTop: '3px'
                      }}>
                        {getPromoExpirationNotice(proInfo)}
                      </div>
                    )}
                  </div>
                </div>

                {/* Promo Badge Pill */}
                <div style={{
                  backgroundColor: '#FFF3E0',
                  border: '1px solid #FB8500',
                  borderRadius: '10px',
                  padding: '2px 7px',
                  fontSize: '8.5px',
                  fontWeight: 800,
                  color: '#FB8500'
                }}>
                  {getEffectiveMobileBadge(proInfo)}
                </div>
              </div>

              <div style={{ fontSize: '9.5px', color: '#4B5563', marginTop: '6px' }}>
                {proInfo.description || 'Ideal for growing single & multi-branch retail stores.'}
              </div>

              <div style={{ height: '1px', backgroundColor: '#FED7AA', margin: '8px 0' }} />

              {/* Merchant Benefits */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', marginBottom: '12px' }}>
                {(proInfo.merchant_benefits || ["Up to 10 Stores", "500 items / store", "10 staff accounts"]).map((b, i) => {
                  const cleanText = b.replace(/^[^a-zA-Z0-9]+/, '').trim();
                  return (
                    <div key={i} style={{ display: 'flex', alignItems: 'center', gap: '5px', fontSize: '9.5px', color: '#1F2937' }}>
                      <div style={{ width: '11px', height: '11px', borderRadius: '50%', backgroundColor: '#FB8500', color: '#FFFFFF', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '6.5px', fontWeight: 900, flexShrink: 0 }}>✓</div>
                      <span>{cleanText}</span>
                    </div>
                  );
                })}
              </div>

              <button style={{
                width: '100%',
                padding: '8px',
                backgroundColor: '#FB8500',
                color: '#FFFFFF',
                border: 'none',
                borderRadius: '8px',
                fontWeight: 800,
                fontSize: '9.5px',
                boxShadow: '0 3px 8px rgba(251,133,0,0.25)',
                cursor: 'pointer'
              }}>
                {proInfo.cta_button_text || `UPGRADE TO PRO (${proPricing.priceFormatted}/MO)`}
              </button>
            </div>

            {/* 2. VIP TIER CARD */}
            <div style={{
              backgroundColor: '#FFFFFF',
              borderRadius: '14px',
              padding: '14px',
              marginBottom: '10px',
              border: activeEditId === 'vip' ? '3px solid #00897B' : '2px solid #219EBC',
              boxShadow: activeEditId === 'vip' ? '0 0 0 4px rgba(0, 137, 123, 0.25), 0 6px 20px rgba(0, 137, 123, 0.2)' : '0 4px 14px rgba(33, 158, 188, 0.12)',
              position: 'relative',
              transition: 'all 0.2s ease'
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span style={{ fontSize: '18px' }}>💎</span>
                  <div>
                    <div style={{ fontSize: '14px', fontWeight: 800, color: '#219EBC' }}>
                      {vipInfo.name || 'VIP Tier'}
                    </div>
                    <div style={{ display: 'flex', alignItems: 'baseline', gap: '4px' }}>
                      <span style={{ fontSize: '17px', fontWeight: 800, color: '#219EBC' }}>
                        {vipPricing.priceFormatted}
                      </span>
                      {vipPricing.hasDiscount && (
                        <span style={{ fontSize: '10px', color: '#94A3B8', textDecoration: 'line-through', fontWeight: 600 }}>
                          ₱{vipPricing.basePrice}
                        </span>
                      )}
                      <span style={{ fontSize: '9px', color: '#6B7280', fontWeight: 600 }}>/ {vipInfo.billing_period || 'month'}</span>
                    </div>
                    {getPromoExpirationNotice(vipInfo) && (
                      <div style={{
                        fontSize: '9px',
                        fontWeight: 700,
                        color: '#6B7280',
                        marginTop: '3px'
                      }}>
                        {getPromoExpirationNotice(vipInfo)}
                      </div>
                    )}
                  </div>
                </div>

                {/* Promo Badge Pill */}
                <div style={{
                  backgroundColor: '#E0F2FE',
                  border: '1px solid #219EBC',
                  borderRadius: '10px',
                  padding: '2px 7px',
                  fontSize: '8.5px',
                  fontWeight: 800,
                  color: '#219EBC'
                }}>
                  {getEffectiveMobileBadge(vipInfo)}
                </div>
              </div>

              <div style={{ fontSize: '9.5px', color: '#4B5563', marginTop: '6px' }}>
                {vipInfo.description || 'Ideal for high-volume businesses & enterprise managers.'}
              </div>

              <div style={{ height: '1px', backgroundColor: '#99F6E4', margin: '8px 0' }} />

              {/* Merchant Benefits */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', marginBottom: '12px' }}>
                {(vipInfo.merchant_benefits || ["Unlimited Stores", "Unlimited items", "Unlimited staff"]).map((b, i) => {
                  const cleanText = b.replace(/^[^a-zA-Z0-9]+/, '').trim();
                  return (
                    <div key={i} style={{ display: 'flex', alignItems: 'center', gap: '5px', fontSize: '9.5px', color: '#064E3B', fontWeight: 600 }}>
                      <div style={{ width: '11px', height: '11px', borderRadius: '50%', backgroundColor: '#219EBC', color: '#FFFFFF', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '6.5px', fontWeight: 900, flexShrink: 0 }}>✓</div>
                      <span>{cleanText}</span>
                    </div>
                  );
                })}
              </div>

              <button style={{
                width: '100%',
                padding: '8px',
                backgroundColor: '#219EBC',
                color: '#FFFFFF',
                border: 'none',
                borderRadius: '8px',
                fontWeight: 800,
                fontSize: '9.5px',
                boxShadow: '0 3px 8px rgba(33,158,188,0.25)',
                cursor: 'pointer'
              }}>
                {vipInfo.cta_button_text || `UPGRADE TO VIP (${vipPricing.priceFormatted}/MO)`}
              </button>
            </div>

            {/* 3. FREE TIER CARD (AT THE BOTTOM) */}
            <div style={{
              backgroundColor: '#FFFFFF',
              borderRadius: '14px',
              padding: '14px',
              marginBottom: '10px',
              border: activeEditId === 'free' ? '3px solid #64748B' : '1px solid #DDDDDD',
              boxShadow: activeEditId === 'free' ? '0 0 0 4px rgba(100, 116, 139, 0.25), 0 6px 20px rgba(0,0,0,0.1)' : '0 2px 6px rgba(0,0,0,0.02)',
              position: 'relative',
              transition: 'all 0.2s ease'
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div>
                  <div style={{ fontSize: '14px', fontWeight: 800, color: '#1F2937' }}>
                    {freeInfo.name || 'Free Plan'}
                  </div>
                  <div style={{ display: 'flex', alignItems: 'baseline', gap: '4px' }}>
                    <span style={{ fontSize: '17px', fontWeight: 800, color: '#FB8500' }}>
                      {freePricing.priceFormatted}
                    </span>
                    <span style={{ fontSize: '9px', color: '#6B7280' }}>/ forever</span>
                  </div>
                </div>

                {/* Promo Badge Pill */}
                <div style={{
                  backgroundColor: '#F1F5F9',
                  border: '1px solid #CBD5E1',
                  borderRadius: '10px',
                  padding: '2px 7px',
                  fontSize: '8.5px',
                  fontWeight: 800,
                  color: '#64748B'
                }}>
                  {freeInfo.promo_badge || 'BASIC PLAN'}
                </div>
              </div>

              <div style={{ fontSize: '9.5px', color: '#4B5563', marginTop: '6px' }}>
                {freeInfo.description || 'Ideal for micro sari-sari stores & single vendors.'}
              </div>

              <div style={{ height: '1px', backgroundColor: '#E5E7EB', margin: '8px 0' }} />

              {/* Merchant Benefits */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', marginBottom: '12px' }}>
                {(freeInfo.merchant_benefits || ["1 Store Branch", "50 Items / Store", "1 Staff Account"]).map((b, i) => {
                  const cleanText = b.replace(/^[^a-zA-Z0-9]+/, '').trim();
                  return (
                    <div key={i} style={{ display: 'flex', alignItems: 'center', gap: '5px', fontSize: '9.5px', color: '#374151' }}>
                      <div style={{ width: '11px', height: '11px', borderRadius: '50%', backgroundColor: '#6B7280', color: '#FFFFFF', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '6.5px', fontWeight: 900, flexShrink: 0 }}>✓</div>
                      <span>{cleanText}</span>
                    </div>
                  );
                })}
              </div>

              <button style={{
                width: '100%',
                padding: '8px',
                backgroundColor: 'transparent',
                color: '#FB8500',
                border: '1.5px solid #FB8500',
                borderRadius: '8px',
                fontWeight: 800,
                fontSize: '9.5px',
                cursor: 'pointer'
              }}>
                {freeInfo.cta_button_text || 'CURRENT ACTIVE PLAN'}
              </button>
            </div>

          </div>
        </div>
      </div>
    );
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

  // Calculate metrics
  const proCount = users.filter(u => u.subscription_tier === 'pro').length;
  const vipCount = users.filter(u => u.subscription_tier === 'vip').length;
  const proPrice = tiers.find(t => t.tier_id === 'pro')?.price || 100;
  const vipPrice = tiers.find(t => t.tier_id === 'vip')?.price || 299;
  const estimatedMRR = (proCount * proPrice) + (vipCount * vipPrice);

  return (
    <div style={{ fontFamily: "'Outfit', sans-serif" }}>
      {/* Header Banner */}
      <div style={{ 
        display: 'flex', 
        justify: 'space-between', 
        alignItems: 'center', 
        marginBottom: '24px',
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

      {/* Sleek Sub-Tab Navigation Bar & Mobile Preview Toggle */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px', flexWrap: 'wrap', gap: '16px' }}>
        <div style={{ 
          display: 'inline-flex', 
          gap: '6px', 
          backgroundColor: '#F1F5F9', 
          padding: '6px', 
          borderRadius: '16px', 
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

        {subTab === 'config' && (
          <button
            onClick={() => setShowMobilePreview(!showMobilePreview)}
            style={{
              padding: '10px 18px',
              borderRadius: '14px',
              fontWeight: 700,
              fontSize: '0.85rem',
              backgroundColor: showMobilePreview ? '#EFF6FF' : '#FFFFFF',
              color: showMobilePreview ? '#2563EB' : '#475569',
              border: showMobilePreview ? '1.5px solid #60A5FA' : '1px solid #E2E8F0',
              boxShadow: '0 2px 8px rgba(0,0,0,0.03)',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: '8px'
            }}
          >
            📱 {showMobilePreview ? 'Hide Mobile Live Preview' : 'Show Mobile Live Preview'}
          </button>
        )}
      </div>

      {/* TAB 1: PLAN CONFIGURATION */}
      {subTab === 'config' && (
        <div style={{ display: 'flex', gap: '28px', alignItems: 'flex-start', flexWrap: 'wrap' }}>
          {/* Main Tier Cards Grid */}
          <div style={{ flex: 1, minWidth: '320px' }}>
            <div style={{ 
              display: 'grid', 
              gridTemplateColumns: 'repeat(auto-fit, minmax(310px, 1fr))', 
              gap: '24px' 
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
                      padding: '24px 20px 20px 20px',
                      background: isVip 
                        ? 'linear-gradient(135deg, #E6FFFA 0%, #B2F5EA 100%)' 
                        : isPro 
                        ? 'linear-gradient(135deg, #FFF5EB 0%, #FFEDD5 100%)' 
                        : '#F8FAFC',
                      borderBottom: '1px solid rgba(0,0,0,0.06)'
                    }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
                        <span style={{ 
                          fontSize: '0.75rem', 
                          fontWeight: 800, 
                          letterSpacing: '0.5px',
                          textTransform: 'uppercase',
                          padding: '4px 10px',
                          borderRadius: '16px',
                          backgroundColor: isVip ? '#00897B' : isPro ? '#FB8500' : '#64748B',
                          color: '#FFFFFF',
                          boxShadow: isVip ? '0 4px 10px rgba(0,137,123,0.3)' : isPro ? '0 4px 10px rgba(251,133,0,0.3)' : 'none'
                        }}>
                          {isVip ? 'VIP TIER 💎' : isPro ? 'PRO TIER ⭐' : 'FREE TIER 🎁'}
                        </span>

                        {(() => {
                          const status = getPromoScheduleStatus(tier);
                          const active = isPromoActive(tier);
                          const badgeLabel = active 
                            ? (tier.promo_badge || (tier.discount_percent > 0 ? `${tier.discount_percent}% OFF` : tier.trial_days > 0 ? `${tier.trial_days}-Day Trial` : 'Active Promo'))
                            : (status.status === 'scheduled' ? status.label : status.status === 'expired' ? 'Expired Promo' : 'Standard Rates');
                          
                          return (
                            <span style={{ 
                              fontSize: '0.75rem', 
                              color: status.color, 
                              fontWeight: 800, 
                              backgroundColor: status.bg, 
                              padding: '4px 10px', 
                              borderRadius: '12px',
                              border: `1px solid ${status.color}`
                            }}>
                              {badgeLabel}
                            </span>
                          );
                        })()}
                      </div>

                      <h3 style={{ margin: '0 0 4px 0', fontSize: '1.35rem', fontWeight: 800, color: isVip ? '#064E3B' : isPro ? '#9A3412' : '#0F172A' }}>
                        {tier.name}
                      </h3>

                      <div style={{ display: 'flex', alignItems: 'baseline', gap: '6px', marginTop: '8px' }}>
                        {(() => {
                          const basePrice = tier.price || 0;
                          const discPct = tier.discount_percent || 0;
                          const manualPrice = tier.promo_price;
                          const autoPrice = discPct > 0 ? basePrice * (1 - discPct / 100) : basePrice;
                          const finalPrice = (manualPrice !== null && manualPrice !== undefined && manualPrice !== '') ? parseFloat(manualPrice) : autoPrice;
                          const hasDiscount = finalPrice < basePrice;

                          return (
                            <>
                              <span style={{ fontSize: '2.2rem', fontWeight: 900, color: isVip ? '#00897B' : isPro ? '#FB8500' : '#0F172A', lineHeight: 1 }}>
                                ₱{finalPrice % 1 === 0 ? finalPrice : finalPrice.toFixed(2)}
                              </span>
                              {hasDiscount && (
                                <span style={{ fontSize: '1.1rem', color: '#94A3B8', textDecoration: 'line-through', fontWeight: 600 }}>
                                  ₱{basePrice}
                                </span>
                              )}
                              <span style={{ fontSize: '0.85rem', color: '#64748B', fontWeight: 600 }}>
                                / {tier.billing_period}
                              </span>
                            </>
                          );
                        })()}
                      </div>

                      {getPromoExpirationNotice(tier) && (
                        <div style={{ fontSize: '0.8rem', fontWeight: 700, color: '#6B7280', marginTop: '6px' }}>
                          {getPromoExpirationNotice(tier)}
                        </div>
                      )}
                    </div>

                    {/* Limits & Feature List Body */}
                    <div style={{ padding: '20px' }}>
                      <div style={{ fontSize: '0.72rem', fontWeight: 800, color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '10px' }}>
                        Capacity Caps &amp; Limits
                      </div>

                      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px', marginBottom: '16px' }}>
                        <div style={{ backgroundColor: '#F8FAFC', padding: '10px', borderRadius: '12px', border: '1px solid #F1F5F9' }}>
                          <div style={{ fontSize: '0.7rem', color: '#64748B', fontWeight: 700 }}>STORES</div>
                          <div style={{ fontSize: '0.95rem', fontWeight: 800, color: '#0F172A', marginTop: '2px' }}>
                            {tier.max_stores > 999 ? 'Unlimited ∞' : `${tier.max_stores}`}
                          </div>
                        </div>

                        <div style={{ backgroundColor: '#F8FAFC', padding: '10px', borderRadius: '12px', border: '1px solid #F1F5F9' }}>
                          <div style={{ fontSize: '0.7rem', color: '#64748B', fontWeight: 700 }}>ITEMS / STORE</div>
                          <div style={{ fontSize: '0.95rem', fontWeight: 800, color: '#0F172A', marginTop: '2px' }}>
                            {tier.max_items_per_store > 9999 ? 'Unlimited ∞' : `${tier.max_items_per_store}`}
                          </div>
                        </div>

                        <div style={{ backgroundColor: '#F8FAFC', padding: '10px', borderRadius: '12px', border: '1px solid #F1F5F9' }}>
                          <div style={{ fontSize: '0.7rem', color: '#64748B', fontWeight: 700 }}>STAFF / STORE</div>
                          <div style={{ fontSize: '0.95rem', fontWeight: 800, color: '#0F172A', marginTop: '2px' }}>
                            {tier.max_staff_per_store > 9999 ? 'Unlimited ∞' : `${tier.max_staff_per_store}`}
                          </div>
                        </div>

                        <div style={{ backgroundColor: '#F8FAFC', padding: '10px', borderRadius: '12px', border: '1px solid #F1F5F9' }}>
                          <div style={{ fontSize: '0.7rem', color: '#64748B', fontWeight: 700 }}>DAILY AI PARSES</div>
                          <div style={{ fontSize: '0.95rem', fontWeight: 800, color: '#0F172A', marginTop: '2px' }}>
                            {tier.max_ai_parses_per_day} / day
                          </div>
                        </div>
                      </div>

                      {/* Merchant Benefits Preview */}
                      <div style={{ fontSize: '0.72rem', fontWeight: 800, color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '8px' }}>
                        Key Features &amp; Perks
                      </div>

                      <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginBottom: '16px' }}>
                        {(tier.merchant_benefits || []).map((b, i) => (
                          <div key={i} style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '0.8rem', color: '#334155', fontWeight: 500 }}>
                            <span style={{ color: isVip ? '#00897B' : isPro ? '#FB8500' : '#64748B', fontWeight: 800, flexShrink: 0 }}>✓</span>
                            <span>{b}</span>
                          </div>
                        ))}
                      </div>

                      <button
                        className="admin-btn-action"
                        style={{ 
                          width: '100%', 
                          padding: '12px', 
                          backgroundColor: isVip ? '#00897B' : isPro ? '#FB8500' : '#475569', 
                          color: '#FFFFFF', 
                          borderRadius: '12px',
                          fontWeight: 800,
                          fontSize: '0.85rem',
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
          </div>

          {/* Right Side: Interactive Mobile App Phone Screen Preview */}
          {showMobilePreview && !editingTier && (
            <div style={{ position: 'sticky', top: '20px' }}>
              <div style={{ fontSize: '0.8rem', fontWeight: 800, color: '#64748B', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '8px', textAlign: 'center' }}>
                📱 Mobile App Live Preview
              </div>
              {renderMobileAppPreview(tiers, null)}
            </div>
          )}

          {/* ⚡ 10X UX UPGRADED EDIT TIER MODAL WITH REAL-TIME LIVE MOBILE PREVIEW */}
          {editingTier && (
            <div style={{ 
              position: 'fixed', 
              top: 0, 
              left: 0, 
              right: 0, 
              bottom: 0, 
              backgroundColor: 'rgba(15, 23, 42, 0.8)', 
              backdropFilter: 'blur(12px)',
              display: 'flex', 
              alignItems: 'center', 
              justifyContent: 'center', 
              zIndex: 1000,
              padding: '24px'
            }}>
              <div style={{ 
                backgroundColor: '#FFFFFF', 
                borderRadius: '28px', 
                width: '1150px', 
                maxWidth: '96vw', 
                maxHeight: '92vh', 
                boxShadow: '0 30px 70px -15px rgba(0,0,0,0.4)',
                display: 'flex',
                flexDirection: 'column',
                overflow: 'hidden'
              }}>
                {/* Modal Top Header Bar */}
                <div style={{
                  padding: '20px 28px',
                  borderBottom: '1px solid #E2E8F0',
                  display: 'flex',
                  justify: 'space-between',
                  alignItems: 'center',
                  backgroundColor: '#F8FAFC'
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                    <span style={{ 
                      fontSize: '0.78rem', 
                      fontWeight: 800, 
                      padding: '5px 12px', 
                      borderRadius: '16px', 
                      backgroundColor: editingTier.tier_id === 'vip' ? '#00897B' : editingTier.tier_id === 'pro' ? '#FB8500' : '#64748B', 
                      color: '#FFFFFF' 
                    }}>
                      {editingTier.tier_id === 'vip' ? 'VIP TIER 💎' : editingTier.tier_id === 'pro' ? 'PRO TIER ⭐' : 'FREE TIER 🎁'}
                    </span>
                    <div>
                      <h3 style={{ margin: 0, fontSize: '1.3rem', color: '#0F172A', fontWeight: 800 }}>
                        Editing Plan Tier: <span style={{ color: editingTier.tier_id === 'vip' ? '#00897B' : '#FB8500' }}>{editingTier.name}</span>
                      </h3>
                      <div style={{ fontSize: '0.8rem', color: '#64748B', marginTop: '1px' }}>
                        Changes instantly update the mobile app live cards &amp; Supabase database.
                      </div>
                    </div>
                  </div>

                  <button 
                    onClick={() => setEditingTier(null)}
                    style={{ background: '#E2E8F0', border: 'none', width: '36px', height: '36px', borderRadius: '50%', fontSize: '1.1rem', cursor: 'pointer', color: '#475569', display: 'flex', alignItems: 'center', justifyContent: 'center', transition: 'all 0.2s ease' }}
                  >
                    ✕
                  </button>
                </div>

                {/* Modal Body: 2-Column Split View */}
                <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
                  {/* Left Column: Form Controls with Smooth Scroll */}
                  <div style={{ flex: 1, padding: '24px 28px', overflowY: 'auto', maxHeight: 'calc(92vh - 140px)' }}>
                    {/* ⚡ Modal Sub-Section Navigation Jump Bar */}
                    <div style={{
                      display: 'flex',
                      gap: '8px',
                      marginBottom: '18px',
                      padding: '6px',
                      backgroundColor: '#F1F5F9',
                      borderRadius: '16px',
                      overflowX: 'auto'
                    }}>
                      <a 
                        href="#modal-sec-pricing" 
                        style={{
                          padding: '8px 14px',
                          borderRadius: '12px',
                          fontSize: '0.78rem',
                          fontWeight: 800,
                          backgroundColor: '#FFFFFF',
                          color: '#0F172A',
                          textDecoration: 'none',
                          boxShadow: '0 2px 6px rgba(0,0,0,0.04)',
                          whiteSpace: 'nowrap'
                        }}
                      >
                        💳 Pricing &amp; Promos
                      </a>
                      <a 
                        href="#modal-sec-quotas" 
                        style={{
                          padding: '8px 14px',
                          borderRadius: '12px',
                          fontSize: '0.78rem',
                          fontWeight: 700,
                          backgroundColor: 'transparent',
                          color: '#64748B',
                          textDecoration: 'none',
                          whiteSpace: 'nowrap'
                        }}
                      >
                        🏢 Limits &amp; Quotas
                      </a>
                      <a 
                        href="#modal-sec-features" 
                        style={{
                          padding: '8px 14px',
                          borderRadius: '12px',
                          fontSize: '0.78rem',
                          fontWeight: 700,
                          backgroundColor: 'transparent',
                          color: '#64748B',
                          textDecoration: 'none',
                          whiteSpace: 'nowrap'
                        }}
                      >
                        ⚡ Feature Flags
                      </a>
                      <a 
                        href="#modal-sec-benefits" 
                        style={{
                          padding: '8px 14px',
                          borderRadius: '12px',
                          fontSize: '0.78rem',
                          fontWeight: 700,
                          backgroundColor: 'transparent',
                          color: '#64748B',
                          textDecoration: 'none',
                          whiteSpace: 'nowrap'
                        }}
                      >
                        📋 Benefit Bullets
                      </a>
                    </div>

                    {/* ⚡ 1-Click Quick Promo Presets Bar */}
                    <div style={{ 
                      background: 'linear-gradient(135deg, #FFF7ED 0%, #FFEDD5 100%)', 
                      padding: '16px 20px', 
                      borderRadius: '20px', 
                      marginBottom: '20px', 
                      border: '1.5px solid #FED7AA',
                      boxShadow: '0 4px 14px rgba(251, 133, 0, 0.06)'
                    }}>
                      <div style={{ fontSize: '0.75rem', fontWeight: 800, color: '#C2410C', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '10px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <span>⚡ 1-Click Quick Promo Presets</span>
                      </div>
                      <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                        <button
                          type="button"
                          onClick={() => applyPresetDiscount(10, '10% OFF')}
                          style={{ padding: '7px 14px', borderRadius: '12px', backgroundColor: '#FFFFFF', border: '1.5px solid #FDBA74', color: '#EA580C', fontWeight: 800, fontSize: '0.78rem', cursor: 'pointer', boxShadow: '0 2px 6px rgba(0,0,0,0.03)', transition: 'all 0.15s ease' }}
                        >
                          ⚡ 10% OFF
                        </button>
                        <button
                          type="button"
                          onClick={() => applyPresetDiscount(20, 'SAVE 20%')}
                          style={{ padding: '7px 14px', borderRadius: '12px', backgroundColor: '#FFFFFF', border: '1.5px solid #FDBA74', color: '#EA580C', fontWeight: 800, fontSize: '0.78rem', cursor: 'pointer', boxShadow: '0 2px 6px rgba(0,0,0,0.03)', transition: 'all 0.15s ease' }}
                        >
                          ⚡ 20% OFF
                        </button>
                        <button
                          type="button"
                          onClick={() => applyPresetDiscount(50, '50% OFF PROMO')}
                          style={{ padding: '7px 14px', borderRadius: '12px', backgroundColor: '#FFFFFF', border: '1.5px solid #FDBA74', color: '#EA580C', fontWeight: 900, fontSize: '0.78rem', cursor: 'pointer', boxShadow: '0 2px 6px rgba(0,0,0,0.03)', transition: 'all 0.15s ease' }}
                        >
                          🔥 50% OFF
                        </button>
                        <button
                          type="button"
                          onClick={() => setEditingTier({ ...editingTier, trial_days: 7, promo_badge: '7 DAYS FREE TRIAL' })}
                          style={{ padding: '7px 14px', borderRadius: '12px', backgroundColor: '#FFFFFF', border: '1.5px solid #FDBA74', color: '#D97706', fontWeight: 800, fontSize: '0.78rem', cursor: 'pointer', boxShadow: '0 2px 6px rgba(0,0,0,0.03)', transition: 'all 0.15s ease' }}
                        >
                          🎁 7-Day Free Trial
                        </button>
                        <button
                          type="button"
                          onClick={() => setEditingTier({ ...editingTier, discount_percent: 0, promo_price: null, promo_badge: '' })}
                          style={{ padding: '7px 14px', borderRadius: '12px', backgroundColor: '#FEF2F2', border: '1.5px solid #FCA5A5', color: '#DC2626', fontWeight: 800, fontSize: '0.78rem', cursor: 'pointer', transition: 'all 0.15s ease' }}
                        >
                          🔄 Clear Promo
                        </button>
                      </div>
                    </div>

                    <form id="tierEditForm" onSubmit={handleSaveTier}>
                      {/* Section 1: 💰 10X UX PRICING & HYBRID PROMO ENGINE */}
                      <div 
                        id="modal-sec-pricing"
                        style={{ 
                          backgroundColor: '#FFFFFF', 
                          padding: '24px', 
                          borderRadius: '24px', 
                          marginBottom: '24px', 
                          border: '1.5px solid #E2E8F0',
                          boxShadow: '0 8px 24px rgba(0,0,0,0.04)'
                        }}
                      >
                        <div style={{ 
                          display: 'flex', 
                          justify: 'space-between', 
                          alignItems: 'center', 
                          marginBottom: '20px',
                          paddingBottom: '14px',
                          borderBottom: '1px solid #F1F5F9'
                        }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                            <div style={{ width: '38px', height: '38px', borderRadius: '12px', background: 'linear-gradient(135deg, #FFF7ED 0%, #FFEDD5 100%)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '1.2rem', border: '1px solid #FED7AA' }}>
                              💰
                            </div>
                            <div>
                              <div style={{ fontSize: '1rem', fontWeight: 900, color: '#0F172A', letterSpacing: '-0.3px' }}>
                                Pricing &amp; Hybrid Promo Engine
                              </div>
                              <div style={{ fontSize: '0.78rem', color: '#64748B', marginTop: '1px' }}>
                                Manage original base rates, percentage discounts, manual price overrides, and promo CTA labels.
                              </div>
                            </div>
                          </div>

                          <span style={{ fontSize: '0.75rem', color: '#00897B', fontWeight: 800, backgroundColor: '#E6FFFA', padding: '6px 12px', borderRadius: '14px', border: '1px solid #B2F5EA' }}>
                            Billing: {editingTier.billing_period || 'month'}
                          </span>
                        </div>

                        {/* Sub-Card 1: Base Pricing & Trial */}
                        <div style={{ 
                          backgroundColor: '#F8FAFC', 
                          padding: '18px', 
                          borderRadius: '18px', 
                          marginBottom: '16px', 
                          border: '1.5px solid #E2E8F0'
                        }}>
                          <div style={{ fontSize: '0.75rem', fontWeight: 800, color: '#475569', marginBottom: '12px', textTransform: 'uppercase', letterSpacing: '0.5px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                            <span>🏷️ Base Pricing &amp; Trial Duration</span>
                          </div>

                          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                            <div>
                              <label style={{ fontSize: '0.8rem', fontWeight: 700, color: '#1E293B', display: 'block', marginBottom: '6px' }}>
                                Original Base Price (₱)
                              </label>
                              <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
                                <div style={{ position: 'absolute', left: '12px', fontWeight: 900, color: '#64748B', fontSize: '1rem', pointerEvents: 'none' }}>₱</div>
                                <input
                                  type="number"
                                  className="admin-search-input"
                                  style={{ 
                                    width: '100%', 
                                    paddingLeft: '32px', 
                                    fontWeight: 800, 
                                    fontSize: '1.1rem', 
                                    color: '#0F172A', 
                                    backgroundColor: '#FFFFFF', 
                                    border: '1.5px solid #CBD5E1', 
                                    borderRadius: '12px',
                                    height: '44px'
                                  }}
                                  value={editingTier.price}
                                  onChange={(e) => setEditingTier({ ...editingTier, price: parseFloat(e.target.value) || 0 })}
                                />
                              </div>
                            </div>

                            <div>
                              <label style={{ fontSize: '0.8rem', fontWeight: 700, color: '#1E293B', display: 'block', marginBottom: '6px' }}>
                                Trial Duration (Days)
                              </label>
                              <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
                                <input
                                  type="number"
                                  className="admin-search-input"
                                  style={{ 
                                    width: '100%', 
                                    paddingRight: '56px', 
                                    fontWeight: 800, 
                                    fontSize: '1.05rem', 
                                    color: '#0F172A', 
                                    backgroundColor: '#FFFFFF', 
                                    border: '1.5px solid #CBD5E1', 
                                    borderRadius: '12px',
                                    height: '44px'
                                  }}
                                  value={editingTier.trial_days}
                                  onChange={(e) => setEditingTier({ ...editingTier, trial_days: parseInt(e.target.value, 10) || 0 })}
                                />
                                <span style={{ position: 'absolute', right: '14px', fontWeight: 800, color: '#94A3B8', fontSize: '0.78rem', pointerEvents: 'none' }}>Days</span>
                              </div>
                            </div>
                          </div>
                        </div>

                        {/* Sub-Card 2: Hybrid Discount & Manual Price Override */}
                        <div style={{ 
                          background: 'linear-gradient(135deg, #EFF6FF 0%, #EEF2FF 100%)', 
                          padding: '18px', 
                          borderRadius: '18px', 
                          border: '1.5px solid #BFDBFE', 
                          marginBottom: '16px' 
                        }}>
                          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px', flexWrap: 'wrap', gap: '8px' }}>
                            <div style={{ fontSize: '0.78rem', fontWeight: 800, color: '#1E40AF', textTransform: 'uppercase', letterSpacing: '0.5px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                              <span>⚡ Hybrid Discount &amp; Manual Price Override</span>
                            </div>

                            {/* Active Effective Price Live Calculation Pill */}
                            {(() => {
                              const base = editingTier.price || 0;
                              const pct = editingTier.discount_percent || 0;
                              const manual = editingTier.promo_price;
                              const autoP = pct > 0 ? base * (1 - pct / 100) : base;
                              const finalP = (manual !== null && manual !== undefined && manual !== '') ? parseFloat(manual) : autoP;
                              const isDiscounted = finalP < base;

                              return (
                                <div style={{ 
                                  fontSize: '0.78rem', 
                                  color: isDiscounted ? '#15803D' : '#1E40AF', 
                                  fontWeight: 800, 
                                  backgroundColor: isDiscounted ? '#DCFCE7' : '#FFFFFF', 
                                  border: isDiscounted ? '1.5px solid #86EFAC' : '1.5px solid #93C5FD',
                                  padding: '5px 12px', 
                                  borderRadius: '12px',
                                  display: 'flex',
                                  alignItems: 'center',
                                  gap: '8px',
                                  boxShadow: '0 2px 6px rgba(0,0,0,0.03)'
                                }}>
                                  <span>Active Paywall Price:</span>
                                  <span style={{ fontSize: '1rem', fontWeight: 900 }}>₱{finalP % 1 === 0 ? finalP : finalP.toFixed(2)}</span>
                                  {isDiscounted && (
                                    <span style={{ fontSize: '0.68rem', backgroundColor: '#22C55E', color: '#FFFFFF', padding: '2px 6px', borderRadius: '6px', fontWeight: 900 }}>
                                      SAVINGS APPLIED
                                    </span>
                                  )}
                                </div>
                              );
                            })()}
                          </div>

                          {/* Live Calculation Formula Callout Card */}
                          {(() => {
                            const base = editingTier.price || 0;
                            const pct = editingTier.discount_percent || 0;
                            const manual = editingTier.promo_price;
                            const autoP = pct > 0 ? base * (1 - pct / 100) : base;
                            const finalP = (manual !== null && manual !== undefined && manual !== '') ? parseFloat(manual) : autoP;
                            const hasManual = (manual !== null && manual !== undefined && manual !== '');

                            return (
                              <div style={{ 
                                backgroundColor: '#FFFFFF', 
                                padding: '10px 14px', 
                                borderRadius: '12px', 
                                marginBottom: '14px',
                                border: '1px solid #DBEAFE',
                                fontSize: '0.78rem',
                                color: '#1E3A8A',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'space-between',
                                flexWrap: 'wrap',
                                gap: '8px'
                              }}>
                                <div>
                                  <span style={{ fontWeight: 800 }}>Formula Summary:</span>{' '}
                                  <span>Base ₱{base}</span>
                                  {pct > 0 && <span style={{ color: '#16A34A', fontWeight: 700 }}> - {pct}% Disc (-₱{(base * (pct / 100)).toFixed(2)})</span>}
                                  {hasManual && <span style={{ color: '#D97706', fontWeight: 800 }}> (Overridden by ₱{manual})</span>}
                                  <span> = </span>
                                  <span style={{ fontWeight: 900, color: '#15803D' }}>₱{finalP % 1 === 0 ? finalP : finalP.toFixed(2)} / {editingTier.billing_period || 'mo'}</span>
                                </div>
                                {hasManual && (
                                  <span style={{ backgroundColor: '#FEF3C7', color: '#B45309', border: '1px solid #FCD34D', padding: '2px 8px', borderRadius: '8px', fontSize: '0.7rem', fontWeight: 800 }}>
                                    ⚡ Manual Override Mode Active
                                  </span>
                                )}
                              </div>
                            );
                          })()}

                          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                            <div>
                              <label style={{ fontSize: '0.8rem', fontWeight: 700, color: '#1E3A8A', display: 'block', marginBottom: '6px' }}>
                                Discount Percentage (%)
                              </label>
                              <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
                                <input
                                  type="number"
                                  className="admin-search-input"
                                  placeholder="0"
                                  style={{ 
                                    width: '100%', 
                                    paddingRight: '36px', 
                                    fontWeight: 800, 
                                    fontSize: '1rem', 
                                    backgroundColor: '#FFFFFF', 
                                    border: '1.5px solid #93C5FD', 
                                    borderRadius: '12px', 
                                    color: '#1E3A8A',
                                    height: '44px'
                                  }}
                                  value={editingTier.discount_percent ?? ''}
                                  onChange={(e) => setEditingTier({ ...editingTier, discount_percent: parseFloat(e.target.value) || 0 })}
                                />
                                <span style={{ position: 'absolute', right: '14px', fontWeight: 900, color: '#3B82F6', fontSize: '0.9rem', pointerEvents: 'none' }}>%</span>
                              </div>
                            </div>

                            <div>
                              <label style={{ fontSize: '0.8rem', fontWeight: 700, color: '#1E3A8A', display: 'block', marginBottom: '6px' }}>
                                Manual Override Price (₱)
                              </label>
                              <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
                                <span style={{ position: 'absolute', left: '12px', fontWeight: 900, color: '#3B82F6', fontSize: '1rem', pointerEvents: 'none' }}>₱</span>
                                <input
                                  type="number"
                                  className="admin-search-input"
                                  placeholder="Optional price override..."
                                  style={{ 
                                    width: '100%', 
                                    paddingLeft: '32px', 
                                    fontWeight: 800, 
                                    fontSize: '1rem', 
                                    backgroundColor: '#FFFFFF', 
                                    border: '1.5px solid #93C5FD', 
                                    borderRadius: '12px', 
                                    color: '#1E3A8A',
                                    height: '44px'
                                  }}
                                  value={editingTier.promo_price ?? ''}
                                  onChange={(e) => setEditingTier({ ...editingTier, promo_price: e.target.value === '' ? null : parseFloat(e.target.value) })}
                                />
                              </div>
                            </div>
                          </div>
                        </div>

                        {/* Sub-Card 3: Custom Promo Badge & Custom CTA Label */}
                        <div style={{ 
                          background: 'linear-gradient(135deg, #FFF7ED 0%, #FFEDD5 100%)', 
                          padding: '18px', 
                          borderRadius: '18px', 
                          border: '1.5px solid #FFEDD5' 
                        }}>
                          {/* Custom Promo Badge Text */}
                          <div style={{ marginBottom: '18px' }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
                              <label style={{ fontSize: '0.8rem', fontWeight: 800, color: '#C2410C' }}>
                                Custom Promo Badge Text
                              </label>

                              {/* LIVE BADGE PREVIEW CHIP */}
                              <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                <span style={{ fontSize: '0.7rem', color: '#9A3412', fontWeight: 700 }}>Badge Preview:</span>
                                <span style={{ 
                                  padding: '3px 9px', 
                                  backgroundColor: editingTier.tier_id === 'vip' ? '#E0F2FE' : '#FFF3E0',
                                  color: editingTier.tier_id === 'vip' ? '#219EBC' : '#FB8500',
                                  border: editingTier.tier_id === 'vip' ? '1px solid #219EBC' : '1px solid #FB8500',
                                  borderRadius: '8px',
                                  fontSize: '0.72rem',
                                  fontWeight: 900
                                }}>
                                  {editingTier.promo_badge || 'NONE'}
                                </span>
                              </div>
                            </div>

                            <input
                              type="text"
                              className="admin-search-input"
                              placeholder="e.g. '50% OFF', 'SAVE 50%', or '7 DAYS TRIAL'"
                              style={{ 
                                width: '100%', 
                                fontWeight: 800, 
                                fontSize: '0.95rem', 
                                color: '#EA580C', 
                                backgroundColor: '#FFFFFF', 
                                border: '1.5px solid #FDBA74', 
                                borderRadius: '12px',
                                height: '42px',
                                paddingLeft: '14px'
                              }}
                              value={editingTier.promo_badge ?? ''}
                              onChange={(e) => setEditingTier({ ...editingTier, promo_badge: e.target.value })}
                            />

                            <div style={{ display: 'flex', gap: '6px', marginTop: '8px', flexWrap: 'wrap', alignItems: 'center' }}>
                              <span style={{ fontSize: '0.72rem', color: '#9A3412', fontWeight: 700 }}>Quick tags:</span>
                              {['50% OFF', 'SAVE 50%', '7 DAYS TRIAL', 'BEST VALUE', 'HOLIDAY PROMO'].map((tag) => {
                                const isActive = editingTier.promo_badge === tag;
                                return (
                                  <button
                                    key={tag}
                                    type="button"
                                    onClick={() => setEditingTier({ ...editingTier, promo_badge: tag })}
                                    style={{ 
                                      background: isActive ? '#EA580C' : '#FFFFFF', 
                                      border: '1.5px solid #FDBA74', 
                                      borderRadius: '10px', 
                                      fontSize: '0.72rem', 
                                      padding: '4px 10px', 
                                      color: isActive ? '#FFFFFF' : '#EA580C', 
                                      cursor: 'pointer', 
                                      fontWeight: 800,
                                      transition: 'all 0.15s ease',
                                      boxShadow: isActive ? '0 2px 8px rgba(234, 88, 12, 0.25)' : 'none'
                                    }}
                                  >
                                    {isActive ? '✓ ' : '+ '}{tag}
                                  </button>
                                );
                              })}
                            </div>
                          </div>

                          {/* Custom CTA Button Label Override */}
                          <div>
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px', flexWrap: 'wrap', gap: '6px' }}>
                              <label style={{ fontSize: '0.8rem', fontWeight: 800, color: '#C2410C', display: 'flex', alignItems: 'center', gap: '6px' }}>
                                <span>👉 Custom CTA Button Label Override</span>
                                <span style={{ backgroundColor: '#EA580C', color: '#FFFFFF', fontSize: '0.62rem', padding: '1px 5px', borderRadius: '5px', fontWeight: 900 }}>[NEW]</span>
                              </label>
                            </div>

                            {/* 3D LIVE MOBILE CTA BUTTON PREVIEW CARD */}
                            {(() => {
                              const baseP = editingTier.price || 0;
                              const discP = editingTier.discount_percent || 0;
                              const manualP = editingTier.promo_price;
                              const autoP = discP > 0 ? baseP * (1 - discP / 100) : baseP;
                              const finalP = (manualP !== null && manualP !== undefined && manualP !== '') ? parseFloat(manualP) : autoP;
                              const formattedPrice = finalP % 1 === 0 ? `₱${finalP}` : `₱${finalP.toFixed(2)}`;
                              const defaultCtaText = editingTier.tier_id === 'vip' 
                                ? `UPGRADE TO VIP (${formattedPrice}/MO)` 
                                : editingTier.tier_id === 'pro' 
                                ? `UPGRADE TO PRO (${formattedPrice}/MO)` 
                                : 'CURRENT ACTIVE PLAN';
                              const activeCtaText = editingTier.cta_button_text?.trim() ? editingTier.cta_button_text : defaultCtaText;
                              const isPro = editingTier.tier_id === 'pro';
                              const isVip = editingTier.tier_id === 'vip';

                              return (
                                <div style={{ 
                                  backgroundColor: '#FFFFFF', 
                                  padding: '12px 16px', 
                                  borderRadius: '14px', 
                                  marginBottom: '12px',
                                  border: '1px solid #FDBA74',
                                  boxShadow: '0 4px 12px rgba(251, 133, 0, 0.08)'
                                }}>
                                  <div style={{ fontSize: '0.7rem', color: '#9A3412', fontWeight: 800, textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '6px' }}>
                                    📱 Live 3D Mobile Button Component Preview:
                                  </div>
                                  <div style={{
                                    width: '100%',
                                    padding: '11px 16px',
                                    backgroundColor: isVip ? '#219EBC' : isPro ? '#FB8500' : 'transparent',
                                    color: isVip || isPro ? '#FFFFFF' : '#FB8500',
                                    border: isVip || isPro ? 'none' : '1.5px solid #FB8500',
                                    borderRadius: '12px',
                                    fontWeight: 900,
                                    fontSize: '0.88rem',
                                    textAlign: 'center',
                                    boxShadow: isVip ? '0 6px 16px rgba(33, 158, 188, 0.3)' : isPro ? '0 6px 16px rgba(251, 133, 0, 0.3)' : 'none',
                                    letterSpacing: '0.2px',
                                    transition: 'all 0.2s ease'
                                  }}>
                                    {activeCtaText}
                                  </div>
                                </div>
                              );
                            })()}

                            <input
                              type="text"
                              className="admin-search-input"
                              placeholder="e.g. 'Proceed 7 days Trial', 'CLAIM PRO DEAL', or 'GET STARTED'"
                              style={{ 
                                width: '100%', 
                                fontWeight: 800, 
                                fontSize: '0.95rem', 
                                color: '#0F172A', 
                                backgroundColor: '#FFFFFF', 
                                border: '1.5px solid #FDBA74', 
                                borderRadius: '12px',
                                padding: '12px 14px'
                              }}
                              value={editingTier.cta_button_text ?? ''}
                              onChange={(e) => setEditingTier({ ...editingTier, cta_button_text: e.target.value })}
                            />

                            <div style={{ display: 'flex', gap: '6px', marginTop: '10px', flexWrap: 'wrap', alignItems: 'center' }}>
                              <span style={{ fontSize: '0.72rem', color: '#9A3412', fontWeight: 700 }}>CTA presets:</span>
                              {['Proceed 7 days Trial', 'UPGRADE TO PRO', 'CLAIM VIP DEAL', 'GET STARTED FREE'].map((cta) => {
                                const isActive = editingTier.cta_button_text === cta;
                                return (
                                  <button
                                    key={cta}
                                    type="button"
                                    onClick={() => setEditingTier({ ...editingTier, cta_button_text: cta })}
                                    style={{ 
                                      background: isActive ? '#EA580C' : '#FFFFFF', 
                                      border: '1.5px solid #FDBA74', 
                                      borderRadius: '10px', 
                                      fontSize: '0.72rem', 
                                      padding: '4px 10px', 
                                      color: isActive ? '#FFFFFF' : '#C2410C', 
                                      cursor: 'pointer', 
                                      fontWeight: 800,
                                      transition: 'all 0.15s ease',
                                      boxShadow: isActive ? '0 2px 8px rgba(234, 88, 12, 0.25)' : 'none'
                                    }}
                                  >
                                    {isActive ? '✓ ' : '+ '}{cta}
                                  </button>
                                );
                              })}
                              {editingTier.cta_button_text && (
                                <button
                                  type="button"
                                  onClick={() => setEditingTier({ ...editingTier, cta_button_text: '' })}
                                  style={{ background: '#FEF2F2', border: '1.5px solid #FCA5A5', borderRadius: '10px', fontSize: '0.72rem', padding: '4px 10px', color: '#DC2626', cursor: 'pointer', fontWeight: 800 }}
                                >
                                  Reset CTA to Default
                                </button>
                              )}
                            </div>
                          </div>
                        </div>

                        {/* Sub-Card 4: Scheduled Promo Timeframe (Start & Expiration) */}
                        <div style={{ 
                          background: 'linear-gradient(135deg, #F0FDF4 0%, #DCFCE7 100%)', 
                          padding: '18px', 
                          borderRadius: '18px', 
                          border: '1.5px solid #86EFAC',
                          marginTop: '18px'
                        }}>
                          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px', flexWrap: 'wrap', gap: '8px' }}>
                            <div style={{ fontSize: '0.78rem', fontWeight: 800, color: '#166534', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                              Scheduled Promo Timeframe (Start &amp; Expiration)
                            </div>

                            {/* Live Schedule Status Pill */}
                            {(() => {
                              const status = getPromoScheduleStatus(editingTier);
                              return (
                                <span style={{ 
                                  fontSize: '0.75rem', 
                                  fontWeight: 800, 
                                  color: status.color, 
                                  backgroundColor: status.bg, 
                                  border: `1px solid ${status.color}`, 
                                  padding: '4px 10px', 
                                  borderRadius: '12px' 
                                }}>
                                  {status.label}
                                </span>
                              );
                            })()}
                          </div>

                          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px', marginBottom: '14px' }}>
                            <div>
                              <label style={{ fontSize: '0.8rem', fontWeight: 700, color: '#14532D', display: 'block', marginBottom: '6px' }}>
                                Promo Start Date &amp; Time (Optional)
                              </label>
                              <input
                                type="datetime-local"
                                className="admin-search-input"
                                style={{ 
                                  width: '100%', 
                                  fontWeight: 700, 
                                  fontSize: '0.9rem', 
                                  color: '#14532D', 
                                  backgroundColor: '#FFFFFF', 
                                  border: '1.5px solid #86EFAC', 
                                  borderRadius: '12px',
                                  height: '42px',
                                  padding: '8px 12px'
                                }}
                                value={toDatetimeLocal(editingTier.promo_start_at)}
                                onChange={(e) => setEditingTier({ ...editingTier, promo_start_at: e.target.value ? new Date(e.target.value).toISOString() : null })}
                              />
                            </div>

                            <div>
                              <label style={{ fontSize: '0.8rem', fontWeight: 700, color: '#14532D', display: 'block', marginBottom: '6px' }}>
                                Promo End Date &amp; Time (Optional Expiration)
                              </label>
                              <input
                                type="datetime-local"
                                className="admin-search-input"
                                style={{ 
                                  width: '100%', 
                                  fontWeight: 700, 
                                  fontSize: '0.9rem', 
                                  color: '#14532D', 
                                  backgroundColor: '#FFFFFF', 
                                  border: '1.5px solid #86EFAC', 
                                  borderRadius: '12px',
                                  height: '42px',
                                  padding: '8px 12px'
                                }}
                                value={toDatetimeLocal(editingTier.promo_end_at)}
                                onChange={(e) => setEditingTier({ ...editingTier, promo_end_at: e.target.value ? new Date(e.target.value).toISOString() : null })}
                              />
                            </div>
                          </div>

                          {/* Quick Schedule Presets */}
                          <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', alignItems: 'center' }}>
                            <span style={{ fontSize: '0.72rem', color: '#166534', fontWeight: 700 }}>Quick Schedule Presets:</span>
                            <button
                              type="button"
                              onClick={setSchedule7Days}
                              style={{ padding: '6px 12px', borderRadius: '10px', backgroundColor: '#FFFFFF', border: '1.5px solid #86EFAC', color: '#15803D', fontWeight: 800, fontSize: '0.75rem', cursor: 'pointer' }}
                            >
                              +7 Days
                            </button>
                            <button
                              type="button"
                              onClick={setSchedule30Days}
                              style={{ padding: '6px 12px', borderRadius: '10px', backgroundColor: '#FFFFFF', border: '1.5px solid #86EFAC', color: '#15803D', fontWeight: 800, fontSize: '0.75rem', cursor: 'pointer' }}
                            >
                              +30 Days
                            </button>
                            <button
                              type="button"
                              onClick={setScheduleEndOfMonth}
                              style={{ padding: '6px 12px', borderRadius: '10px', backgroundColor: '#FFFFFF', border: '1.5px solid #86EFAC', color: '#15803D', fontWeight: 800, fontSize: '0.75rem', cursor: 'pointer' }}
                            >
                              End of Current Month
                            </button>
                            <button
                              type="button"
                              onClick={clearPromoSchedule}
                              style={{ padding: '6px 12px', borderRadius: '10px', backgroundColor: '#FEF2F2', border: '1.5px solid #FCA5A5', color: '#DC2626', fontWeight: 800, fontSize: '0.75rem', cursor: 'pointer' }}
                            >
                              Clear Schedule / Permanent
                            </button>
                          </div>

                          {/* Custom Expiry Notice Label Override (Pure Standing Text) */}
                          <div style={{ marginTop: '16px', paddingTop: '14px', borderTop: '1px solid #BBF7D0' }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px', flexWrap: 'wrap', gap: '6px' }}>
                              <label style={{ fontSize: '0.8rem', fontWeight: 800, color: '#166534' }}>
                                Custom Expiration Notice Label Override
                              </label>

                              {getPromoExpirationNotice(editingTier) && (
                                <div style={{ fontSize: '0.72rem', color: '#15803D', fontWeight: 800 }}>
                                  Active Display: <span style={{ color: '#6B7280', fontWeight: 800 }}>"{getPromoExpirationNotice(editingTier)}"</span>
                                </div>
                              )}
                            </div>

                            <input
                              type="text"
                              className="admin-search-input"
                              placeholder="e.g. 'Special offer valid until Nov 30, 2026' or 'Offer ends soon'"
                              style={{ 
                                width: '100%', 
                                fontWeight: 800, 
                                fontSize: '0.95rem', 
                                color: '#14532D', 
                                backgroundColor: '#FFFFFF', 
                                border: '1.5px solid #86EFAC', 
                                borderRadius: '12px',
                                height: '42px',
                                paddingLeft: '14px'
                              }}
                              value={editingTier.promo_expiry_label ?? ''}
                              onChange={(e) => setEditingTier({ ...editingTier, promo_expiry_label: e.target.value })}
                            />

                            {/* Expiry Label Presets */}
                            <div style={{ display: 'flex', gap: '6px', marginTop: '8px', flexWrap: 'wrap', alignItems: 'center' }}>
                              <span style={{ fontSize: '0.72rem', color: '#166534', fontWeight: 700 }}>Quick Presets:</span>
                              {(() => {
                                const autoDateLabel = editingTier.promo_end_at 
                                  ? `Special offer valid until ${new Date(editingTier.promo_end_at).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}`
                                  : 'Special offer valid until Dec 31, 2026';
                                
                                const presets = [
                                  autoDateLabel,
                                  'Limited Time Offer',
                                  'Valid Until Supplies Last',
                                  'Ends End of Month'
                                ];

                                return presets.map((label) => {
                                  const isActive = editingTier.promo_expiry_label === label;
                                  return (
                                    <button
                                      key={label}
                                      type="button"
                                      onClick={() => setEditingTier({ ...editingTier, promo_expiry_label: label })}
                                      style={{ 
                                        background: isActive ? '#15803D' : '#FFFFFF', 
                                        border: '1.5px solid #86EFAC', 
                                        borderRadius: '10px', 
                                        fontSize: '0.72rem', 
                                        padding: '4px 10px', 
                                        color: isActive ? '#FFFFFF' : '#15803D', 
                                        cursor: 'pointer', 
                                        fontWeight: 800,
                                        transition: 'all 0.15s ease',
                                        boxShadow: isActive ? '0 2px 8px rgba(21, 128, 61, 0.25)' : 'none'
                                      }}
                                    >
                                      {isActive ? '✓ ' : '+ '}{label}
                                    </button>
                                  );
                                });
                              })()}

                              {editingTier.promo_expiry_label && (
                                <button
                                  type="button"
                                  onClick={() => setEditingTier({ ...editingTier, promo_expiry_label: '' })}
                                  style={{ background: '#FEF2F2', border: '1.5px solid #FCA5A5', borderRadius: '10px', fontSize: '0.72rem', padding: '4px 10px', color: '#DC2626', cursor: 'pointer', fontWeight: 800 }}
                                >
                                  Reset Expiry Text
                                </button>
                              )}
                            </div>
                          </div>
                        </div>
                      </div>

                      {/* Section 2: 🏢 CAPACITY CAPS & QUOTAS */}
                      <div 
                        id="modal-sec-quotas"
                        style={{ 
                          backgroundColor: '#FFFFFF', 
                          padding: '22px', 
                          borderRadius: '24px', 
                          marginBottom: '24px', 
                          border: '1.5px solid #E2E8F0',
                          boxShadow: '0 8px 24px rgba(0,0,0,0.04)'
                        }}
                      >
                        <div style={{ fontSize: '0.95rem', fontWeight: 900, color: '#0F172A', marginBottom: '16px', textTransform: 'uppercase', letterSpacing: '0.5px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                          <span>🏢 Merchant Capacity Caps &amp; AI Quotas</span>
                        </div>

                        {/* Sub-Grid 1: Store & Staff Limits */}
                        <div style={{ backgroundColor: '#F8FAFC', padding: '16px', borderRadius: '16px', marginBottom: '14px', border: '1px solid #E2E8F0' }}>
                          <div style={{ fontSize: '0.75rem', fontWeight: 800, color: '#475569', marginBottom: '10px', textTransform: 'uppercase' }}>
                            🏬 Store &amp; Staff Capacities
                          </div>
                          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px', marginBottom: '12px' }}>
                            <div>
                              <label style={{ fontSize: '0.78rem', fontWeight: 700, color: '#475569' }}>Owned Store Limit</label>
                              <input
                                type="number"
                                className="admin-search-input"
                                style={{ width: '100%', marginTop: '4px', fontWeight: 800, borderRadius: '10px' }}
                                value={editingTier.max_stores}
                                onChange={(e) => setEditingTier({ ...editingTier, max_stores: parseInt(e.target.value, 10) || 1 })}
                              />
                            </div>
                            <div>
                              <label style={{ fontSize: '0.78rem', fontWeight: 700, color: '#475569' }}>Staff Limit / Store</label>
                              <input
                                type="number"
                                className="admin-search-input"
                                style={{ width: '100%', marginTop: '4px', fontWeight: 800, borderRadius: '10px' }}
                                value={editingTier.max_staff_per_store}
                                onChange={(e) => setEditingTier({ ...editingTier, max_staff_per_store: parseInt(e.target.value, 10) || 1 })}
                              />
                            </div>
                          </div>

                          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
                            <div>
                              <label style={{ fontSize: '0.78rem', fontWeight: 700, color: '#475569' }}>Category Limit / Store</label>
                              <input
                                type="number"
                                className="admin-search-input"
                                style={{ width: '100%', marginTop: '4px', fontWeight: 800, borderRadius: '10px' }}
                                value={editingTier.max_categories_per_store ?? 10}
                                onChange={(e) => setEditingTier({ ...editingTier, max_categories_per_store: parseInt(e.target.value, 10) || 10 })}
                              />
                            </div>
                            <div>
                              <label style={{ fontSize: '0.78rem', fontWeight: 700, color: '#475569' }}>Items Limit / Store</label>
                              <input
                                type="number"
                                className="admin-search-input"
                                style={{ width: '100%', marginTop: '4px', fontWeight: 800, borderRadius: '10px' }}
                                value={editingTier.max_items_per_store}
                                onChange={(e) => setEditingTier({ ...editingTier, max_items_per_store: parseInt(e.target.value, 10) || 100 })}
                              />
                            </div>
                          </div>
                        </div>

                        {/* Sub-Grid 2: AI & Image Parser Quotas */}
                        <div style={{ backgroundColor: '#F8FAFC', padding: '16px', borderRadius: '16px', marginBottom: '14px', border: '1px solid #E2E8F0' }}>
                          <div style={{ fontSize: '0.75rem', fontWeight: 800, color: '#475569', marginBottom: '10px', textTransform: 'uppercase' }}>
                            🤖 AI &amp; Photo Parser Daily Quotas
                          </div>
                          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
                            <div>
                              <label style={{ fontSize: '0.78rem', fontWeight: 700, color: '#475569' }}>Daily AI Parser Quota (/day)</label>
                              <input
                                type="number"
                                className="admin-search-input"
                                style={{ width: '100%', marginTop: '4px', fontWeight: 800, borderRadius: '10px' }}
                                value={editingTier.max_ai_parses_per_day}
                                onChange={(e) => setEditingTier({ ...editingTier, max_ai_parses_per_day: parseInt(e.target.value, 10) || 3 })}
                              />
                            </div>
                            <div>
                              <label style={{ fontSize: '0.78rem', fontWeight: 700, color: '#475569' }}>Photo Scans Quota (/day)</label>
                              <input
                                type="number"
                                className="admin-search-input"
                                style={{ width: '100%', marginTop: '4px', fontWeight: 800, borderRadius: '10px' }}
                                value={editingTier.max_photo_scans_per_day ?? 3}
                                onChange={(e) => setEditingTier({ ...editingTier, max_photo_scans_per_day: parseInt(e.target.value, 10) || 3 })}
                              />
                            </div>
                          </div>
                        </div>

                        {/* Sub-Grid 3: Customer & Suki Quotas */}
                        <div style={{ backgroundColor: '#F8FAFC', padding: '16px', borderRadius: '16px', border: '1px solid #E2E8F0' }}>
                          <div style={{ fontSize: '0.75rem', fontWeight: 800, color: '#2563EB', marginBottom: '10px', textTransform: 'uppercase' }}>
                            👥 Customer &amp; Suki Account Quotas
                          </div>
                          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '12px' }}>
                            <div>
                              <label style={{ fontSize: '0.75rem', fontWeight: 700, color: '#475569' }}>Suking Tindahan</label>
                              <input
                                type="number"
                                className="admin-search-input"
                                style={{ width: '100%', marginTop: '4px', fontWeight: 800, borderRadius: '10px' }}
                                value={editingTier.max_suki_partners ?? 5}
                                onChange={(e) => setEditingTier({ ...editingTier, max_suki_partners: parseInt(e.target.value, 10) || 5 })}
                              />
                            </div>
                            <div>
                              <label style={{ fontSize: '0.75rem', fontWeight: 700, color: '#475569' }}>Presyohan Store</label>
                              <input
                                type="number"
                                className="admin-search-input"
                                style={{ width: '100%', marginTop: '4px', fontWeight: 800, borderRadius: '10px' }}
                                value={editingTier.max_presyohan_stores ?? 5}
                                onChange={(e) => setEditingTier({ ...editingTier, max_presyohan_stores: parseInt(e.target.value, 10) || 5 })}
                              />
                            </div>
                            <div>
                              <label style={{ fontSize: '0.75rem', fontWeight: 700, color: '#475569' }}>Internet Search (/day)</label>
                              <input
                                type="number"
                                className="admin-search-input"
                                style={{ width: '100%', marginTop: '4px', fontWeight: 800, borderRadius: '10px' }}
                                value={editingTier.max_internet_searches_per_day ?? 3}
                                onChange={(e) => setEditingTier({ ...editingTier, max_internet_searches_per_day: parseInt(e.target.value, 10) || 3 })}
                              />
                            </div>
                          </div>
                        </div>
                      </div>

                      {/* Section 3: ⚡ FEATURE FLAGS & CAPABILITIES */}
                      <div 
                        id="modal-sec-features"
                        style={{ 
                          backgroundColor: '#FFFFFF', 
                          padding: '22px', 
                          borderRadius: '24px', 
                          marginBottom: '24px', 
                          border: '1.5px solid #E2E8F0',
                          boxShadow: '0 8px 24px rgba(0,0,0,0.04)'
                        }}
                      >
                        <div style={{ fontSize: '0.95rem', fontWeight: 900, color: '#0F172A', marginBottom: '16px', textTransform: 'uppercase', letterSpacing: '0.5px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                          <span>⚡ Feature Flags &amp; Capabilities</span>
                        </div>
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                          {[
                            { key: 'has_price_cloning', label: '📋 Store Items Cloning' },
                            { key: 'has_customer_pairing', label: '🤝 Customer Pairing' },
                            { key: 'has_excel_export', label: '📊 Convert to Excel' },
                            { key: 'has_pdf_export', label: '📄 Convert to PDF' },
                            { key: 'has_notes_export', label: '📝 Convert as Notes' },
                            { key: 'has_priority_support', label: '👑 24/7 VIP Support' }
                          ].map(item => {
                            const isChecked = !!editingTier[item.key];
                            return (
                              <label 
                                key={item.key}
                                style={{ 
                                  display: 'flex', 
                                  alignItems: 'center', 
                                  justify: 'space-between', 
                                  backgroundColor: isChecked ? '#ECFDF5' : '#F8FAFC', 
                                  border: isChecked ? '1.5px solid #10B981' : '1px solid #E2E8F0',
                                  padding: '12px 16px', 
                                  borderRadius: '14px', 
                                  cursor: 'pointer',
                                  fontWeight: 800,
                                  fontSize: '0.85rem',
                                  color: isChecked ? '#047857' : '#475569',
                                  transition: 'all 0.2s ease',
                                  boxShadow: isChecked ? '0 4px 12px rgba(16, 185, 129, 0.08)' : 'none'
                                }}
                              >
                                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                  <span>{item.label}</span>
                                </div>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                  <span style={{ 
                                    fontSize: '0.65rem', 
                                    padding: '2px 6px', 
                                    borderRadius: '6px', 
                                    backgroundColor: isChecked ? '#10B981' : '#CBD5E1', 
                                    color: '#FFFFFF',
                                    fontWeight: 900
                                  }}>
                                    {isChecked ? 'ENABLED' : 'OFF'}
                                  </span>
                                  <input
                                    type="checkbox"
                                    checked={isChecked}
                                    onChange={(e) => setEditingTier({ ...editingTier, [item.key]: e.target.checked })}
                                    style={{ width: '18px', height: '18px', accentColor: '#10B981', cursor: 'pointer' }}
                                  />
                                </div>
                              </label>
                            );
                          })}
                        </div>
                      </div>

                      {/* Section 4: 📋 MOBILE PAYWALL BENEFIT BULLET POINTS (DRAGGABLE & REORDERABLE) */}
                      <div 
                        id="modal-sec-benefits"
                        style={{ 
                          backgroundColor: '#FFF7ED', 
                          padding: '22px', 
                          borderRadius: '24px', 
                          marginBottom: '24px', 
                          border: '1.5px solid #FFEDD5',
                          boxShadow: '0 8px 24px rgba(251, 133, 0, 0.05)'
                        }}
                      >
                        <div style={{ fontSize: '0.95rem', fontWeight: 900, color: '#C2410C', marginBottom: '14px', textTransform: 'uppercase', letterSpacing: '0.5px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                          <span>📋 Mobile Paywall Benefit Bullets</span>
                          <span style={{ fontSize: '0.75rem', color: '#EA580C', fontWeight: 800 }}>
                            🖐️ Drag &amp; Drop or ⬆️⬇️ to Re-order
                          </span>
                        </div>

                        {/* Quick Preset Benefits Bar */}
                        <div style={{ backgroundColor: '#FFFFFF', padding: '12px 14px', borderRadius: '14px', marginBottom: '14px', border: '1px solid #FED7AA' }}>
                          <div style={{ fontSize: '0.7rem', color: '#9A3412', fontWeight: 800, textTransform: 'uppercase', marginBottom: '6px' }}>
                            ⚡ Quick Add Common Benefits:
                          </div>
                          <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
                            {[
                              'Unlimited Price Catalog Exports',
                              '24/7 VIP Priority Phone Support',
                              'Store Items & Price Cloning',
                              'Multi-Branch Inventory Sync',
                              'Customer Pairing & Ledger'
                            ].map(presetBenefit => (
                              <button
                                key={presetBenefit}
                                type="button"
                                onClick={() => {
                                  if ((editingTier.merchant_benefits || []).includes(presetBenefit)) return;
                                  setEditingTier({
                                    ...editingTier,
                                    merchant_benefits: [...(editingTier.merchant_benefits || []), presetBenefit]
                                  });
                                }}
                                style={{
                                  padding: '4px 10px',
                                  borderRadius: '8px',
                                  backgroundColor: '#FFF7ED',
                                  border: '1px solid #FDBA74',
                                  color: '#EA580C',
                                  fontWeight: 800,
                                  fontSize: '0.72rem',
                                  cursor: 'pointer',
                                  transition: 'all 0.15s ease'
                                }}
                              >
                                + {presetBenefit}
                              </button>
                            ))}
                          </div>
                        </div>

                        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: '14px' }}>
                          {(editingTier.merchant_benefits || []).map((b, idx, arr) => (
                            <div 
                              key={idx}
                              draggable
                              onDragStart={(e) => {
                                e.dataTransfer.setData('text/plain', idx);
                                e.currentTarget.style.opacity = '0.5';
                              }}
                              onDragEnd={(e) => {
                                e.currentTarget.style.opacity = '1';
                              }}
                              onDragOver={(e) => e.preventDefault()}
                              onDrop={(e) => {
                                e.preventDefault();
                                const fromIdx = parseInt(e.dataTransfer.getData('text/plain'), 10);
                                if (!isNaN(fromIdx) && fromIdx !== idx) {
                                  const updated = [...arr];
                                  const [movedItem] = updated.splice(fromIdx, 1);
                                  updated.splice(idx, 0, movedItem);
                                  setEditingTier({ ...editingTier, merchant_benefits: updated });
                                }
                              }}
                              style={{ 
                                display: 'flex', 
                                alignItems: 'center', 
                                justifyContent: 'space-between', 
                                backgroundColor: '#FFFFFF', 
                                padding: '12px 16px', 
                                borderRadius: '14px', 
                                border: '1.5px solid #FED7AA', 
                                boxShadow: '0 2px 8px rgba(251, 133, 0, 0.06)',
                                cursor: 'grab',
                                transition: 'all 0.15s ease'
                              }}
                            >
                              <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flex: 1, marginRight: '10px' }}>
                                {/* Drag Grip Handle */}
                                <span 
                                  title="Drag to reorder" 
                                  style={{ fontSize: '1.2rem', color: '#FB8500', cursor: 'grab', userSelect: 'none', fontWeight: 900 }}
                                >
                                  ⋮⋮
                                </span>
                                <span style={{ fontWeight: 900, color: '#FB8500', fontSize: '0.9rem' }}>✓</span>
                                {/* Inline Editable Input */}
                                <input
                                  type="text"
                                  className="admin-search-input"
                                  style={{
                                    flex: 1,
                                    padding: '8px 12px',
                                    fontSize: '0.88rem',
                                    fontWeight: 800,
                                    border: '1.5px solid #FED7AA',
                                    borderRadius: '10px',
                                    backgroundColor: '#FFFBF7',
                                    color: '#0F172A'
                                  }}
                                  value={b}
                                  onChange={(e) => {
                                    const updated = [...arr];
                                    updated[idx] = e.target.value;
                                    setEditingTier({ ...editingTier, merchant_benefits: updated });
                                  }}
                                  placeholder="Type benefit text..."
                                />
                              </div>

                              <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                {/* Move Up Button */}
                                <button
                                  type="button"
                                  disabled={idx === 0}
                                  onClick={() => {
                                    if (idx === 0) return;
                                    const updated = [...arr];
                                    const temp = updated[idx - 1];
                                    updated[idx - 1] = updated[idx];
                                    updated[idx] = temp;
                                    setEditingTier({ ...editingTier, merchant_benefits: updated });
                                  }}
                                  style={{ 
                                    background: idx === 0 ? '#F1F5F9' : '#FFF7ED', 
                                    border: '1px solid #FED7AA', 
                                    color: idx === 0 ? '#CBD5E1' : '#EA580C', 
                                    width: '28px', 
                                    height: '28px', 
                                    borderRadius: '8px', 
                                    fontWeight: 900, 
                                    cursor: idx === 0 ? 'not-allowed' : 'pointer', 
                                    display: 'flex', 
                                    alignItems: 'center', 
                                    justifyContent: 'center',
                                    fontSize: '0.8rem'
                                  }}
                                  title="Move Up"
                                >
                                  ▲
                                </button>

                                {/* Move Down Button */}
                                <button
                                  type="button"
                                  disabled={idx === arr.length - 1}
                                  onClick={() => {
                                    if (idx === arr.length - 1) return;
                                    const updated = [...arr];
                                    const temp = updated[idx + 1];
                                    updated[idx + 1] = updated[idx];
                                    updated[idx] = temp;
                                    setEditingTier({ ...editingTier, merchant_benefits: updated });
                                  }}
                                  style={{ 
                                    background: idx === arr.length - 1 ? '#F1F5F9' : '#FFF7ED', 
                                    border: '1px solid #FED7AA', 
                                    color: idx === arr.length - 1 ? '#CBD5E1' : '#EA580C', 
                                    width: '28px', 
                                    height: '28px', 
                                    borderRadius: '8px', 
                                    fontWeight: 900, 
                                    cursor: idx === arr.length - 1 ? 'not-allowed' : 'pointer', 
                                    display: 'flex', 
                                    alignItems: 'center', 
                                    justifyContent: 'center',
                                    fontSize: '0.8rem'
                                  }}
                                  title="Move Down"
                                >
                                  ▼
                                </button>

                                {/* Delete Button */}
                                <button
                                  type="button"
                                  onClick={() => handleRemoveMerchantBenefit(idx)}
                                  style={{ background: '#FEE2E2', border: 'none', color: '#EF4444', width: '28px', height: '28px', borderRadius: '50%', fontWeight: 900, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.85rem', marginLeft: '4px' }}
                                  title="Remove Bullet"
                                >
                                  ✕
                                </button>
                              </div>
                            </div>
                          ))}
                        </div>

                        <div style={{ display: 'flex', gap: '10px' }}>
                          <input
                            type="text"
                            className="admin-search-input"
                            placeholder="Add benefit bullet (e.g. 'Unlimited Price Catalog Exports')..."
                            value={newMerchantBenefit}
                            onChange={(e) => setNewMerchantBenefit(e.target.value)}
                            style={{ flex: 1, fontWeight: 700, borderRadius: '12px', padding: '10px 14px' }}
                          />
                          <button
                            type="button"
                            onClick={handleAddMerchantBenefit}
                            style={{ backgroundColor: '#FB8500', color: '#FFFFFF', border: 'none', padding: '10px 20px', borderRadius: '12px', fontWeight: 900, cursor: 'pointer', boxShadow: '0 4px 12px rgba(251,133,0,0.25)', fontSize: '0.85rem' }}
                          >
                            + Add Bullet
                          </button>
                        </div>
                      </div>

                    </form>
                  </div>

                  {/* Right Column: Sticky Live Mobile App Screen Preview (Clean 3 Cards Only) */}
                  <div style={{
                    width: '375px',
                    backgroundColor: '#F8FAFC',
                    padding: '24px 18px',
                    borderLeft: '1.5px solid #E2E8F0',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justify: 'flex-start',
                    overflowY: 'auto'
                  }}>
                    <div style={{ fontSize: '0.8rem', fontWeight: 900, color: '#FB8500', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '14px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                      <span>⚡ Real-Time Mobile Card Preview</span>
                    </div>
                    {renderMobileAppPreview(tiers, editingTier)}
                  </div>
                </div>

                {/* Modal Footer Actions */}
                <div style={{
                  padding: '18px 28px',
                  borderTop: '1px solid #E2E8F0',
                  display: 'flex',
                  justify: 'space-between',
                  alignItems: 'center',
                  backgroundColor: '#FFFFFF'
                }}>
                  <div style={{ fontSize: '0.8rem', color: '#64748B', fontWeight: 600 }}>
                    Changes apply immediately to Supabase database &amp; synced across web/mobile apps.
                  </div>

                  <div style={{ display: 'flex', gap: '12px' }}>
                    <button
                      type="button"
                      className="admin-btn-action"
                      style={{ backgroundColor: '#F1F5F9', color: '#475569', padding: '12px 24px', borderRadius: '14px', fontWeight: 700, border: 'none', cursor: 'pointer' }}
                      onClick={() => setEditingTier(null)}
                    >
                      Cancel
                    </button>
                    <button
                      type="submit"
                      form="tierEditForm"
                      className="admin-btn-action"
                      style={{ backgroundColor: '#FB8500', color: '#FFFFFF', padding: '12px 32px', borderRadius: '14px', fontWeight: 900, border: 'none', cursor: 'pointer', boxShadow: '0 4px 16px rgba(251, 133, 0, 0.35)', fontSize: '0.95rem' }}
                      disabled={mutating}
                    >
                      {mutating ? 'Saving Settings...' : '💾 Save Plan Changes & Publish to Mobile'}
                    </button>
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* TAB 2: MANUAL TIER OVERRIDES */}
      {subTab === 'overrides' && (
        <div>
          <div style={{ 
            backgroundColor: '#EFF6FF', 
            border: '1px solid #BFDBFE', 
            borderRadius: '20px', 
            padding: '18px 24px', 
            marginBottom: '28px',
            display: 'flex',
            alignItems: 'center',
            gap: '16px'
          }}>
            <div style={{ fontSize: '1.8rem' }}>👑</div>
            <div>
              <div style={{ fontWeight: 800, color: '#1E40AF', fontSize: '1rem' }}>Fail-Proof Subscription Override Console</div>
              <div style={{ color: '#3B82F6', fontSize: '0.85rem', marginTop: '2px' }}>
                Grant instant PRO or VIP tier access to any merchant account or store branch without requiring payment.
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
