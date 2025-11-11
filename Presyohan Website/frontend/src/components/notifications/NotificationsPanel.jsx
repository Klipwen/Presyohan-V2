import React, { useEffect, useMemo, useState } from 'react';
import { supabase } from '../../config/supabaseClient';
import '../../styles/NotificationsPanel.css';
import JoinRequestRoleModal from './JoinRequestRoleModal';

export default function NotificationsPanel({ open, onClose, initialUnreadIds = [] }) {
  const [loading, setLoading] = useState(false);
  const [notifications, setNotifications] = useState([]);
  const [error, setError] = useState(null);
  const [newIdsAtOpen, setNewIdsAtOpen] = useState(initialUnreadIds);
  const [roleModalOpen, setRoleModalOpen] = useState(false);
  const [activeJoinNotif, setActiveJoinNotif] = useState(null);
  const [activeTab, setActiveTab] = useState('all');

  const statusLabel = (n) => {
    // Map types and local outcomes to human status labels
    if (n.action_outcome === 'accept') return 'Accepted';
    if (n.action_outcome === 'reject') return 'Declined';
    switch (n.type) {
      case 'join_request':
      case 'store_invitation':
        return n.read ? 'Viewed' : 'Pending';
      case 'join_pending':
      case 'invitation_pending':
        return 'Sent';
      case 'join_accepted':
      case 'invitation_accepted':
        return 'Accepted';
      case 'join_rejected':
      case 'invitation_rejected':
        return 'Declined';
      case 'member_joined':
        return 'Joined';
      case 'member_left':
        return 'Left';
      default:
        return '';
    }
  };

  const joinTypes = ['join_request','join_pending','join_accepted','join_rejected'];
  const inviteTypes = ['store_invitation','invitation_pending','invitation_accepted','invitation_rejected'];
  const systemTypes = ['member_joined','member_left','member_removed','join_confirmed','leave_confirmed'];
  const filtered = useMemo(() => {
    switch (activeTab) {
      case 'join':
        return notifications.filter(n => joinTypes.includes(n.type));
      case 'invites':
        return notifications.filter(n => inviteTypes.includes(n.type));
      case 'system':
        return notifications.filter(n => systemTypes.includes(n.type));
      default:
        return notifications;
    }
  }, [notifications, activeTab]);

  useEffect(() => {
    let ignore = false;
    const fetchNotifications = async () => {
      if (!open) return;
      setLoading(true);
      setError(null);
      try {
        const { data: { session } } = await supabase.auth.getSession();
        const userId = session?.user?.id;
        if (!userId) {
          setNotifications([]);
          return;
        }
        const { data, error: qErr } = await supabase
          .from('notifications')
          .select('id, receiver_user_id, sender_user_id, store_id, type, title, message, read, read_at, created_at')
          .eq('receiver_user_id', userId)
          .order('created_at', { ascending: false })
          .limit(50);
        if (qErr) throw qErr;
        if (!ignore) setNotifications(data || []);
      } catch (e) {
        console.error('Failed loading notifications', e);
        if (!ignore) setError('Failed to load notifications.');
      } finally {
        if (!ignore) setLoading(false);
      }
    };
    fetchNotifications();
    return () => { ignore = true; };
  }, [open]);

  // Real-time subscription: listen for new or updated notifications for the current user
  useEffect(() => {
    if (!open) return;
    let channel;
    let mounted = true;
    (async () => {
      const { data: { session } } = await supabase.auth.getSession();
      const userId = session?.user?.id;
      if (!userId || !mounted) return;
      channel = supabase
        .channel(`notifications_${userId}`)
        .on(
          'postgres_changes',
          { event: 'INSERT', schema: 'public', table: 'notifications', filter: `receiver_user_id=eq.${userId}` },
          payload => {
            const n = payload.new;
            setNotifications(prev => {
              const exists = prev.find(x => x.id === n.id);
              if (exists) {
                return prev.map(x => x.id === n.id ? { ...x, ...n } : x);
              }
              return [n, ...prev];
            });
          }
        )
        .on(
          'postgres_changes',
          { event: 'UPDATE', schema: 'public', table: 'notifications', filter: `receiver_user_id=eq.${userId}` },
          payload => {
            const n = payload.new;
            setNotifications(prev => prev.map(x => x.id === n.id ? { ...x, ...n } : x));
          }
        )
        .subscribe();
    })();
    return () => {
      mounted = false;
      if (channel) supabase.removeChannel(channel);
    };
  }, [open]);

  // Listen for initial unread ids dispatched from the header when bell is clicked
  useEffect(() => {
    const handler = (e) => {
      const ids = Array.isArray(e.detail) ? e.detail : [];
      setNewIdsAtOpen(ids);
    };
    window.addEventListener('presyohan:initialUnreadIds', handler);
    return () => window.removeEventListener('presyohan:initialUnreadIds', handler);
  }, []);

  const unreadCount = useMemo(() => notifications.filter(n => !n.read).length, [notifications]);

  // No manual mark-all; indicators clear on bell click (handled in header)

  const handleAction = async (n, action) => {
    try {
      if (n.type === 'store_invitation') {
        const { error: rpcErr } = await supabase.rpc('handle_store_invitation', {
          p_notification_id: n.id,
          p_action: action,
        });
        if (rpcErr) throw rpcErr;
      } else if (n.type === 'join_request') {
        if (action === 'accept') {
          // Open role selection modal instead of immediate RPC
          setActiveJoinNotif(n);
          setRoleModalOpen(true);
          return;
        }
        if (action === 'reject') {
          const { error: rpcErr } = await supabase.rpc('handle_join_request', {
            p_notification_id: n.id,
            p_action: 'reject',
            p_role: 'employee',
          });
          if (rpcErr) throw rpcErr;
        }
      }
      // Update local card to reflect outcome and disable actions
      setNotifications(prev => prev.map(x => x.id === n.id ? { ...x, read: true, read_at: new Date().toISOString(), action_outcome: action } : x));
    } catch (e) {
      console.error('Action failed', e);
      setError('Failed to process action.');
    }
  };

  const acceptJoinRequestWithRole = async (roleChoice) => {
    if (!activeJoinNotif) return;
    try {
      const { error: rpcErr } = await supabase.rpc('handle_join_request', {
        p_notification_id: activeJoinNotif.id,
        p_action: 'accept',
        p_role: roleChoice || 'employee',
      });
      if (rpcErr) throw rpcErr;
      setNotifications(prev => prev.map(x => x.id === activeJoinNotif.id ? { ...x, read: true, read_at: new Date().toISOString() } : x));
    } catch (e) {
      console.error('Action failed', e);
      setError('Failed to process action.');
    } finally {
      setRoleModalOpen(false);
      setActiveJoinNotif(null);
    }
  };

  const formatRelative = (ts) => {
    const d = typeof ts === 'string' ? new Date(ts) : ts;
    const diff = Date.now() - d.getTime();
    const s = Math.floor(diff / 1000);
    if (s < 60) return `${s}s ago`;
    const m = Math.floor(s / 60);
    if (m < 60) return `${m}m ago`;
    const h = Math.floor(m / 60);
    if (h < 24) return `${h}h ago`;
    const days = Math.floor(h / 24);
    return `${days}d ago`;
  };

  return (
    <>
      <div className={`notifications-overlay ${open ? 'active' : ''}`} onClick={onClose}></div>
      <aside className={`notifications-panel ${open ? 'active' : ''}`} role="dialog" aria-modal="true" aria-label="Notifications">
        <div className="notifications-header">
          <div>
            <h3>Notifications</h3>
            <p>{unreadCount > 0 ? `${unreadCount} new` : 'All caught up'}</p>
          </div>
          <div className="notifications-header-actions">
            <button className="close-btn" onClick={onClose} aria-label="Close notifications" title="Close">×</button>
          </div>
        </div>
        <div className="notifications-tabs" role="tablist" aria-label="Notification categories">
          <button className={`tab ${activeTab === 'all' ? 'active' : ''}`} role="tab" aria-selected={activeTab==='all'} onClick={() => setActiveTab('all')}>All</button>
          <button className={`tab ${activeTab === 'join' ? 'active' : ''}`} role="tab" aria-selected={activeTab==='join'} onClick={() => setActiveTab('join')}>Join requests</button>
          <button className={`tab ${activeTab === 'invites' ? 'active' : ''}`} role="tab" aria-selected={activeTab==='invites'} onClick={() => setActiveTab('invites')}>Store invites</button>
          <button className={`tab ${activeTab === 'system' ? 'active' : ''}`} role="tab" aria-selected={activeTab==='system'} onClick={() => setActiveTab('system')}>System</button>
        </div>
        <div className="notifications-content">
          {loading && (
            <div className="loading">Loading notifications…</div>
          )}
          {!loading && error && (
            <div className="error">{error}</div>
          )}
          {!loading && !error && filtered.length === 0 && (
            <div className="empty">No notifications yet.</div>
          )}
          {!loading && !error && filtered.length > 0 && (
            <ul className="notifications-list">
              {filtered.map(n => (
                <li key={n.id} className={`notification-item ${n.read ? 'read' : 'unread'}`}>
                  <div className="notification-main">
                    <div className="notification-title-row">
                      <h4 className="title">
                        {n.title || (n.type || 'Notification')}
                        {newIdsAtOpen.includes(n.id) && (
                          <span className="new-indicator" aria-label="New notification">New</span>
                        )}
                      </h4>
                      <div className="meta-right">
                        {statusLabel(n) && <span className={`status-pill ${statusLabel(n).toLowerCase()}`}>{statusLabel(n)}</span>}
                        <span className="time">{formatRelative(n.created_at)}</span>
                      </div>
                    </div>
                    {n.message && <p className="message">{n.message}</p>}
                  </div>
                  <div className="notification-actions">
                    {n.type === 'store_invitation' && (
                      <>
                        {!n.action_outcome && (
                          <>
                            <button className="btn btn-primary" onClick={() => handleAction(n, 'accept')}>Accept</button>
                            <button className="btn btn-outline" onClick={() => handleAction(n, 'reject')}>Decline</button>
                          </>
                        )}
                      </>
                    )}
                    {n.type === 'join_request' && (
                      <>
                        {!n.action_outcome && (
                          <>
                            <button className="btn btn-primary" onClick={() => handleAction(n, 'accept')}>Accept</button>
                            <button className="btn btn-outline" onClick={() => handleAction(n, 'reject')}>Reject</button>
                          </>
                        )}
                      </>
                    )}
                    {(n.action_outcome === 'accept' || ['join_accepted','invitation_accepted'].includes(n.type)) && (
                      <a className="btn btn-link" href={`/store?id=${encodeURIComponent(n.store_id)}`}>View store</a>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </aside>
      <JoinRequestRoleModal
        open={roleModalOpen}
        onBack={() => { setRoleModalOpen(false); setActiveJoinNotif(null); }}
        onAccept={acceptJoinRequestWithRole}
        storeId={activeJoinNotif?.store_id}
        requesterMessage={activeJoinNotif?.message}
      />
    </>
  );
}