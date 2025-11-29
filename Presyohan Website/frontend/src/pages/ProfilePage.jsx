import React, { useEffect, useState, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { supabase } from '../config/supabaseClient';
import StoreHeader from '../components/layout/StoreHeader';

const formatPhoneFromInput = (raw) => {
  if (!raw || !raw.trim()) {
    return { formatted: '', normalized: null };
  }
  const digits = raw.replace(/\D/g, '');
  if (!digits) {
    return null;
  }
  let normalized;
  if (digits.startsWith('63') && digits.length === 12) {
    normalized = '0' + digits.slice(2);
  } else if (digits.startsWith('0') && digits.length === 11) {
    normalized = digits;
  } else if (digits.length === 10 && digits.startsWith('9')) {
    normalized = '0' + digits;
  } else if (digits.length === 11 && digits[0] !== '0') {
    normalized = '0' + digits.slice(-10);
  } else {
    return null;
  }
  if (!/^0\d{10}$/.test(normalized)) {
    return null;
  }
  const formatted = `${normalized.slice(0, 4)} ${normalized.slice(4, 7)} ${normalized.slice(7)}`;
  return { formatted, normalized };
};

const normalizePhoneForState = (raw) => {
  if (!raw) return '';
  const result = formatPhoneFromInput(raw);
  return result?.formatted || raw;
};

export default function ProfilePage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [activeTab, setActiveTab] = useState('overview');
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
  const [profile, setProfile] = useState({
    id: null,
    authId: null,
    name: '',
    email: '',
    phone: '',
    avatar_url: ''
  });

  const [preferences, setPreferences] = useState({
    notifications: {
      storeUpdates: true,
      joinRequests: true
    },
    theme: 'system'
  });

  const [memberships, setMemberships] = useState([]);

  const tabs = [
    { id: 'overview', label: 'Profile Overview' },
    { id: 'account', label: 'Account Settings'},
    { id: 'security', label: 'Security'},
    { id: 'memberships', label: 'Memberships'},
    { id: 'notifications', label: 'Notifications'}
  ];

  const [actionStatus, setActionStatus] = useState(null); // { kind: 'info'|'success'|'error', text }
  const [isEditingOverview, setIsEditingOverview] = useState(false);
  const [isSavingProfile, setIsSavingProfile] = useState(false);
  const editSnapshotRef = useRef(null);

  // Avatar cropper state
  const [avatarCropOpen, setAvatarCropOpen] = useState(false);
  const [avatarSrc, setAvatarSrc] = useState(null);
  const [avatarNatural, setAvatarNatural] = useState({ w: 0, h: 0 });
  const [avatarScale, setAvatarScale] = useState(1);
  const [avatarOffset, setAvatarOffset] = useState({ x: 0, y: 0 });
  const [avatarDragging, setAvatarDragging] = useState(false);
  const [avatarLastPoint, setAvatarLastPoint] = useState({ x: 0, y: 0 });
  const AVATAR_CROP_SIZE = 240;

  const fetchAppUserRow = async (authId) => {
    if (!authId) return null;
    const columns = 'id, name, email, avatar_url, phone, user_code';
    let { data, error } = await supabase
      .from('app_users')
      .select(columns)
      .eq('id', authId)
      .maybeSingle();
    if (error && error.code !== 'PGRST116') throw error;
    if (!data) {
      try {
        const res2 = await supabase
          .from('app_users')
          .select(columns)
          .eq('auth_uid', authId)
          .maybeSingle();
        if (res2.error && res2.error.code !== 'PGRST116') throw res2.error;
        data = res2.data || null;
      } catch (_) {}
    }
    return data || null;
  };

  const upsertAppUserFields = async (fields) => {
    const { data: { session } } = await supabase.auth.getSession();
    const authId = session?.user?.id || profile.authId;
    if (!authId) throw new Error('Not signed in.');
    const recordId = profile.id || authId;
    const payload = { id: recordId, ...fields };
    let upsertError = null;
    try {
      const { error } = await supabase
        .from('app_users')
        .upsert(payload, { onConflict: 'id' });
      if (error) upsertError = error;
    } catch (e) {
      upsertError = e;
    }
    if (upsertError) {
      try {
        const payload2 = { id: recordId, auth_uid: recordId, ...fields };
        const { error: err2 } = await supabase
          .from('app_users')
          .upsert(payload2, { onConflict: 'id' });
        if (err2) throw err2;
      } catch (e2) {
        throw e2;
      }
    }
    if (!profile.id) {
      setProfile((prev) => ({ ...prev, id: recordId, authId }));
    }
    return { recordId, authId };
  };

  useEffect(() => {
    const init = async () => {
      try {
        const { data: { session }, error } = await supabase.auth.getSession();
        if (error) throw error;
        if (!session) {
          navigate('/login', { replace: true });
          return;
        }
        await loadProfile(session);
        await Promise.all([
          loadMemberships(),
        ]);
      } catch (e) {
        setActionStatus({ kind: 'error', text: e.message || 'Failed to initialize profile.' });
      }
    };
    init();
  }, [navigate]);

  useEffect(() => {
    const state = location.state;
    if (!state) return;
    let shouldClear = false;
    const desiredTab = state.tab;
    if (desiredTab && ['overview','account','security','memberships','notifications'].includes(desiredTab) && desiredTab !== activeTab) {
      setActiveTab(desiredTab);
      if (!state.startProfileEdit) {
        shouldClear = true;
      }
    }
    if (state.startProfileEdit) {
      if (profile?.id && activeTab === 'overview' && !isEditingOverview) {
        startEditingOverview();
        shouldClear = true;
      } else if (activeTab !== 'overview') {
        setActiveTab('overview');
      }
    }
    if (shouldClear) {
      navigate(location.pathname, { replace: true, state: {} });
    }
  }, [location.state, profile?.id, activeTab, isEditingOverview, navigate]);

  const logout = async () => {
    try {
      await supabase.auth.signOut();
      navigate('/login', { replace: true });
    } catch (e) {
      setActionStatus({ kind: 'error', text: e.message || 'Failed to sign out.' });
    }
  };

  const loadProfile = async (session) => {
    try {
      const authUser = session?.user;
      const row = await fetchAppUserRow(authUser?.id);
      const base = {
        id: row?.id || null,
        authId: authUser?.id || null,
        name: row?.name || authUser?.user_metadata?.name || authUser?.email?.split('@')[0] || '',
        email: row?.email ?? authUser?.email ?? '',
        phone: normalizePhoneForState(row?.phone || authUser?.user_metadata?.phone || ''),
        avatar_url: row?.avatar_url || authUser?.user_metadata?.avatar_url || authUser?.user_metadata?.picture || ''
      };
      setProfile(base);
    } catch (e) {
      console.warn('loadProfile failed', e);
    }
  };

  const loadMemberships = async () => {
    try {
      const { data, error } = await supabase.rpc('get_user_stores');
      if (error) throw error;
      const base = (Array.isArray(data) ? data : []).map((row) => ({
        id: row?.store_id ?? row?.id ?? null,
        name: row?.name ?? '',
        branch: row?.branch ?? '',
        type: row?.type ?? '',
        role: (row?.role ?? '').toLowerCase(),
        owner_name: row?.owner_name ?? null
      }));
      const enriched = await Promise.all(base.map(async (s) => {
        if (s.role !== 'owner' || !s.id) return s;
        try {
          const { data: members, error: memErr } = await supabase.rpc('get_store_members', { p_store_id: s.id });
          if (memErr) throw memErr;
          const owners = (Array.isArray(members) ? members : []).filter((m) => (m.role || '').toLowerCase() === 'owner').length;
          return { ...s, ownerCount: owners, hasOtherOwner: owners > 1 };
        } catch (_) {
          return { ...s, ownerCount: 1, hasOtherOwner: false };
        }
      }));
      setMemberships(enriched);
    } catch (e) {
      console.warn('loadMemberships failed', e);
    }
  };

  const leaveStore = async (storeId) => {
    try {
      setMemberships((prev) => prev.filter((m) => m.id !== storeId));
      const { error } = await supabase.rpc('leave_store', { p_store_id: storeId });
      if (error) throw error;
      setActionStatus({ kind: 'success', text: 'Left store.' });
    } catch (e) {
      setActionStatus({ kind: 'error', text: e.message || 'Failed to leave store.' });
      loadMemberships().catch(() => {});
    }
  };

  const deleteStore = async (storeId) => {
    try {
      const { error } = await supabase.from('stores').delete().eq('id', storeId);
      if (error) throw error;
      setMemberships((prev) => prev.filter((m) => m.id !== storeId));
      setActionStatus({ kind: 'success', text: 'Store deleted.' });
    } catch (e) {
      setActionStatus({ kind: 'error', text: e.message || 'Failed to delete store.' });
    }
  };

  // Preferences persistence: use localStorage fallback (no DB column)
  useEffect(() => {
    try {
      const raw = window.localStorage.getItem('profile_preferences');
      if (raw) {
        const parsed = JSON.parse(raw);
        if (parsed && typeof parsed === 'object') {
          setPreferences((prev) => ({ ...prev, ...parsed }));
        }
      }
    } catch (_) {}
  }, []);

  const savePreferences = async () => {
    try {
      window.localStorage.setItem('profile_preferences', JSON.stringify(preferences));
      setActionStatus({ kind: 'success', text: 'Preferences saved.' });
    } catch (e) {
      setActionStatus({ kind: 'error', text: e.message || 'Failed to save preferences.' });
    }
  };

  const uploadAvatar = async (fileOrBlob) => {
    try {
      const ownerId = profile.id || profile.authId;
      if (!fileOrBlob || !ownerId) return;
      const ext = (fileOrBlob.name?.split('.')?.pop()?.toLowerCase()) || 'jpg';
      const filename = `${ownerId}-${Date.now()}.${ext}`;
      const { error: upErr } = await supabase.storage.from('avatars').upload(filename, fileOrBlob, { upsert: true });
      if (upErr) throw upErr;
      const { data } = supabase.storage.from('avatars').getPublicUrl(filename);
      const publicUrl = data?.publicUrl;
      if (!publicUrl) throw new Error('Failed to resolve avatar URL.');
      setProfile((p) => ({ ...p, avatar_url: publicUrl }));
      await upsertAppUserFields({ avatar_url: publicUrl });
      await supabase.auth.updateUser({ data: { avatar_url: publicUrl } });
      setActionStatus({ kind: 'success', text: 'Avatar updated.' });
    } catch (e) {
      setActionStatus({ kind: 'error', text: e.message || 'Avatar upload failed.' });
    }
  };

  // Start cropper with selected file
  const startAvatarCrop = (file) => {
    try {
      if (!file) return;
      const reader = new FileReader();
      reader.onload = () => {
        setAvatarSrc(reader.result);
        // Reset cropper defaults; scale/offset will finalize on image load
        setAvatarScale(1);
        setAvatarOffset({ x: 0, y: 0 });
        setAvatarCropOpen(true);
      };
      reader.readAsDataURL(file);
    } catch (e) {
      setActionStatus({ kind: 'error', text: e.message || 'Failed to load image.' });
    }
  };

  // When image loads in modal, set natural size and initial scale/offset to cover and center
  const onAvatarImgLoad = (e) => {
    try {
      const img = e.target;
      const w = img.naturalWidth || img.width;
      const h = img.naturalHeight || img.height;
      const minScale = Math.max(AVATAR_CROP_SIZE / w, AVATAR_CROP_SIZE / h);
      setAvatarNatural({ w, h });
      setAvatarScale(minScale);
      const x = (AVATAR_CROP_SIZE - w * minScale) / 2;
      const y = (AVATAR_CROP_SIZE - h * minScale) / 2;
      setAvatarOffset({ x, y });
    } catch (_) {}
  };

  // Dragging within crop area
  const onAvatarPointerDown = (e) => {
    setAvatarDragging(true);
    const pt = { x: e.clientX, y: e.clientY };
    setAvatarLastPoint(pt);
  };
  const onAvatarPointerMove = (e) => {
    if (!avatarDragging) return;
    const dx = e.clientX - avatarLastPoint.x;
    const dy = e.clientY - avatarLastPoint.y;
    const next = { x: avatarOffset.x + dx, y: avatarOffset.y + dy };
    // Clamp offsets to avoid empty areas
    const maxX = 0;
    const minX = AVATAR_CROP_SIZE - avatarNatural.w * avatarScale;
    const maxY = 0;
    const minY = AVATAR_CROP_SIZE - avatarNatural.h * avatarScale;
    next.x = Math.min(maxX, Math.max(minX, next.x));
    next.y = Math.min(maxY, Math.max(minY, next.y));
    setAvatarOffset(next);
    setAvatarLastPoint({ x: e.clientX, y: e.clientY });
  };
  const onAvatarPointerUp = () => {
    setAvatarDragging(false);
  };

  // Adjust scale, keeping image within bounds
  const changeAvatarScale = (s) => {
    const scale = Math.max(s, Math.max(AVATAR_CROP_SIZE / avatarNatural.w, AVATAR_CROP_SIZE / avatarNatural.h));
    // Keep center point approximately stable
    const center = { x: AVATAR_CROP_SIZE / 2, y: AVATAR_CROP_SIZE / 2 };
    const imgCenterBefore = { x: -avatarOffset.x + center.x, y: -avatarOffset.y + center.y };
    const ratio = scale / avatarScale;
    const nextOffset = {
      x: center.x - imgCenterBefore.x * ratio,
      y: center.y - imgCenterBefore.y * ratio
    };
    const maxX = 0;
    const minX = AVATAR_CROP_SIZE - avatarNatural.w * scale;
    const maxY = 0;
    const minY = AVATAR_CROP_SIZE - avatarNatural.h * scale;
    nextOffset.x = Math.min(maxX, Math.max(minX, nextOffset.x));
    nextOffset.y = Math.min(maxY, Math.max(minY, nextOffset.y));
    setAvatarScale(scale);
    setAvatarOffset(nextOffset);
  };

  // Produce cropped image and upload
  const confirmAvatarCrop = async () => {
    try {
      const canvas = document.createElement('canvas');
      canvas.width = AVATAR_CROP_SIZE;
      canvas.height = AVATAR_CROP_SIZE;
      const ctx = canvas.getContext('2d');
      const img = new Image();
      img.src = avatarSrc;
      await new Promise((res, rej) => { img.onload = res; img.onerror = rej; });
      // Compute source rect in original image coords
      let srcX = -avatarOffset.x / avatarScale;
      let srcY = -avatarOffset.y / avatarScale;
      let srcW = AVATAR_CROP_SIZE / avatarScale;
      let srcH = AVATAR_CROP_SIZE / avatarScale;
      // Clamp to image bounds
      srcX = Math.max(0, Math.min(avatarNatural.w - srcW, srcX));
      srcY = Math.max(0, Math.min(avatarNatural.h - srcH, srcY));
      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = 'high';
      ctx.drawImage(img, srcX, srcY, srcW, srcH, 0, 0, AVATAR_CROP_SIZE, AVATAR_CROP_SIZE);
      const blob = await new Promise((res) => canvas.toBlob(res, 'image/jpeg', 0.92));
      if (!blob) throw new Error('Failed to create image blob.');
      await uploadAvatar(blob);
      setAvatarCropOpen(false);
      setAvatarSrc(null);
    } catch (e) {
      setActionStatus({ kind: 'error', text: e.message || 'Failed to crop avatar.' });
    }
  };
  const closeAvatarCrop = () => {
    setAvatarCropOpen(false);
    setAvatarSrc(null);
  };

  // Change Email removed per requirements

  const changePassword = async (newPassword) => {
    try {
      const { error } = await supabase.auth.updateUser({ password: newPassword });
      if (error) throw error;
      setActionStatus({ kind: 'success', text: 'Password updated.' });
    } catch (e) {
      setActionStatus({ kind: 'error', text: e.message || 'Failed to change password.' });
    }
  };

  // Confirmation modal state
  const [confirmState, setConfirmState] = useState({
    open: false,
    title: '',
    message: '',
    confirmText: 'Confirm',
    cancelText: 'Cancel',
    onConfirm: null
  });

  const openConfirm = (opts) => setConfirmState({ open: true, ...opts });
  const closeConfirm = () => setConfirmState((s) => ({ ...s, open: false }));

  const startEditingOverview = () => {
    editSnapshotRef.current = { ...profile };
    setIsEditingOverview(true);
    setActionStatus(null);
  };

  const cancelEditingOverview = () => {
    if (editSnapshotRef.current) {
      setProfile(editSnapshotRef.current);
    }
    setIsEditingOverview(false);
    setIsSavingProfile(false);
  };

  const handleSave = async () => {
    try {
      if (!profile.id && !profile.authId) throw new Error('No profile loaded.');
      setIsSavingProfile(true);
      const trimmedName = profile.name?.trim() || '';
      let formattedPhone = '';
      let normalizedPhone = null;
      if (profile.phone?.trim()) {
        const phoneResult = formatPhoneFromInput(profile.phone);
        if (!phoneResult) {
          setActionStatus({ kind: 'error', text: 'Please enter a valid phone number (e.g., 0932 430 8387).' });
          setIsSavingProfile(false);
          return;
        }
        formattedPhone = phoneResult.formatted;
        normalizedPhone = phoneResult.normalized;
        const { data: conflicts, error: conflictErr } = await supabase
          .from('app_users')
          .select('id')
          .neq('id', profile.id)
          .eq('phone_normalized', normalizedPhone)
          .limit(1);
        if (conflictErr) throw conflictErr;
        if (Array.isArray(conflicts) && conflicts.length > 0) {
          setActionStatus({ kind: 'error', text: 'Phone number already used.' });
          setIsSavingProfile(false);
          return;
        }
      }
      const payload = {
        name: trimmedName || null,
        phone: formattedPhone || null,
        phone_normalized: normalizedPhone || null
      };
      await upsertAppUserFields(payload);
      await supabase.auth.updateUser({ data: { name: payload.name, phone: payload.phone } });
      editSnapshotRef.current = { ...profile, name: trimmedName, phone: formattedPhone || '' };
      setProfile((prev) => ({ ...prev, name: trimmedName, phone: formattedPhone || '' }));
      setIsEditingOverview(false);
      setActionStatus({ kind: 'success', text: 'Profile updated.' });
    } catch (e) {
      setActionStatus({ kind: 'error', text: e.message || 'Failed to save profile.' });
    } finally {
      setIsSavingProfile(false);
    }
  };

  return (
    <div style={{ 
      minHeight: '100vh', 
      background: '#f5f5f5', 
      fontFamily: 'system-ui, -apple-system, sans-serif',
      paddingBottom: '40px'
    }}>
      {/* Header */}
      <StoreHeader onLogout={logout} includeAllStoresLink={true} />
      {/* Subheader */}
      <div style={{
        background: '#ffffff',
        padding: '15px 20px',
        borderBottom: '1px solid #e0e0e0',
        position: 'sticky',
        top: 0, // pin directly to viewport top
        zIndex: 1000,
        boxShadow: '0 2px 6px rgba(0,0,0,0.06)'
      }}>
        <div style={{ 
          display: 'flex', 
          alignItems: 'center', 
          gap: '15px',
          width: '100%'
        }}>
          <button onClick={() => navigate(-1)} style={{
            background: 'transparent',
            border: 'none',
            cursor: 'pointer',
            padding: '5px',
            color: '#ff8c00'
          }}>
            <svg width="24" height="24" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 19l-7-7 7-7"/>
            </svg>
          </button>
          <h1 style={{ 
            fontSize: '1.3rem', 
            fontWeight: '700',
            margin: 0,
            color: '#ff8c00'
          }}>
            Profile Settings
          </h1>
          
          {/* Mobile Hamburger Menu */}
          <button 
            className="mobile-menu-toggle"
            onClick={() => setMobileSidebarOpen(!mobileSidebarOpen)}
            style={{
              marginLeft: 'auto',
              background: 'transparent',
              border: 'none',
              cursor: 'pointer',
              padding: '5px',
              color: '#ff8c00'
            }}
          >
            <svg width="28" height="28" fill="currentColor" viewBox="0 0 24 24">
              <path d="M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z"/>
            </svg>
          </button>
        </div>
      </div>

      {/* Mobile Sidebar Overlay */}
      {mobileSidebarOpen && (
        <div 
          className="mobile-overlay"
          onClick={() => setMobileSidebarOpen(false)}
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            background: 'rgba(0, 0, 0, 0.5)',
            zIndex: 999
          }}
        ></div>
      )}

      {/* Main Content */}
      <div style={{ 
        display: 'flex',
        width: '100%',
        margin: 0,
        gap: '0',
        minHeight: 'calc(100vh - 60px)'
      }}>
        {/* Side Navigation */}
        <div 
          className="sidebar"
          style={{ 
            width: '280px',
            background: 'white',
            borderRight: '1px solid #e0e0e0',
            padding: '20px',
            position: 'sticky',
            top: '64px',
            height: 'fit-content',
            maxHeight: 'calc(100vh - 64px)',
            overflowY: 'auto'
          }}>
          <h3 style={{ 
            fontSize: '0.75rem', 
            fontWeight: '700', 
            color: '#999', 
            letterSpacing: '1px',
            marginBottom: '15px',
            paddingLeft: '5px'
          }}>
            NAVIGATION
          </h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            {tabs.map((tab) => {
              const isSelected = activeTab === tab.id;
              return (
                <button
                  key={tab.id}
                  onClick={() => {
                    setActiveTab(tab.id);
                    setMobileSidebarOpen(false);
                  }}
                  onMouseEnter={(e) => {
                    if (!isSelected) {
                      e.currentTarget.style.background = 'linear-gradient(135deg, #e0f7fa 0%, #b2ebf2 100%)';
                    }
                  }}
                  onMouseLeave={(e) => {
                    if (!isSelected) {
                      e.currentTarget.style.background = 'white';
                    }
                  }}
                  style={{
                    padding: '14px 18px',
                    border: 'none',
                    borderRadius: '25px',
                    background: isSelected ? '#00bcd4' : 'white',
                    color: isSelected ? 'white' : '#555',
                    fontSize: '0.9rem',
                    fontWeight: isSelected ? '600' : '500',
                    cursor: 'pointer',
                    textAlign: 'left',
                    transition: 'all 0.3s ease',
                    boxShadow: isSelected ? 
                      '0 4px 12px rgba(0, 188, 212, 0.3)' : 
                      '0 1px 3px rgba(0,0,0,0.05)',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '10px'
                  }}
                >
                  <span style={{ fontSize: '1.2rem' }}>{tab.icon}</span>
                  {tab.label}
                </button>
              );
            })}
          </div>
        </div>

        {/* Content Area */}
        <div style={{ flex: 1, padding: '20px' }}>
          <div style={{
            background: 'white',
            borderRadius: '20px',
            padding: '30px',
            boxShadow: '0 4px 15px rgba(0, 0, 0, 0.1)'
          }}>
            {/* Profile Overview */}
            {activeTab === 'overview' && (
              <div>
                <h2 style={{ 
                  fontSize: '1.5rem', 
                  fontWeight: '700', 
                  color: '#333',
                  marginBottom: '30px'
                }}>
                  Profile Overview
                </h2>

                {/* Avatar Section */}
                <div style={{
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  marginBottom: '40px'
                }}>
                  <div style={{
                    width: '120px',
                    height: '120px',
                    borderRadius: '50%',
                    backgroundImage: profile.avatar_url 
                      ? `url(${profile.avatar_url})` 
                      : 'linear-gradient(135deg, #ffb800 0%, #ff8c00 100%)',
                    backgroundRepeat: 'no-repeat',
                    backgroundSize: 'cover',
                    backgroundPosition: 'center',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    marginBottom: '15px',
                    boxShadow: '0 4px 15px rgba(0, 0, 0, 0.1)'
                  }}>
                    {!profile.avatar_url && (
                      <svg width="60" height="60" fill="white" viewBox="0 0 24 24">
                        <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/>
                      </svg>
                    )}
                  </div>
                  <input
                    id="avatar-input"
                    type="file"
                    accept="image/*"
                    style={{ display: 'none' }}
                    onChange={(e) => startAvatarCrop(e.target.files?.[0])}
                  />
                  <button onClick={() => document.getElementById('avatar-input')?.click()} style={{
                    padding: '8px 20px',
                    background: 'transparent',
                    border: '2px solid #ff8c00',
                    borderRadius: '20px',
                    color: '#ff8c00',
                    fontSize: '0.9rem',
                    fontWeight: '600',
                    cursor: 'pointer'
                  }}>
                    Change Avatar
                  </button>
                </div>

                {/* Form Fields */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
                  <div>
                    <label style={{
                      display: 'block',
                      fontSize: '0.9rem',
                      fontWeight: '600',
                      color: '#333',
                      marginBottom: '8px'
                    }}>Full Name</label>
                    <input
                      type="text"
                      value={profile.name}
                      readOnly={!isEditingOverview}
                      onChange={(e) => setProfile({ ...profile, name: e.target.value })}
                      style={{
                        width: '100%',
                        padding: '14px',
                        border: '2px solid #e0e0e0',
                        borderRadius: '10px',
                        fontSize: '1rem',
                        outline: 'none',
                        boxSizing: 'border-box',
                        background: isEditingOverview ? 'white' : '#f5f5f5',
                        color: isEditingOverview ? '#333' : '#777',
                        cursor: isEditingOverview ? 'text' : 'not-allowed'
                      }}
                    />
                  </div>

                  <div>
                    <label style={{
                      display: 'block',
                      fontSize: '0.9rem',
                      fontWeight: '600',
                      color: '#333',
                      marginBottom: '8px'
                    }}>Email</label>
                    <input
                      type="email"
                      value={profile.email}
                      readOnly
                      style={{
                        width: '100%',
                        padding: '14px',
                        border: '2px solid #e0e0e0',
                        borderRadius: '10px',
                        fontSize: '1rem',
                        outline: 'none',
                        boxSizing: 'border-box',
                        background: '#f5f5f5',
                        color: '#999'
                      }}
                    />
                  </div>

                  <div>
                    <label style={{
                      display: 'block',
                      fontSize: '0.9rem',
                      fontWeight: '600',
                      color: '#333',
                      marginBottom: '8px'
                    }}>Phone Number</label>
                    <input
                      type="tel"
                      value={profile.phone}
                      readOnly={!isEditingOverview}
                      placeholder="e.g., +63 900 000 0000"
                      onChange={(e) => setProfile({ ...profile, phone: e.target.value })}
                      style={{
                        width: '100%',
                        padding: '14px',
                        border: '2px solid #e0e0e0',
                        borderRadius: '10px',
                        fontSize: '1rem',
                        outline: 'none',
                        boxSizing: 'border-box',
                        background: isEditingOverview ? 'white' : '#f5f5f5',
                        color: isEditingOverview ? '#333' : '#777',
                        cursor: isEditingOverview ? 'text' : 'not-allowed'
                      }}
                    />
                  </div>

                  {!isEditingOverview ? (
                    <button
                      onClick={startEditingOverview}
                      style={{
                        width: '100%',
                        padding: '16px',
                        background: 'linear-gradient(135deg, #00bcd4 0%, #0097a7 100%)',
                        color: 'white',
                        border: 'none',
                        borderRadius: '25px',
                        fontSize: '1.1rem',
                        fontWeight: '700',
                        cursor: 'pointer',
                        marginTop: '10px'
                      }}
                    >
                      Edit Profile
                    </button>
                  ) : (
                    <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', marginTop: '10px' }}>
                      <button
                        onClick={cancelEditingOverview}
                        style={{
                          flex: 1,
                          padding: '16px',
                          background: '#f1f1f1',
                          color: '#555',
                          border: '2px solid #e0e0e0',
                          borderRadius: '25px',
                          fontSize: '1rem',
                          fontWeight: '600',
                          cursor: 'pointer'
                        }}
                      >
                        Cancel
                      </button>
                      <button
                        onClick={handleSave}
                        disabled={isSavingProfile}
                        style={{
                          flex: 1,
                          padding: '16px',
                          background: 'linear-gradient(135deg, #ffb800 0%, #ff8c00 100%)',
                          color: 'white',
                          border: 'none',
                          borderRadius: '25px',
                          fontSize: '1.1rem',
                          fontWeight: '700',
                          cursor: isSavingProfile ? 'wait' : 'pointer',
                          opacity: isSavingProfile ? 0.7 : 1
                        }}
                      >
                        {isSavingProfile ? 'Saving…' : 'Save Changes'}
                      </button>
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* Account Settings */}
            {activeTab === 'account' && (
              <div>
                <h2 style={{ 
                  fontSize: '1.5rem', 
                  fontWeight: '700', 
                  color: '#333',
                  marginBottom: '30px'
                }}>
                  Account Settings
                </h2>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '25px' }}>
                  <div style={{
                    padding: '20px',
                    background: '#f8f9fa',
                    borderRadius: '12px'
                  }}>
                    <h3 style={{ fontSize: '1.1rem', fontWeight: '600', marginBottom: '15px' }}>
                      Change Password
                    </h3>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: '12px' }}>
                      <input
                        id="current-password"
                        type="password"
                        placeholder="Current password"
                        style={{
                          padding: '12px',
                          border: '2px solid #e0e0e0',
                          borderRadius: '10px',
                          fontSize: '1rem',
                          outline: 'none'
                        }}
                      />
                      <input
                        id="new-password"
                        type="password"
                        placeholder="New password (min 8 chars, mixed case, number)"
                        style={{
                          padding: '12px',
                          border: '2px solid #e0e0e0',
                          borderRadius: '10px',
                          fontSize: '1rem',
                          outline: 'none'
                        }}
                      />
                      <input
                        id="confirm-password"
                        type="password"
                        placeholder="Confirm new password"
                        style={{
                          padding: '12px',
                          border: '2px solid #e0e0e0',
                          borderRadius: '10px',
                          fontSize: '1rem',
                          outline: 'none'
                        }}
                      />
                      <button onClick={() => {
                        const current = document.getElementById('current-password')?.value || '';
                        const next = document.getElementById('new-password')?.value || '';
                        const confirm = document.getElementById('confirm-password')?.value || '';
                        const hasUpper = /[A-Z]/.test(next);
                        const hasLower = /[a-z]/.test(next);
                        const hasNum = /\d/.test(next);
                        if (!current) return setActionStatus({ kind: 'error', text: 'Enter current password.' });
                        if (next.length < 8 || !hasUpper || !hasLower || !hasNum) {
                          return setActionStatus({ kind: 'error', text: 'Password must be 8+ chars with upper, lower, and a number.' });
                        }
                        if (next !== confirm) {
                          return setActionStatus({ kind: 'error', text: 'New password and confirmation do not match.' });
                        }
                        openConfirm({
                          title: 'Change Password',
                          message: 'Are you sure you want to change your password?',
                          confirmText: 'Change Password',
                          onConfirm: async () => {
                            try {
                              const { error: reauthError } = await supabase.auth.signInWithPassword({ email: profile.email, password: current });
                              if (reauthError) throw new Error('Current password is incorrect.');
                              await changePassword(next);
                            } catch (e) {
                              setActionStatus({ kind: 'error', text: e.message || 'Failed to change password.' });
                            } finally {
                              closeConfirm();
                            }
                          }
                        });
                      }} style={{
                        padding: '12px 30px',
                        background: '#00bcd4',
                        color: 'white',
                        border: 'none',
                        borderRadius: '20px',
                        fontSize: '0.9rem',
                        fontWeight: '600',
                        cursor: 'pointer'
                      }}>
                        Update Password
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* Security */}
            {activeTab === 'security' && (
              <div>
                <h2 style={{ 
                  fontSize: '1.5rem', 
                  fontWeight: '700', 
                  color: '#333',
                  marginBottom: '30px'
                }}>
                  Security
                </h2>

                <div style={{
                  padding: '20px',
                  background: '#f8f9fa',
                  borderRadius: '12px',
                  marginBottom: '20px'
                }}>
                  <div style={{ marginBottom: '15px' }}>
                    <strong>Email Verification:</strong> <span style={{ color: '#00bcd4' }}>Verified ✓</span>
                  </div>
                  <div>
                    <strong>Two-Factor Authentication:</strong> <span style={{ color: '#999' }}>Not enabled</span>
                  </div>
                </div>

                <button onClick={() => openConfirm({
                  title: 'Sign Out',
                  message: 'Are you sure you want to sign out?',
                  confirmText: 'Sign Out',
                  onConfirm: async () => { await logout(); closeConfirm(); }
                })} style={{
                  padding: '12px 30px',
                  background: '#ff4444',
                  color: 'white',
                  border: 'none',
                  borderRadius: '20px',
                  fontSize: '1rem',
                  fontWeight: '600',
                  cursor: 'pointer'
                }}>
                  Sign Out
                </button>
              </div>
            )}

            {/* Memberships */}
            {activeTab === 'memberships' && (
              <div>
                <h2 style={{ 
                  fontSize: '1.5rem', 
                  fontWeight: '700', 
                  color: '#333',
                  marginBottom: '30px'
                }}>
                  Store Memberships
                </h2>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
                  {memberships.map((store, idx) => (
                    <div key={store?.id ?? `${store?.name || 'store'}-${idx}` } style={{
                      padding: '20px',
                      background: '#f8f9fa',
                      borderRadius: '12px',
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      flexWrap: 'wrap',
                      gap: '15px'
                    }}>
                      <div>
                        <div style={{ fontSize: '1.1rem', fontWeight: '600', color: '#333' }}>
                          {store.name}
                        </div>
                        <div style={{ fontSize: '0.9rem', color: '#999', marginTop: '5px' }}>
                          {store.branch} • {store.role}
                        </div>
                      </div>
                      <div style={{ display: 'flex', gap: '10px' }}>
                        <button onClick={() => {
                          if (!store?.id) {
                            return setActionStatus({ kind: 'error', text: 'Cannot open store: missing ID.' });
                          }
                          navigate(`/store?id=${encodeURIComponent(store.id)}`);
                        }} style={{
                          padding: '8px 20px',
                          background: '#00bcd4',
                          color: 'white',
                          border: 'none',
                          borderRadius: '20px',
                          fontSize: '0.9rem',
                          fontWeight: '600',
                          cursor: 'pointer'
                        }}>
                          View Store
                        </button>
                        {(() => {
                          const isOwner = (store.role || '').toLowerCase() === 'owner';
                          const isSoleOwner = isOwner && !(store.hasOtherOwner);
                          if (isOwner && isSoleOwner) {
                            return (
                              <button onClick={() => openConfirm({
                                title: 'Delete Store',
                                message: 'Deleting a store is permanent. Continue?',
                                confirmText: 'Delete Store',
                                onConfirm: async () => { await deleteStore(store.id); closeConfirm(); }
                              })} style={{
                                padding: '8px 20px',
                                background: 'transparent',
                                color: '#ff4444',
                                border: '2px solid #ff4444',
                                borderRadius: '20px',
                                fontSize: '0.9rem',
                                fontWeight: '600',
                                cursor: 'pointer'
                              }}>
                                Delete Store
                              </button>
                            );
                          }
                          return (
                            <button onClick={() => openConfirm({
                              title: 'Leave Store',
                              message: 'Are you sure you want to leave this store?',
                              confirmText: 'Leave Store',
                              onConfirm: async () => { await leaveStore(store.id); closeConfirm(); }
                            })} style={{
                              padding: '8px 20px',
                              background: 'transparent',
                              color: '#ff4444',
                              border: '2px solid #ff4444',
                              borderRadius: '20px',
                              fontSize: '0.9rem',
                              fontWeight: '600',
                              cursor: 'pointer'
                            }}>
                              Leave Store
                            </button>
                          );
                        })()}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Notifications */}
            {activeTab === 'notifications' && (
              <div>
                <h2 style={{ 
                  fontSize: '1.5rem', 
                  fontWeight: '700', 
                  color: '#333',
                  marginBottom: '30px'
                }}>
                  Notification Preferences
                </h2>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
                  <div style={{
                    padding: '15px 20px',
                    background: '#f8f9fa',
                    borderRadius: '12px',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '15px'
                  }}>
                    <input
                      type="checkbox"
                      checked={preferences.notifications.storeUpdates}
                      onChange={(e) => setPreferences({
                        ...preferences,
                        notifications: { ...preferences.notifications, storeUpdates: e.target.checked }
                      })}
                      style={{ width: '20px', height: '20px', cursor: 'pointer' }}
                    />
                    <div>
                      <div style={{ fontWeight: '600' }}>Store Updates</div>
                      <div style={{ fontSize: '0.85rem', color: '#666' }}>
                        Receive notifications about store changes and updates
                      </div>
                    </div>
                  </div>

                  <div style={{
                    padding: '15px 20px',
                    background: '#f8f9fa',
                    borderRadius: '12px',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '15px'
                  }}>
                    <input
                      type="checkbox"
                      checked={preferences.notifications.joinRequests}
                      onChange={(e) => setPreferences({
                        ...preferences,
                        notifications: { ...preferences.notifications, joinRequests: e.target.checked }
                      })}
                      style={{ width: '20px', height: '20px', cursor: 'pointer' }}
                    />
                    <div>
                      <div style={{ fontWeight: '600' }}>Join Requests</div>
                      <div style={{ fontSize: '0.85rem', color: '#666' }}>
                        Get notified when someone requests to join your store
                      </div>
                    </div>
                  </div>

                  <button onClick={savePreferences} style={{
                    padding: '14px',
                    background: 'linear-gradient(135deg, #ffb800 0%, #ff8c00 100%)',
                    color: 'white',
                    border: 'none',
                    borderRadius: '25px',
                    fontSize: '1rem',
                    fontWeight: '700',
                    cursor: 'pointer',
                    marginTop: '10px'
                  }}>
                    Save Preferences
                  </button>
                </div>
              </div>
            )}

            {/* Activity Log removed per requirements */}
          </div>
        </div>
      </div>

      {/* Confirmation Modal */}
      {confirmState.open && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 2000 }}>
          <div style={{ width: '90%', maxWidth: '420px', background: 'white', borderRadius: '12px', boxShadow: '0 10px 30px rgba(0,0,0,0.2)' }}>
            <div style={{ padding: '20px', borderBottom: '1px solid #eee' }}>
              <div style={{ fontSize: '1.1rem', fontWeight: 700 }}>{confirmState.title}</div>
            </div>
            <div style={{ padding: '20px', color: '#555' }}>{confirmState.message}</div>
            <div style={{ padding: '16px 20px', display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
              <button onClick={closeConfirm} style={{ padding: '10px 16px', background: '#f1f1f1', border: 'none', borderRadius: '8px', cursor: 'pointer' }}>{confirmState.cancelText || 'Cancel'}</button>
              <button onClick={confirmState.onConfirm} style={{ padding: '10px 16px', background: '#ff4444', color: 'white', border: 'none', borderRadius: '8px', cursor: 'pointer' }}>{confirmState.confirmText || 'Confirm'}</button>
            </div>
          </div>
        </div>
      )}

      {/* Avatar Crop Modal */}
      {avatarCropOpen && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 2100 }}
             onPointerUp={onAvatarPointerUp}
             onMouseLeave={onAvatarPointerUp}>
          <div style={{ width: '95%', maxWidth: '520px', background: 'white', borderRadius: '12px', boxShadow: '0 12px 30px rgba(0,0,0,0.25)' }}>
            <div style={{ padding: '16px 20px', borderBottom: '1px solid #eee' }}>
              <div style={{ fontSize: '1.1rem', fontWeight: 700 }}>Crop Avatar</div>
              <div style={{ fontSize: '0.85rem', color: '#666' }}>Drag the image and use the slider to zoom. We’ll save a square avatar.</div>
            </div>
            <div style={{ padding: '20px', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '16px' }}>
              <div style={{ width: AVATAR_CROP_SIZE, height: AVATAR_CROP_SIZE, borderRadius: '50%', overflow: 'hidden', position: 'relative', background: '#f2f2f2', boxShadow: 'inset 0 0 0 2px #eee' }}
                   onPointerDown={onAvatarPointerDown}
                   onPointerMove={onAvatarPointerMove}>
                {avatarSrc && (
                  <img src={avatarSrc}
                       onLoad={onAvatarImgLoad}
                       alt="avatar-crop"
                       style={{
                         position: 'absolute',
                         left: 0,
                         top: 0,
                         transform: `translate(${avatarOffset.x}px, ${avatarOffset.y}px) scale(${avatarScale})`,
                         transformOrigin: 'top left',
                         userSelect: 'none',
                         pointerEvents: 'none'
                       }} />
                )}
              </div>
              <div style={{ width: '80%', display: 'flex', alignItems: 'center', gap: '12px' }}>
                <span style={{ fontSize: '0.85rem', color: '#666' }}>Zoom</span>
                <input type="range" min={Math.max(AVATAR_CROP_SIZE / Math.max(1, avatarNatural.w), AVATAR_CROP_SIZE / Math.max(1, avatarNatural.h))}
                       max={4}
                       step={0.01}
                       value={avatarScale}
                       onChange={(e) => changeAvatarScale(parseFloat(e.target.value))}
                       style={{ flex: 1 }} />
              </div>
            </div>
            <div style={{ padding: '16px 20px', display: 'flex', justifyContent: 'flex-end', gap: '10px', borderTop: '1px solid #eee' }}>
              <button onClick={closeAvatarCrop} style={{ padding: '10px 16px', background: '#f1f1f1', border: 'none', borderRadius: '8px', cursor: 'pointer' }}>Cancel</button>
              <button onClick={confirmAvatarCrop} style={{ padding: '10px 16px', background: '#ff8c00', color: 'white', border: 'none', borderRadius: '8px', cursor: 'pointer' }}>Save Avatar</button>
            </div>
          </div>
        </div>
      )}

      {/* Responsive Styles */}
      <style>{`
        @media (max-width: 768px) {
          .sidebar {
            position: fixed !important;
            left: ${mobileSidebarOpen ? '0' : '-300px'} !important;
            top: 0 !important;
            height: 100vh !important;
            max-height: 100vh !important;
            z-index: 1000 !important;
            transition: left 0.3s ease !important;
            box-shadow: 2px 0 10px rgba(0, 0, 0, 0.1) !important;
          }
          .mobile-menu-toggle {
            display: block !important;
          }
          .mobile-overlay {
            display: ${mobileSidebarOpen ? 'block' : 'none'} !important;
          }
        }
      `}</style>
    </div>
  );
}
