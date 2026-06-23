import React, { useState, useEffect } from 'react';
import { supabase } from '../../config/supabaseClient';

export default function UserManagement() {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [roleFilter, setRoleFilter] = useState('all');
  const [statusFilter, setStatusFilter] = useState('all');
  const [currentAdminId, setCurrentAdminId] = useState(null);
  const [actionLoading, setActionLoading] = useState(null); // id of user currently mutating

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

  // Filter users based on query and selectors
  const filteredUsers = users.filter(user => {
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

    return matchesSearch && matchesRole && matchesStatus;
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

        <div style={{ display: 'flex', gap: '12px' }}>
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

      <div className="admin-table-container">
        <table className="admin-table">
          <thead>
            <tr>
              <th>Profile & Account</th>
              <th>Role</th>
              <th>Last Activity</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {filteredUsers.map((user) => (
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
                        {user.id === currentAdminId && <span style={{ color: '#ff8c00', fontSize: '0.8rem', marginLeft: '6px' }}>(You)</span>}
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
                <td>{formatLastActive(user.last_activity_at)}</td>
                <td>
                  <span className={`admin-badge ${user.is_suspended ? 'suspended' : 'active'}`}>
                    {user.is_suspended ? 'Suspended' : 'Active'}
                  </span>
                </td>
                <td>
                  <div className="admin-actions-cell">
                    <button
                      className={`admin-btn-action ${user.is_suspended ? 'unsuspend' : 'suspend'}`}
                      disabled={user.id === currentAdminId || actionLoading === user.id}
                      onClick={() => handleToggleSuspend(user)}
                    >
                      {actionLoading === user.id 
                        ? 'Updating...' 
                        : (user.is_suspended ? 'Unsuspend' : 'Suspend')}
                    </button>
                  </div>
                </td>
              </tr>
            ))}
            {filteredUsers.length === 0 && (
              <tr>
                <td colSpan="5" style={{ textAlign: 'center', color: '#94a3b8', padding: '32px' }}>
                  No registered users found matching the search criteria.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
