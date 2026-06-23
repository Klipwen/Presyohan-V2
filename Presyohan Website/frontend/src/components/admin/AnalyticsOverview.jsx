import React, { useState, useEffect } from 'react';
import { supabase } from '../../config/supabaseClient';

export default function AnalyticsOverview({ setActiveTab }) {
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState({
    totalUsers: 0,
    activeUsers: 0,
    totalStores: 0,
    activeAnnouncements: 0
  });
  const [recentUsers, setRecentUsers] = useState([]);

  const fetchAnalytics = async () => {
    try {
      setLoading(true);
      
      // 1. Fetch Total Users
      const { count: userCount, error: userErr } = await supabase
        .from('app_users')
        .select('*', { count: 'exact', head: true });
        
      // 2. Fetch Active Users (last 1 hour)
      const oneHourAgo = new Date(Date.now() - 60 * 60 * 1000).toISOString();
      const { count: activeCount, error: activeErr } = await supabase
        .from('app_users')
        .select('*', { count: 'exact', head: true })
        .gt('last_activity_at', oneHourAgo);

      // 3. Fetch Total Stores
      const { count: storeCount, error: storeErr } = await supabase
        .from('stores')
        .select('*', { count: 'exact', head: true });

      // 4. Fetch Active Announcements
      const { count: announceCount, error: announceErr } = await supabase
        .from('announcements')
        .select('*', { count: 'exact', head: true })
        .eq('is_active', true);

      // 5. Fetch Recent active users list
      const { data: recentData, error: recentErr } = await supabase
        .from('app_users')
        .select('id, name, email, avatar_url, last_activity_at')
        .order('last_activity_at', { ascending: false })
        .limit(5);

      if (userErr || activeErr || storeErr || announceErr || recentErr) {
        throw new Error('Some analytics queries failed to load.');
      }

      setStats({
        totalUsers: userCount || 0,
        activeUsers: activeCount || 0,
        totalStores: storeCount || 0,
        activeAnnouncements: announceCount || 0
      });

      setRecentUsers(recentData || []);
    } catch (err) {
      console.error('Error fetching analytics details:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAnalytics();
    
    // Refresh stats every 30 seconds for live monitoring
    const interval = setInterval(fetchAnalytics, 30000);
    return () => clearInterval(interval);
  }, []);

  const formatTimeAgo = (isoString) => {
    if (!isoString) return 'Never';
    const date = new Date(isoString);
    const seconds = Math.floor((new Date() - date) / 1000);

    if (seconds < 60) return 'Just now';
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) return `${minutes}m ago`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}h ago`;
    return date.toLocaleDateString();
  };

  if (loading && recentUsers.length === 0) {
    return (
      <div style={{ padding: '40px', textAlign: 'center', color: '#64748b', fontFamily: 'Outfit' }}>
        Loading statistics metrics...
      </div>
    );
  }

  return (
    <div>
      {/* Metrics Row */}
      <div className="metrics-grid">
        <div className="metric-card users">
          <div className="metric-info">
            <div className="metric-title">Total Users</div>
            <div className="metric-value">{stats.totalUsers}</div>
            <div className="metric-subtext">Registered user profiles</div>
          </div>
          <div className="metric-icon-wrapper">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
              <circle cx="9" cy="7" r="4" />
              <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
              <path d="M16 3.13a4 4 0 0 1 0 7.75" />
            </svg>
          </div>
        </div>

        <div className="metric-card active-users">
          <div className="metric-info">
            <div className="metric-title">Active Users</div>
            <div className="metric-value">{stats.activeUsers}</div>
            <div className="metric-subtext">Active within the last hour</div>
          </div>
          <div className="metric-icon-wrapper">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
              <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
              <circle cx="9" cy="7" r="4" />
              <polyline points="16 11 18 13 22 9" />
            </svg>
          </div>
        </div>

        <div className="metric-card stores">
          <div className="metric-info">
            <div className="metric-title">Total Stores</div>
            <div className="metric-value">{stats.totalStores}</div>
            <div className="metric-subtext">Registered store outlets</div>
          </div>
          <div className="metric-icon-wrapper">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
              <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
              <polyline points="9 22 9 12 15 12 15 22" />
            </svg>
          </div>
        </div>

        <div className="metric-card announcements">
          <div className="metric-info">
            <div className="metric-title">Active Broadcasts</div>
            <div className="metric-value">{stats.activeAnnouncements}</div>
            <div className="metric-subtext">Live broadcast notifications</div>
          </div>
          <div className="metric-icon-wrapper">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
              <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
              <path d="M13.73 21a2 2 0 0 1-3.46 0" />
            </svg>
          </div>
        </div>
      </div>

      {/* Main dashboard content */}
      <div className="dashboard-grid">
        {/* Left column: Quick shortcuts & system actions */}
        <div className="admin-card">
          <h3>Quick Administration Controls</h3>
          <p style={{ color: '#64748b', marginBottom: '28px', fontSize: '0.9rem', lineHeight: '1.6' }}>
            Welcome to the Presyohan administration portal. Use the sidebar menu options to manage users, stores, post broadcasts, and publish android APK releases.
          </p>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' }}>
            <div className="quick-action-card" onClick={() => setActiveTab('stores')}>
              <div className="quick-action-icon">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
                  <path d="M18.5 2.5a2.121 2.121 0 1 1 3 3L12 15l-4 1 1-4z" />
                </svg>
              </div>
              <div className="quick-action-title">Manage Store List</div>
              <div className="quick-action-desc">View catalogs, store pricing lists and members.</div>
            </div>

            <div className="quick-action-card" onClick={() => setActiveTab('announcements')}>
              <div className="quick-action-icon">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M22 2L11 13" />
                  <path d="M22 2l-7 20-4-9-9-4 20-7z" />
                </svg>
              </div>
              <div className="quick-action-title">Push System News</div>
              <div className="quick-action-desc">Broadcast notices to both web and mobile users.</div>
            </div>
          </div>
        </div>

        {/* Right column: Recent Activity Stream */}
        <div className="admin-card">
          <h3>Recent Active Users</h3>
          <div className="activity-list">
            {recentUsers.map((user) => (
              <div className="activity-item" key={user.id}>
                <div className="activity-icon" style={{
                  backgroundImage: user.avatar_url ? `url(${user.avatar_url})` : 'none',
                  backgroundSize: 'cover',
                  backgroundPosition: 'center',
                  fontWeight: '700',
                  color: user.avatar_url ? 'transparent' : '#ff8c00',
                  backgroundColor: user.avatar_url ? 'transparent' : 'rgba(255, 140, 0, 0.08)',
                  fontSize: '0.85rem'
                }}>
                  {!user.avatar_url && (user.name || user.email || 'U').slice(0, 2).toUpperCase()}
                </div>
                <div className="activity-details">
                  <div className="activity-user">{user.name || 'Anonymous User'}</div>
                  <div className="activity-desc" title={user.email}>{user.email}</div>
                </div>
                <div className="activity-time">
                  {formatTimeAgo(user.last_activity_at)}
                </div>
              </div>
            ))}
            {recentUsers.length === 0 && (
              <div style={{ textAlign: 'center', color: '#94a3b8', fontSize: '0.9rem', padding: '20px 0' }}>
                No active users recorded.
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
