import React, { useState, useEffect } from 'react';
import { supabase } from '../../config/supabaseClient';

export default function AnalyticsOverview({ setActiveTab }) {
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState({
    totalUsers: 0,
    activeUsers: 0,
    totalStores: 0,
    activeAnnouncements: 0,
    avgRating: 0,
    totalRatings: 0
  });

  const [tierDistribution, setTierDistribution] = useState({
    free: 0,
    pro: 0,
    vip: 0,
    total: 0
  });

  const [ratingBreakdown, setRatingBreakdown] = useState({
    5: 0,
    4: 0,
    3: 0,
    2: 0,
    1: 0
  });

  const [recentUsers, setRecentUsers] = useState([]);

  const fetchAnalytics = async () => {
    try {
      setLoading(true);

      // 1. Fetch Total Users
      const { count: userCount, error: userErr } = await supabase
        .from('app_users')
        .select('*', { count: 'exact', head: true });

      // 2. Fetch Active Users (last 24 hours window)
      const twentyFourHoursAgo = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();
      const { count: activeCount, error: activeErr } = await supabase
        .from('app_users')
        .select('*', { count: 'exact', head: true })
        .gt('last_activity_at', twentyFourHoursAgo);

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
        .limit(6);

      // 6. Fetch Ratings list & compute distribution
      const { data: ratingData, error: ratingErr } = await supabase
        .from('app_ratings')
        .select('rating');

      let avgRating = 0;
      let totalRatings = 0;
      const starCounts = { 5: 0, 4: 0, 3: 0, 2: 0, 1: 0 };

      if (ratingData && ratingData.length > 0) {
        totalRatings = ratingData.length;
        let sum = 0;
        ratingData.forEach((r) => {
          const val = Math.round(Number(r.rating));
          if (val >= 1 && val <= 5) {
            starCounts[val] = (starCounts[val] || 0) + 1;
          }
          sum += Number(r.rating) || 0;
        });
        avgRating = sum / totalRatings;
      }

      // 7. Fetch Real Store Subscription Tiers
      const { data: storeTiersData, error: tiersErr } = await supabase
        .from('stores')
        .select('subscription_tier');

      let freeCount = 0;
      let proCount = 0;
      let vipCount = 0;

      if (storeTiersData && storeTiersData.length > 0) {
        storeTiersData.forEach((st) => {
          const tier = (st.subscription_tier || 'free').toLowerCase();
          if (tier === 'vip') {
            vipCount += 1;
          } else if (tier === 'pro') {
            proCount += 1;
          } else {
            freeCount += 1;
          }
        });
      } else if (storeCount) {
        freeCount = storeCount;
      }

      const totalTierStores = freeCount + proCount + vipCount;

      if (userErr || activeErr || storeErr || announceErr || recentErr || ratingErr) {
        console.warn('One or more analytics queries returned an error.');
      }

      setStats({
        totalUsers: userCount || 0,
        activeUsers: activeCount || 0,
        totalStores: storeCount || 0,
        activeAnnouncements: announceCount || 0,
        avgRating: Number(avgRating.toFixed(1)) || 0,
        totalRatings: totalRatings || 0
      });

      setTierDistribution({
        free: freeCount,
        pro: proCount,
        vip: vipCount,
        total: totalTierStores
      });

      setRatingBreakdown(starCounts);
      setRecentUsers(recentData || []);
    } catch (err) {
      console.error('Error fetching analytics details:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAnalytics();
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
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
  };

  const activeRatio = stats.totalUsers > 0
    ? Math.round((stats.activeUsers / stats.totalUsers) * 100)
    : 0;

  // Doughnut Math
  const radius = 50;
  const circumference = 2 * Math.PI * radius; // ~314.159
  const totalTierSum = tierDistribution.total || 1;

  const proPercent = Math.round((tierDistribution.pro / totalTierSum) * 100);
  const vipPercent = Math.round((tierDistribution.vip / totalTierSum) * 100);
  const freePercent = 100 - proPercent - vipPercent;

  const proDash = (tierDistribution.pro / totalTierSum) * circumference;
  const vipDash = (tierDistribution.vip / totalTierSum) * circumference;
  const freeDash = (tierDistribution.free / totalTierSum) * circumference;

  const proOffset = 0;
  const vipOffset = -proDash;
  const freeOffset = -(proDash + vipDash);

  return (
    <div className="analytics-overview-container">
      {/* 1. Real-Time System Pulse Banner */}
      <section className="analytics-pulse-banner">
        <div className="pulse-banner-title">
          <h2>Presyohan System Pulse</h2>
          <span className="pulse-live-badge">
            <span className="pulse-dot-active"></span>
            Live Monitoring
          </span>
        </div>
        <div className="pulse-banner-meta">
          <div className="pulse-meta-item" title="Active user calculation period">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="10" />
              <polyline points="12 6 12 12 16 14" />
            </svg>
            <span>24-Hour Active Window</span>
          </div>
          <div className="pulse-meta-item" title="Dashboard auto-refresh cycle">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="23 4 23 10 17 10" />
              <polyline points="1 20 1 14 7 14" />
              <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
            </svg>
            <span>Auto-refresh: 30s</span>
          </div>
        </div>
      </section>

      {/* 2. Executive KPI Cards Row (4 Cards) */}
      <section className="analytics-kpi-grid">
        {/* Total Users */}
        <div className="analytics-kpi-card">
          <div className="kpi-header">
            <span className="kpi-title">Total Users</span>
            <div className="kpi-icon-box users">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
                <circle cx="9" cy="7" r="4" />
                <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
                <path d="M16 3.13a4 4 0 0 1 0 7.75" />
              </svg>
            </div>
          </div>
          <div className="kpi-body">
            <div className="kpi-number">{loading && stats.totalUsers === 0 ? '...' : stats.totalUsers}</div>
            <div className="kpi-subtext">Registered user profiles</div>
          </div>
        </div>

        {/* 24h Active Users */}
        <div className="analytics-kpi-card">
          <div className="kpi-header">
            <span className="kpi-title">24h Active Users</span>
            <div className="kpi-icon-box active-dau">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
                <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
                <circle cx="9" cy="7" r="4" />
                <polyline points="16 11 18 13 22 9" />
              </svg>
            </div>
          </div>
          <div className="kpi-body">
            <div className="kpi-number">{loading && stats.activeUsers === 0 ? '...' : stats.activeUsers}</div>
            <div className="kpi-subtext">
              <span className="kpi-ratio-tag">{activeRatio}% active</span>
              <span>Past 24 hours</span>
            </div>
          </div>
        </div>

        {/* Total Stores */}
        <div className="analytics-kpi-card">
          <div className="kpi-header">
            <span className="kpi-title">Total Stores</span>
            <div className="kpi-icon-box stores">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
                <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
                <polyline points="9 22 9 12 15 12 15 22" />
              </svg>
            </div>
          </div>
          <div className="kpi-body">
            <div className="kpi-number">{loading && stats.totalStores === 0 ? '...' : stats.totalStores}</div>
            <div className="kpi-subtext">Registered store outlets</div>
          </div>
        </div>

        {/* Active Broadcasts */}
        <div className="analytics-kpi-card">
          <div className="kpi-header">
            <span className="kpi-title">Active Broadcasts</span>
            <div className="kpi-icon-box broadcasts">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
                <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
                <path d="M13.73 21a2 2 0 0 1-3.46 0" />
              </svg>
            </div>
          </div>
          <div className="kpi-body">
            <div className="kpi-number">{loading && stats.activeAnnouncements === 0 ? '...' : stats.activeAnnouncements}</div>
            <div className="kpi-subtext">Live system announcements</div>
          </div>
        </div>
      </section>

      {/* 3. Visual Analytics (2-Column Grid: Subscriptions Donut & Community Ratings) */}
      <section className="analytics-charts-grid">
        {/* Left: Subscription Tiers Doughnut Graph */}
        <div className="analytics-card">
          <div className="analytics-card-header">
            <h3>Subscription Tiers Breakdown</h3>
            <button
              className="analytics-header-btn"
              onClick={() => setActiveTab('subscriptions')}
              title="Open Subscriptions & Tiers"
            >
              Manage Tiers
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
                <line x1="5" y1="12" x2="19" y2="12" />
                <polyline points="12 5 19 12 12 19" />
              </svg>
            </button>
          </div>

          <div className="donut-chart-container">
            <div className="donut-svg-wrapper">
              <svg viewBox="0 0 140 140">
                {/* Background Ring */}
                <circle
                  cx="70"
                  cy="70"
                  r={radius}
                  fill="transparent"
                  stroke="#f1f5f9"
                  strokeWidth="16"
                />

                {tierDistribution.total > 0 ? (
                  <>
                    {/* Free Tier Slice */}
                    {tierDistribution.free > 0 && (
                      <circle
                        cx="70"
                        cy="70"
                        r={radius}
                        fill="transparent"
                        stroke="#94a3b8"
                        strokeWidth="16"
                        strokeDasharray={`${freeDash} ${circumference}`}
                        strokeDashoffset={freeOffset}
                        strokeLinecap="butt"
                      />
                    )}

                    {/* PRO Tier Slice */}
                    {tierDistribution.pro > 0 && (
                      <circle
                        cx="70"
                        cy="70"
                        r={radius}
                        fill="transparent"
                        stroke="#ff8c00"
                        strokeWidth="16"
                        strokeDasharray={`${proDash} ${circumference}`}
                        strokeDashoffset={proOffset}
                        strokeLinecap="butt"
                      />
                    )}

                    {/* VIP Tier Slice */}
                    {tierDistribution.vip > 0 && (
                      <circle
                        cx="70"
                        cy="70"
                        r={radius}
                        fill="transparent"
                        stroke="#00bcd4"
                        strokeWidth="16"
                        strokeDasharray={`${vipDash} ${circumference}`}
                        strokeDashoffset={vipOffset}
                        strokeLinecap="butt"
                      />
                    )}
                  </>
                ) : (
                  <circle
                    cx="70"
                    cy="70"
                    r={radius}
                    fill="transparent"
                    stroke="#94a3b8"
                    strokeWidth="16"
                  />
                )}
              </svg>

              <div className="donut-center-info">
                <span className="donut-center-val">{tierDistribution.total}</span>
                <span className="donut-center-label">Stores</span>
              </div>
            </div>

            <div className="donut-legend">
              <div className="donut-legend-row">
                <div className="donut-legend-left">
                  <span className="donut-legend-swatch pro"></span>
                  <span className="donut-legend-name">PRO Tier (₱99/mo)</span>
                </div>
                <div className="donut-legend-right">
                  <span className="donut-legend-count">{tierDistribution.pro}</span>
                  <span className="donut-legend-pct">{proPercent}%</span>
                </div>
              </div>

              <div className="donut-legend-row">
                <div className="donut-legend-left">
                  <span className="donut-legend-swatch vip"></span>
                  <span className="donut-legend-name">VIP Tier (₱299/mo)</span>
                </div>
                <div className="donut-legend-right">
                  <span className="donut-legend-count">{tierDistribution.vip}</span>
                  <span className="donut-legend-pct">{vipPercent}%</span>
                </div>
              </div>

              <div className="donut-legend-row">
                <div className="donut-legend-left">
                  <span className="donut-legend-swatch free"></span>
                  <span className="donut-legend-name">Free Plan</span>
                </div>
                <div className="donut-legend-right">
                  <span className="donut-legend-count">{tierDistribution.free}</span>
                  <span className="donut-legend-pct">{freePercent}%</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Right: Community Rating Distribution Horizontal Bar Chart */}
        <div className="analytics-card">
          <div className="analytics-card-header">
            <h3>Community Star Ratings</h3>
            <button
              className="analytics-header-btn"
              onClick={() => setActiveTab('feedback')}
              title="Open Feedback & Support"
            >
              View Feedback
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
                <line x1="5" y1="12" x2="19" y2="12" />
                <polyline points="12 5 19 12 12 19" />
              </svg>
            </button>
          </div>

          <div className="ratings-chart-container">
            <div className="ratings-score-card">
              <div className="ratings-score-value">
                {stats.avgRating > 0 ? stats.avgRating.toFixed(1) : '0.0'}
              </div>
              <div className="ratings-stars-row">
                {[1, 2, 3, 4, 5].map((star) => (
                  <svg
                    key={star}
                    viewBox="0 0 24 24"
                    fill={star <= Math.round(stats.avgRating) ? '#f59e0b' : '#e2e8f0'}
                  >
                    <path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z" />
                  </svg>
                ))}
              </div>
              <div className="ratings-total-sub">{stats.totalRatings} total reviews</div>
            </div>

            <div className="ratings-bars-list">
              {[5, 4, 3, 2, 1].map((stars) => {
                const count = ratingBreakdown[stars] || 0;
                const percent = stats.totalRatings > 0
                  ? Math.round((count / stats.totalRatings) * 100)
                  : 0;

                return (
                  <div className="rating-bar-item" key={stars}>
                    <span className="rating-star-label">
                      {stars}
                      <svg viewBox="0 0 24 24">
                        <path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z" />
                      </svg>
                    </span>
                    <div className="rating-bar-track">
                      <div className="rating-bar-fill" style={{ width: `${percent}%` }}></div>
                    </div>
                    <span className="rating-count-label">{count}</span>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </section>

      {/* 4. Operations Jump Hub & Recent User Activity Matrix */}
      <section className="analytics-bottom-grid">
        {/* Left: Operations Quick Shortcuts */}
        <div className="admin-card">
          <h3>Administration Operations Hub</h3>
          <p style={{ color: '#64748b', fontSize: '0.86rem', marginBottom: '18px', lineHeight: '1.5' }}>
            Direct jump to Presyohan modules to manage catalogs, pricing, subscriptions, releases, and broadcasts.
          </p>

          <div className="operations-tiles-grid">
            {/* Store Directory */}
            <div className="operations-tile" onClick={() => setActiveTab('stores')}>
              <div className="operations-tile-icon">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
                  <polyline points="9 22 9 12 15 12 15 22" />
                </svg>
              </div>
              <div className="operations-tile-content">
                <div className="operations-tile-title">Store Directory</div>
                <div className="operations-tile-desc">Manage store lists, owner links and members.</div>
              </div>
            </div>

            {/* Standard Price Stores */}
            <div className="operations-tile" onClick={() => setActiveTab('standard-stores')}>
              <div className="operations-tile-icon">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="12" cy="12" r="10" />
                  <line x1="12" y1="8" x2="12" y2="16" />
                  <line x1="8" y1="12" x2="16" y2="12" />
                </svg>
              </div>
              <div className="operations-tile-content">
                <div className="operations-tile-title">Standard Price Stores</div>
                <div className="operations-tile-desc">Administer official benchmark pricing outlets.</div>
              </div>
            </div>

            {/* Subscriptions & Tiers */}
            <div className="operations-tile" onClick={() => setActiveTab('subscriptions')}>
              <div className="operations-tile-icon">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
                  <rect x="1" y="4" width="22" height="16" rx="2" ry="2" />
                  <line x1="1" y1="10" x2="23" y2="10" />
                </svg>
              </div>
              <div className="operations-tile-content">
                <div className="operations-tile-title">Subscriptions & Tiers</div>
                <div className="operations-tile-desc">Configure tier prices, quotas, and overrides.</div>
              </div>
            </div>

            {/* System Announcements */}
            <div className="operations-tile" onClick={() => setActiveTab('announcements')}>
              <div className="operations-tile-icon">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
                  <path d="M13.73 21a2 2 0 0 1-3.46 0" />
                </svg>
              </div>
              <div className="operations-tile-content">
                <div className="operations-tile-title">System Announcements</div>
                <div className="operations-tile-desc">Broadcast alerts to web and mobile users.</div>
              </div>
            </div>

            {/* App Releases */}
            <div className="operations-tile" onClick={() => setActiveTab('releases')}>
              <div className="operations-tile-icon">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                  <polyline points="7 10 12 15 17 10" />
                  <line x1="12" y1="15" x2="12" y2="3" />
                </svg>
              </div>
              <div className="operations-tile-content">
                <div className="operations-tile-title">App Releases (APK)</div>
                <div className="operations-tile-desc">Publish and distribute Android mobile builds.</div>
              </div>
            </div>

            {/* Feedback & Support */}
            <div className="operations-tile" onClick={() => setActiveTab('feedback')}>
              <div className="operations-tile-icon">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                </svg>
              </div>
              <div className="operations-tile-content">
                <div className="operations-tile-title">Feedback & Support</div>
                <div className="operations-tile-desc">Manage support inquiries and contact coordinates.</div>
              </div>
            </div>
          </div>
        </div>

        {/* Right: Recent Active User Sessions (24h Activity Stream) */}
        <div className="admin-card">
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '16px' }}>
            <h3 style={{ margin: 0 }}>Recent User Sessions</h3>
            <button
              className="analytics-header-btn"
              onClick={() => setActiveTab('users')}
              title="Open User Management"
            >
              All Users
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
                <line x1="5" y1="12" x2="19" y2="12" />
                <polyline points="12 5 19 12 12 19" />
              </svg>
            </button>
          </div>

          <div className="user-stream-list">
            {recentUsers.map((user) => (
              <div className="user-stream-item" key={user.id}>
                <div className="user-stream-left">
                  <div
                    className="user-stream-avatar"
                    style={{
                      backgroundImage: user.avatar_url ? `url(${user.avatar_url})` : 'none',
                      backgroundSize: 'cover',
                      backgroundPosition: 'center',
                      color: user.avatar_url ? 'transparent' : '#ff8c00',
                      backgroundColor: user.avatar_url ? 'transparent' : 'rgba(255, 140, 0, 0.1)'
                    }}
                  >
                    {!user.avatar_url && (user.name || user.email || 'U').slice(0, 2).toUpperCase()}
                  </div>
                  <div className="user-stream-info">
                    <div className="user-stream-name">{user.name || 'Anonymous User'}</div>
                    <div className="user-stream-email" title={user.email}>{user.email}</div>
                  </div>
                </div>
                <div className="user-stream-time">
                  {formatTimeAgo(user.last_activity_at)}
                </div>
              </div>
            ))}

            {recentUsers.length === 0 && (
              <div style={{ textAlign: 'center', color: '#94a3b8', fontSize: '0.85rem', padding: '24px 0' }}>
                No active user sessions recorded in the last 24 hours.
              </div>
            )}
          </div>
        </div>
      </section>
    </div>
  );
}
