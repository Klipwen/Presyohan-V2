import React, { useState, useEffect } from 'react';
import storeHeaderLogo from '../../assets/presyohan_logo.png';
import { Link, useNavigate } from 'react-router-dom';
import { supabase } from '../../config/supabaseClient';
import '../../styles/StoreHeader.css';
import storeIcon from '../../assets/icon_store.png';
import NotificationsPanel from '../notifications/NotificationsPanel';

// StoreHeader with side-menu layout
export default function StoreHeader({ stores = [], onLogout, includeAllStoresLink = true }) {
  const navigate = useNavigate();
  const [sideMenuOpen, setSideMenuOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);
  const [storesExpanded, setStoresExpanded] = useState(false);
  const [userProfile, setUserProfile] = useState({
    name: 'User Name',
    email: '',
    phone: '',
    avatarUrl: null,
    userCode: ''
  });
  const [requireContactModal, setRequireContactModal] = useState(false);

  const fetchAppUserRow = async (userId) => {
    if (!userId) return null;
    const columns = 'id, name, email, phone, avatar_url, user_code';
    let { data, error } = await supabase
      .from('app_users')
      .select(columns)
      .eq('id', userId)
      .maybeSingle();
    if (error && error.code !== 'PGRST116') throw error;
    if (!data) {
      try {
        const res2 = await supabase
          .from('app_users')
          .select(columns)
          .eq('auth_uid', userId)
          .maybeSingle();
        if (res2.error && res2.error.code !== 'PGRST116') throw res2.error;
        data = res2.data || null;
      } catch (_) {}
    }
    return data || null;
  };

  useEffect(() => {
    const fetchUserProfile = async () => {
      try {
        const { data: { session } } = await supabase.auth.getSession();
        if (session?.user) {
          const user = session.user;
          // Prefer Google/Gmail metadata from OAuth for name and avatar
          let displayName = user.user_metadata?.name || user.user_metadata?.full_name || user.email?.split('@')[0] || 'User';
          let avatarUrl = user.user_metadata?.avatar_url || user.user_metadata?.picture || null;
          let userCode = '';

          // Try to get more complete profile from app_users table (schema: id = auth.uid())
          let appUser = null;
          try {
            appUser = await fetchAppUserRow(user.id);
            if (appUser) {
              displayName = appUser.name || displayName;
              avatarUrl = appUser.avatar_url || avatarUrl;
              userCode = appUser.user_code || '';
            }
          } catch (err) {
            console.warn('Could not fetch from app_users:', err);
          }

          const resolvedEmail = appUser?.email || user.email || '';
          const resolvedPhone = appUser?.phone || '';
          setRequireContactModal(!resolvedEmail && !resolvedPhone);
          setUserProfile({
            name: displayName,
            email: resolvedEmail,
            phone: resolvedPhone,
            avatarUrl: avatarUrl,
            userCode: userCode
          });
        }
      } catch (error) {
        console.warn('Failed to fetch user profile:', error);
      }
    };

    fetchUserProfile();
  }, []);

  // Fetch unread notifications count
  useEffect(() => {
    const loadUnread = async () => {
      try {
        const { data: { session } } = await supabase.auth.getSession();
        const userId = session?.user?.id;
        if (!userId) { setUnreadCount(0); return; }
        const { count, error } = await supabase
          .from('notifications')
          .select('id', { count: 'exact', head: true })
          .eq('receiver_user_id', userId)
          .eq('read', false);
        if (error) throw error;
        setUnreadCount(count || 0);
      } catch (e) {
        console.warn('Failed to get unread notifications count', e);
      }
    };
    loadUnread();
  }, [notificationsOpen]);

  const toggleSideMenu = () => setSideMenuOpen((v) => !v);
  const closeSideMenu = () => setSideMenuOpen(false);

  // Generate avatar from email using a simple hash-based color
  const getAvatarFromEmail = (seed) => {
    const basis = seed || userProfile.name || 'User';
    if (!basis) return 'U';
    const hash = basis.split('').reduce((a, b) => {
      a = ((a << 5) - a) + b.charCodeAt(0);
      return a & a;
    }, 0);
    const colors = ['#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4', '#FFEAA7', '#DDA0DD', '#98D8C8'];
    const color = colors[Math.abs(hash) % colors.length];
    const initial = basis.charAt(0).toUpperCase();
    
    return (
      <div style={{
        width: '50px', height: '50px', borderRadius: '50%',
        background: color, display: 'flex',
        alignItems: 'center', justifyContent: 'center', color: 'white',
        fontWeight: '700', fontSize: '1.2rem'
      }}>
        {initial}
      </div>
    );
  };

  return (
    <>
      {/* Top header */}
      <header>
        <div style={{ display: 'flex', alignItems: 'center', gap: '15px' }}>
          <button className="hamburger-btn" onClick={toggleSideMenu} aria-label="Open menu">
            <span></span>
            <span></span>
            <span></span>
          </button>
          <div className="header-logo">
            <img src={storeHeaderLogo} alt="Presyohan Logo" />
            <h2><span className="presyo">presyo</span><span className="han">han?</span></h2>
          </div>
        </div>
        <div className="header-icons">
          <button
            className="icon-btn"
            title="Notifications"
            onClick={async () => {
              try {
                const { data: { session } } = await supabase.auth.getSession();
                const userId = session?.user?.id;
                if (userId) {
                  const { data: unreadList } = await supabase
                    .from('notifications')
                    .select('id')
                    .eq('receiver_user_id', userId)
                    .eq('read', false)
                    .order('created_at', { ascending: false })
                    .limit(200);
                  const ids = (unreadList || []).map(n => n.id);
                  if (ids.length > 0) {
                    await supabase.rpc('mark_notifications_read', { p_notification_ids: ids });
                  }
                  // Confirm server-side state and update badge
                  const { count } = await supabase
                    .from('notifications')
                    .select('id', { count: 'exact', head: true })
                    .eq('receiver_user_id', userId)
                    .eq('read', false);
                  setUnreadCount(count || 0);
                  // Pass initial unread IDs to panel via window event for simplicity
                  window.dispatchEvent(new CustomEvent('presyohan:initialUnreadIds', { detail: ids }));
                }
              } catch (e) {
                console.warn('Failed to auto-mark notifications as read', e);
              } finally {
                setNotificationsOpen(true);
              }
            }}
            style={{ position: 'relative' }}
          >
            <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"/>
            </svg>
            {unreadCount > 0 && (
              <span style={{
                position: 'absolute',
                top: '-4px',
                right: '-4px',
                minWidth: '18px',
                height: '18px',
                borderRadius: '9px',
                background: '#ff3b30',
                color: '#fff',
                fontSize: '11px',
                fontWeight: 700,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                padding: '0 5px'
              }}>{unreadCount}</span>
            )}
          </button>
          <Link to="/profile" className="icon-btn" title="Profile">
            <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"/>
            </svg>
          </Link>
        </div>
      </header>

      {/* Side menu overlay and drawer */}
      <div className={`side-menu-overlay ${sideMenuOpen ? 'active' : ''}`} onClick={closeSideMenu}></div>
      <div className={`side-menu ${sideMenuOpen ? 'active' : ''}`} role="dialog" aria-modal="true">
        {/* Menu Header */}
        <div className="side-menu-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: '15px' }}>
            {/* Profile pic - use avatar URL or generated avatar */}
            {userProfile.avatarUrl ? (
              <img 
                src={userProfile.avatarUrl} 
                alt="Profile" 
                style={{
                  width: '50px', height: '50px', borderRadius: '50%',
                  objectFit: 'cover'
                }}
              />
            ) : (
              getAvatarFromEmail(userProfile.email)
            )}
            <div>
              <h3 style={{ margin: 0, fontSize: '1.1rem' }}>{userProfile.name}</h3>
              {userProfile.userCode && (
                <div style={{ 
                  fontSize: '0.85rem', 
                  color: 'rgba(255, 255, 255, 0.85)', 
                  fontWeight: 'normal',
                  marginTop: '2px',
                  marginBottom: '2px',
                  letterSpacing: '0.5px'
                }}>
                  ID: {userProfile.userCode.toUpperCase()}
                </div>
              )}
              <Link to="/profile" onClick={closeSideMenu} style={{ color: 'white', textDecoration: 'underline', fontSize: '0.9rem' }}>View Profile</Link>
            </div>
          </div>
        </div>

        {/* Menu Items */}
        <div className="side-menu-items">
          {/* Stores with dropdown */}
          <button className="menu-item" onClick={() => setStoresExpanded((v) => !v)} style={{ width: '100%', background: 'none', border: 'none', textAlign: 'left' }}>
            <svg fill="currentColor" viewBox="0 0 24 24">
              <path d="M3 21h18v-2H3v2zm0-4h18v-2H3v2zm0-4h18v-2H3v2zm0-4h18V7H3v2zm0-6v2h18V3H3z"/>
            </svg>
            <span>Stores</span>
            <svg className={`submenu-toggle-caret ${storesExpanded ? 'expanded' : ''}`} width="18" height="18" viewBox="0 0 24 24" fill="currentColor" style={{ marginLeft: 'auto' }}>
              <path d="M7 10l5 5 5-5H7z"/>
            </svg>
          </button>
          {storesExpanded && (
            <div className="submenu">
              {Array.isArray(stores) && stores.length > 0 ? (
                <>
                  {includeAllStoresLink && (
                    <Link to="/stores" className="submenu-item" onClick={closeSideMenu}>
                      <img src={storeIcon} alt="Stores" className="submenu-store-icon" />
                      <span>All Stores</span>
                    </Link>
                  )}
                  {stores.map((s) => (
                    <Link
                      key={s.id ?? s.name}
                      to={`/store?id=${encodeURIComponent(s.id ?? '')}`}
                      className="submenu-item"
                      onClick={closeSideMenu}
                    >
                      <img src={storeIcon} alt="Store" className="submenu-store-icon" />
                      <span>{s.name}{s.branch ? ` — ${s.branch}` : ''}</span>
                    </Link>
                  ))}
                </>
              ) : (
                <>
                  <Link to="/stores" className="submenu-item" onClick={closeSideMenu}>
                    <img src={storeIcon} alt="Stores" className="submenu-store-icon" />
                    <span>All Stores</span>
                  </Link>
                  <Link to="/store" className="submenu-item" onClick={closeSideMenu}>
                    <img src={storeIcon} alt="Shop" className="submenu-store-icon" />
                    <span>Shop Products</span>
                  </Link>
                </>
              )}
            </div>
          )}

          {/* Notifications */}
          <a href="#" className="menu-item">
            <svg fill="currentColor" viewBox="0 0 24 24">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"/>
            </svg>
            <span>Notifications</span>
          </a>

          {/* Settings */}
          <a href="#" className="menu-item">
            <svg fill="currentColor" viewBox="0 0 24 24">
              <path d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.07.62-.07.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17 .47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22 .08 .47 0 .59-.22l1.92-3.32c.12-.22 .07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"/>
            </svg>
            <span>Settings</span>
          </a>

          {/* Support */}
          <a href="#" className="menu-item">
            <svg fill="currentColor" viewBox="0 0 24 24">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"/>
            </svg>
            <span>Support</span>
          </a>

          <div className="menu-divider"></div>

          {/* Logout */}
          <a
            href="#"
            className="menu-item"
            onClick={async (e) => {
              e.preventDefault();
              try {
                if (onLogout) {
                  await onLogout();
                } else {
                  await supabase.auth.signOut();
                  window.location.href = '/login';
                }
              } catch (err) {
                console.error('Logout failed', err);
                // Fallback redirect even if signOut fails
                window.location.href = '/login';
              }
            }}
          >
            <svg fill="currentColor" viewBox="0 0 24 24">
              <path d="M17 7l-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5zM4 5h8V3H4c-1.1 0-2 .9-2 2v14c0 1.1 .9 2 2 2h8v-2H4V5z"/>
            </svg>
            <span>Logout</span>
          </a>
        </div>
      </div>

      {requireContactModal && (
        <div style={{
          position: 'fixed',
          inset: 0,
          background: 'rgba(0,0,0,0.45)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 2500
        }}>
          <div style={{
            width: '92%',
            maxWidth: '440px',
            background: '#fff',
            borderRadius: '16px',
            boxShadow: '0 12px 30px rgba(0,0,0,0.2)',
            padding: '28px'
          }}>
            <h3 style={{ marginTop: 0, marginBottom: '12px', fontSize: '1.2rem', fontWeight: 700, color: '#ff8c00' }}>
              Complete Your Profile
            </h3>
            <p style={{ fontSize: '0.95rem', color: '#555', lineHeight: 1.5, marginBottom: '24px' }}>
              Add either an email or phone number so we can send staff invites and important updates.
            </p>
            <button
              onClick={() => {
                setRequireContactModal(false);
                closeSideMenu();
                navigate('/profile', { state: { startProfileEdit: true, tab: 'overview' } });
              }}
              style={{
                width: '100%',
                padding: '14px',
                background: 'linear-gradient(135deg, #ffb800 0%, #ff8c00 100%)',
                border: 'none',
                borderRadius: '25px',
                color: '#fff',
                fontSize: '1rem',
                fontWeight: 700,
                cursor: 'pointer'
              }}
            >
              Edit Profile
            </button>
          </div>
        </div>
      )}

      {/* Notifications Panel */}
      <NotificationsPanel open={notificationsOpen} onClose={() => setNotificationsOpen(false)} />
    </>
  );
}
