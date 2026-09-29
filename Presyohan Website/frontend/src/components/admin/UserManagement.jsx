import React, { useState, useEffect } from 'react';
import { supabase } from '../../config/supabaseClient';
import SubscriptionOverrideModal from './SubscriptionOverrideModal.jsx';

export default function UserManagement() {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [roleFilter, setRoleFilter] = useState('all');
  const [statusFilter, setStatusFilter] = useState('all');
  const [subscriptionFilter, setSubscriptionFilter] = useState('all');
  const [currentAdminId, setCurrentAdminId] = useState(null);
  const [actionLoading, setActionLoading] = useState(null); // id of user currently mutating
  
  // Override Modal state
  const [selectedUserForOverride, setSelectedUserForOverride] = useState(null);
  const [isOverrideModalOpen, setIsOverrideModalOpen] = useState(false);

  const loadUsersAndAdmin = async () => {
    try {
      setLoading(true);
      
      // Get current admin user
      const { data: { user } } = await supabase.auth.getUser();
      if (user) {
        setCurrentAdminId(user.id);
      }

      // Fetch all registered users
      const { data, error } = await supabase
        .from('app_users')
        .select('*')
        .order('name', { ascending: true });

      if (error) throw error;
      setUsers(data || []);
    } catch (err) {
      console.error('Failed to load users directory:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadUsersAndAdmin();
  }, []);

  const handleToggleSuspend = async (user) => {
    if (user.id === currentAdminId) {
      alert('Security lock: You cannot suspend your own administrative account.');
      return;
    }
    const confirmMsg = user.is_suspended 
      ? `Are you sure you want to unsuspend account: ${user.name || user.email}?`
      : `Are you sure you want to suspend account: ${user.name || user.email}? Suspended users will be immediately logged out and blocked.`;
      
    if (!window.confirm(confirmMsg)) return;

    try {
      setActionLoading(user.id);
      const newStatus = !user.is_suspended;
      const { error } = await supabase
        .from('app_users')
        .update({ is_suspended: newStatus })
        .eq('id', user.id);

      if (error) throw error;

      // Update local state
      setUsers(prev => prev.map(u => u.id === user.id ? { ...u, is_suspended: newStatus } : u));
    } catch (err) {
      console.error('Failed to change user suspension status:', err);
      alert('Error updating suspension status: ' + err.message);
    } finally {
      setActionLoading(null);
    }
  };

  const handleChangeRole = async (user, newRole) => {
    if (user.id === currentAdminId && newRole !== 'admin') {
      alert('Security lock: You cannot demote yourself from administrative role.');
      return;
    }
    if (!window.confirm(`Are you sure you want to change the role of ${user.name || user.email} to '${newRole}'?`)) {
      return;
    }

    try {
      setActionLoading(user.id);
      const { error } = await supabase
        .from('app_users')
        .update({ role: newRole })
        .eq('id', user.id);

      if (error) throw error;

      // Update local state
      setUsers(prev => prev.map(u => u.id === user.id ? { ...u, role: newRole } : u));
    } catch (err) {
      console.error('Failed to change user role status:', err);
      alert('Error updating user role: ' + err.message);
    } finally {
      setActionLoading(null);
    }
  };

  const handleOpenOverride = (user) => {
    setSelectedUserForOverride(user);
    setIsOverrideModalOpen(true);
  };

  const handleOverrideSuccess = (updatedUser) => {
    setUsers((prev) =>
      prev.map((u) => (u.id === updatedUser.id ? { ...u, ...updatedUser } : u))
    );
  };

  // Helper to determine subscription state
  const getSubscriptionInfo = (user) => {
    const tier = (user.subscription_tier || 'free').toLowerCase();
    const expiresAt = user.subscription_expires_at ? new Date(user.subscription_expires_at) : null;
    const now = new Date();

    if (tier === 'free') {
      return { tier: 'free', status: 'free', label: 'Free Tier', isExpired: false, daysLeft: null };
    }

    if (!expiresAt) {
      return {
        tier,
        status: 'lifetime',
        label: `${tier.toUpperCase()} • Lifetime`,
        isExpired: false,
        daysLeft: null
      };
    }

    if (expiresAt < now) {
      return {
        tier,
        status: 'expired',
        label: `${tier.toUpperCase()} (Expired)`,
        isExpired: true,
        daysLeft: 0,
        expiryDate: expiresAt
      };
    }

    const diffHours = Math.round((expiresAt - now) / (1000 * 60 * 60));
    const diffDays = Math.ceil(diffHours / 24);

    let timeText = `${diffDays}d left`;
    if (diffHours < 24) {
      timeText = `${diffHours}h left`;
    }

    return {
      tier,
      status: 'active_trial',
      label: `${tier.toUpperCase()} • ${timeText}`,
      isExpired: false,
      daysLeft: diffDays,
      expiryDate: expiresAt
    };
  };

  // Filter users based on query and selectors
  const filteredUsers = users.filter((user) => {
    const matchesSearch =
      (user.name || '').toLowerCase().includes(searchQuery.toLowerCase()) ||
      (user.email || '').toLowerCase().includes(searchQuery.toLowerCase()) ||
      (user.phone || '').includes(searchQuery);

    const matchesRole = roleFilter === 'all' || user.role === roleFilter;

    let matchesStatus = true;
    if (statusFilter === 'suspended') {
      matchesStatus = user.is_suspended === true;
    } else if (statusFilter === 'active') {
      matchesStatus = user.is_suspended !== true;
    }

    const subInfo = getSubscriptionInfo(user);
    let matchesSubscription = true;
    if (subscriptionFilter === 'vip') {
      matchesSubscription = subInfo.tier === 'vip';
    } else if (subscriptionFilter === 'pro') {
      matchesSubscription = subInfo.tier === 'pro';
    } else if (subscriptionFilter === 'free') {
      matchesSubscription = subInfo.tier === 'free';
    } else if (subscriptionFilter === 'lifetime') {
      matchesSubscription = subInfo.status === 'lifetime';
    } else if (subscriptionFilter === 'active_override') {
      matchesSubscription = subInfo.tier !== 'free' && !subInfo.isExpired;
    } else if (subscriptionFilter === 'expired') {
      matchesSubscription = subInfo.isExpired;
    }

    return matchesSearch && matchesRole && matchesStatus && matchesSubscription;
  });

  const formatLastActive = (isoString) => {
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

  if (loading) {
    return (
      <div style={{ padding: '40px', textAlign: 'center', color: '#64748b' }}>
        Loading user accounts directory...
      </div>
    );
  }

  return (
    <div>
      {/* Search & Multi-Filter Controls */}
      <div className="admin-table-controls">
        <div className="admin-search-wrapper">
          <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
          <input
            type="text"
            className="admin-search-input"
            placeholder="Search users by name, email..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>

        <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
          <select
            className="admin-select"
            value={subscriptionFilter}
            onChange={(e) => setSubscriptionFilter(e.target.value)}
          >
            <option value="all">All Plans &amp; Tiers</option>
            <option value="vip">VIP Tier</option>
            <option value="pro">PRO Tier</option>
            <option value="free">Free Tier</option>
            <option value="lifetime">Lifetime Access</option>
            <option value="active_override">Active Paid / Overrides</option>
            <option value="expired">Expired Overrides</option>
          </select>

          <select
            className="admin-select"
            value={roleFilter}
            onChange={(e) => setRoleFilter(e.target.value)}
          >
            <option value="all">All Roles</option>
            <option value="user">Regular User</option>
            <option value="admin">Administrator</option>
          </select>

          <select
            className="admin-select"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
          >
            <option value="all">All Statuses</option>
            <option value="active">Active</option>
            <option value="suspended">Suspended</option>
          </select>
        </div>
      </div>

      {/* Users Table */}
      <div className="admin-table-container">
        <table className="admin-table">
          <thead>
            <tr>
              <th>Profile &amp; Account</th>
              <th>Role</th>
              <th>Subscription Plan</th>
              <th>Last Activity</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {filteredUsers.map((user) => {
              const subInfo = getSubscriptionInfo(user);

              return (
                <tr key={user.id}>
                  <td>
                    <div className="admin-table-user">
                      <div
                        className="admin-table-avatar"
                        style={{
                          backgroundImage: user.avatar_url ? `url(${user.avatar_url})` : 'none',
                          backgroundSize: 'cover',
                          backgroundPosition: 'center',
                          color: user.avatar_url ? 'transparent' : '#ff8c00',
                          backgroundColor: user.avatar_url ? 'transparent' : 'rgba(255, 140, 0, 0.08)'
                        }}
                      >
                        {!user.avatar_url && (user.name || user.email || 'U').slice(0, 2).toUpperCase()}
                      </div>
                      <div className="admin-table-user-info">
                        <span className="admin-table-user-name">
                          {user.name || 'Anonymous User'}
                          {user.id === currentAdminId && (
                            <span style={{ color: '#ff8c00', fontSize: '0.8rem', marginLeft: '6px' }}>(You)</span>
                          )}
                        </span>
                        <span className="admin-table-user-email">{user.email}</span>
                      </div>
                    </div>
                  </td>
                  <td>
                    <select
                      className="admin-select"
                      style={{ padding: '6px 12px', fontSize: '0.82rem', height: 'auto' }}
                      value={user.role}
                      disabled={user.id === currentAdminId || actionLoading === user.id}
                      onChange={(e) => handleChangeRole(user, e.target.value)}
                    >
                      <option value="user">User</option>
                      <option value="admin">Admin</option>
                    </select>
                  </td>
                  <td>
                    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: '4px' }}>
                      <span
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '6px',
                          padding: '4px 10px',
                          borderRadius: '20px',
                          fontSize: '0.76rem',
                          fontWeight: 800,
                          backgroundColor:
                            subInfo.tier === 'vip'
                              ? 'rgba(255, 140, 0, 0.12)'
                              : subInfo.tier === 'pro'
                              ? 'rgba(0, 188, 212, 0.12)'
                              : 'rgba(100, 116, 139, 0.08)',
                          color:
                            subInfo.isExpired
                              ? '#DC2626'
                              : subInfo.tier === 'vip'
                              ? '#EA580C'
                              : subInfo.tier === 'pro'
                              ? '#0891B2'
                              : '#475569',
                          border:
                            subInfo.isExpired
                              ? '1px solid rgba(239, 68, 68, 0.2)'
                              : subInfo.tier === 'vip'
                              ? '1px solid rgba(255, 140, 0, 0.25)'
                              : subInfo.tier === 'pro'
                              ? '1px solid rgba(0, 188, 212, 0.25)'
                              : '1px solid rgba(100, 116, 139, 0.15)'
                        }}
                        title={
                          subInfo.expiryDate
                            ? `Expires: ${subInfo.expiryDate.toLocaleString()}`
                            : 'Permanent access'
                        }
                      >
                        {subInfo.tier === 'vip' && (
                          <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor">
                            <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z" />
                          </svg>
                        )}
                        {subInfo.tier === 'pro' && (
                          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                            <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
                          </svg>
                        )}
                        {subInfo.label}
                      </span>
                    </div>
                  </td>
                  <td>{formatLastActive(user.last_activity_at)}</td>
                  <td>
                    <span className={`admin-badge ${user.is_suspended ? 'suspended' : 'active'}`}>
                      {user.is_suspended ? 'Suspended' : 'Active'}
                    </span>
                  </td>
                  <td>
                    <div className="admin-actions-cell">
                      {/* Subscription Override Action Button */}
                      <button
                        type="button"
                        className="admin-btn-action"
                        style={{
                          backgroundColor: '#FFF7ED',
                          color: '#EA580C',
                          borderColor: '#FED7AA',
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '6px',
                          fontWeight: 700
                        }}
                        onClick={() => handleOpenOverride(user)}
                        title="Override subscription tier & duration"
                      >
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                          <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
                        </svg>
                        Override Plan
                      </button>

                      {/* Suspension Toggle */}
                      <button
                        className={`admin-btn-action ${user.is_suspended ? 'unsuspend' : 'suspend'}`}
                        disabled={user.id === currentAdminId || actionLoading === user.id}
                        onClick={() => handleToggleSuspend(user)}
                      >
                        {actionLoading === user.id
                          ? 'Updating...'
                          : user.is_suspended
                          ? 'Unsuspend'
                          : 'Suspend'}
                      </button>
                    </div>
                  </td>
                </tr>
              );
            })}
            {filteredUsers.length === 0 && (
              <tr>
                <td colSpan="6" style={{ textAlign: 'center', color: '#94a3b8', padding: '36px' }}>
                  No registered users found matching the search and filter criteria.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* Subscription Override Modal */}
      <SubscriptionOverrideModal
        user={selectedUserForOverride}
        isOpen={isOverrideModalOpen}
        onClose={() => {
          setIsOverrideModalOpen(false);
          setSelectedUserForOverride(null);
        }}
        onSuccess={handleOverrideSuccess}
      />
    </div>
  );
}
