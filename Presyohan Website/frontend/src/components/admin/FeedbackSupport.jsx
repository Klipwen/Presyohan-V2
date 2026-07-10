import React, { useState, useEffect } from 'react';
import { supabase } from '../../config/supabaseClient';
import '../../styles/FeedbackSupport.css';

export default function FeedbackSupport() {
  const [activeTab, setActiveTab] = useState('comments');
  const [loading, setLoading] = useState(true);
  const [savingSettings, setSavingSettings] = useState(false);
  const [currentAdmin, setCurrentAdmin] = useState(null);

  // Settings state
  const [location, setLocation] = useState('');
  const [email, setEmail] = useState('');
  const [number, setNumber] = useState('');

  // Feed & Ratings state
  const [ratings, setRatings] = useState([]);
  const [messages, setMessages] = useState([]);
  const [usersMap, setUsersMap] = useState({});

  // Reply state
  const [replyTargetId, setReplyTargetId] = useState(null);
  const [replyText, setReplyText] = useState('');

  // Edit state (for admin's own replies)
  const [editingMsgId, setEditingMsgId] = useState(null);
  const [editingText, setEditingText] = useState('');

  const loadData = async () => {
    try {
      setLoading(true);

      // 1. Get current logged in admin
      const { data: { user } } = await supabase.auth.getUser();
      if (user) {
        setCurrentAdmin(user);
      }

      // 2. Fetch contact info configurations
      const { data: contactData, error: contactErr } = await supabase
        .from('app_contact_info')
        .select('*')
        .eq('id', 'default')
        .maybeSingle();

      if (contactErr) throw contactErr;
      if (contactData) {
        setLocation(contactData.location);
        setEmail(contactData.email);
        setNumber(contactData.number);
      }

      // 3. Fetch ratings
      const { data: ratingsData, error: ratingsErr } = await supabase
        .from('app_ratings')
        .select('*')
        .order('created_at', { ascending: false });

      if (ratingsErr) throw ratingsErr;
      setRatings(ratingsData || []);

      // 4. Fetch messages
      const { data: messagesData, error: messagesErr } = await supabase
        .from('contact_messages')
        .select('*')
        .order('created_at', { ascending: true });

      if (messagesErr) throw messagesErr;
      setMessages(messagesData || []);

      // 5. Fetch user profiles to map names and avatars
      const { data: profilesData, error: profilesErr } = await supabase
        .from('app_users')
        .select('id, name, email, avatar_url, role');

      if (profilesErr) throw profilesErr;
      const userMap = {};
      if (profilesData) {
        profilesData.forEach(profile => {
          userMap[profile.id] = profile;
        });
      }
      setUsersMap(userMap);

    } catch (err) {
      console.error('Failed to load Support details:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleSaveSettings = async (e) => {
    e.preventDefault();
    if (!location.trim() || !email.trim() || !number.trim()) {
      alert('Please fill in all contact details.');
      return;
    }

    try {
      setSavingSettings(true);
      const { error } = await supabase
        .from('app_contact_info')
        .upsert({
          id: 'default',
          location: location.trim(),
          email: email.trim(),
          number: number.trim(),
          updated_by: currentAdmin?.id
        });

      if (error) throw error;
      alert('Contact information updated successfully.');
    } catch (err) {
      console.error('Failed to save contact info settings:', err);
      alert('Error updating contact information: ' + err.message);
    } finally {
      setSavingSettings(false);
    }
  };

  const handlePostReply = async (parentId) => {
    if (!replyText.trim()) return;
    if (!currentAdmin) {
      alert('Error: Admin session not detected.');
      return;
    }

    try {
      // Find the parent message to get the original poster's user_id
      const parentMsg = messages.find(m => m.id === parentId);
      const receiverId = parentMsg ? parentMsg.user_id : null;

      const { error } = await supabase
        .from('contact_messages')
        .insert({
          user_id: currentAdmin.id,
          message: replyText.trim(),
          parent_id: parentId
        });

      if (error) throw error;

      // Send the user a support reply notification (if receiver is not the admin itself)
      if (receiverId && receiverId !== currentAdmin.id) {
        await supabase
          .from('notifications')
          .insert({
            receiver_user_id: receiverId,
            sender_user_id: currentAdmin.id,
            type: 'support_reply',
            title: 'Support Reply',
            message: `Presyohan replied to your feedback: "${replyText.trim().substring(0, 60)}${replyText.trim().length > 60 ? '...' : ''}"`
          });
      }

      setReplyText('');
      setReplyTargetId(null);
      await refreshMessages();
    } catch (err) {
      console.error('Failed to post administrative reply:', err);
      alert('Error posting reply: ' + err.message);
    }
  };

  const refreshMessages = async () => {
    const { data: freshMessages, error } = await supabase
      .from('contact_messages')
      .select('*')
      .order('created_at', { ascending: true });
    if (!error) setMessages(freshMessages || []);
  };

  const handleDeleteMessage = async (msgId) => {
    if (!window.confirm('Delete this message and all its replies?')) return;
    try {
      const { error } = await supabase
        .from('contact_messages')
        .delete()
        .eq('id', msgId);
      if (error) throw error;
      await refreshMessages();
    } catch (err) {
      console.error('Failed to delete message:', err);
      alert('Error deleting: ' + err.message);
    }
  };

  const handleSaveEdit = async (msgId) => {
    if (!editingText.trim()) return;
    try {
      const { error } = await supabase
        .from('contact_messages')
        .update({ message: editingText.trim() })
        .eq('id', msgId);
      if (error) throw error;
      setEditingMsgId(null);
      setEditingText('');
      await refreshMessages();
    } catch (err) {
      console.error('Failed to update message:', err);
      alert('Error saving edit: ' + err.message);
    }
  };

  const formatDate = (isoString) => {
    if (!isoString) return '';
    const date = new Date(isoString);
    return date.toLocaleString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  // Helper to render rating stars
  const renderStars = (rating) => {
    const stars = [];
    for (let i = 1; i <= 5; i++) {
      stars.push(
        <svg
          key={i}
          className={`rating-star-svg ${i > rating ? 'empty' : ''}`}
          viewBox="0 0 24 24"
        >
          <path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z" />
        </svg>
      );
    }
    return <div className="rating-stars-row">{stars}</div>;
  };

  // Build comment thread hierarchy
  const buildThreadTrees = () => {
    const roots = messages.filter(m => m.parent_id === null);
    
    const renderMessageCard = (msg, isNested = false) => {
      const author = usersMap[msg.user_id] || { name: 'User', email: msg.user_id.slice(0, 8), avatar_url: '' };
      const isOwnReply = currentAdmin && msg.user_id === currentAdmin.id;
      const isEditingThis = editingMsgId === msg.id;

      return (
        <div key={msg.id} className={`message-card ${isNested ? 'nested' : ''}`}>
          <img
            src={author.avatar_url || '/avatar_default.png'}
            alt="User Avatar"
            className="user-avatar"
            onError={(e) => { e.target.src = '/avatar_default.png'; }}
          />
          <div className="user-details" style={{ flex: 1 }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <span className="user-name">{author.name?.toUpperCase() || 'USER'}</span>
                {author.role === 'admin' && <span className="role-badge">ADMIN</span>}
                <span className="message-time">{formatDate(msg.created_at)}</span>
              </div>
              {/* Admin action buttons — delete for all, edit only own replies */}
              <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                {isOwnReply && !isEditingThis && (
                  <button
                    className="msg-action-btn edit"
                    onClick={() => { setEditingMsgId(msg.id); setEditingText(msg.message); }}
                    title="Edit reply"
                  >
                    ✏️ Edit
                  </button>
                )}
                <button
                  className="msg-action-btn delete"
                  onClick={() => handleDeleteMessage(msg.id)}
                  title="Delete message"
                >
                  🗑 Delete
                </button>
              </div>
            </div>

            {/* Inline Edit Mode */}
            {isEditingThis ? (
              <div className="reply-input-box" style={{ marginTop: '8px' }}>
                <textarea
                  value={editingText}
                  onChange={(e) => setEditingText(e.target.value)}
                  autoFocus
                />
                <div className="reply-actions-row">
                  <button
                    className="cancel-reply-btn"
                    onClick={() => { setEditingMsgId(null); setEditingText(''); }}
                  >
                    Cancel
                  </button>
                  <button
                    className="submit-reply-btn"
                    onClick={() => handleSaveEdit(msg.id)}
                  >
                    Save Changes
                  </button>
                </div>
              </div>
            ) : (
              <p className="message-text">{msg.message}</p>
            )}

            {/* Thread Reply action for root level messages */}
            {!isNested && !isEditingThis && replyTargetId !== msg.id && (
              <button
                className="thread-reply-btn"
                onClick={() => {
                  setReplyTargetId(msg.id);
                  setReplyText('');
                }}
              >
                Reply
              </button>
            )}

            {/* Render Reply input form inline */}
            {replyTargetId === msg.id && (
              <div className="reply-input-box">
                <textarea
                  placeholder="Type your reply as Administrator..."
                  value={replyText}
                  onChange={(e) => setReplyText(e.target.value)}
                  autoFocus
                />
                <div className="reply-actions-row">
                  <button
                    className="cancel-reply-btn"
                    onClick={() => {
                      setReplyTargetId(null);
                      setReplyText('');
                    }}
                  >
                    Cancel
                  </button>
                  <button
                    className="submit-reply-btn"
                    onClick={() => handlePostReply(msg.id)}
                  >
                    Send Reply
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      );
    };

    const threadNodes = [];

    roots.forEach(root => {
      threadNodes.push(
        <div key={root.id} className="thread-item">
          {renderMessageCard(root, false)}
          
          {/* Render children replies recursively */}
          {messages.filter(m => m.parent_id === root.id).map(child => (
            <React.Fragment key={child.id}>
              {renderMessageCard(child, true)}
              {/* Support level 2 reply nesting (user replying to admin reply) */}
              {messages.filter(m => m.parent_id === child.id).map(grandchild => 
                renderMessageCard(grandchild, true)
              )}
            </React.Fragment>
          ))}
        </div>
      );
    });

    return <div className="threads-list">{threadNodes}</div>;
  };

  if (loading) {
    return (
      <div style={{ padding: '40px', textAlign: 'center', color: '#64748b' }}>
        Loading support panel records...
      </div>
    );
  }

  return (
    <div className="feedback-container">
      {/* Left Panel: Settings Editor */}
      <div className="admin-card" style={{ padding: '24px' }}>
        <h4 className="feedback-section-title">
          <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" style={{ stroke: '#00bcd4' }}>
            <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" />
          </svg>
          Contact Information
        </h4>
        <form onSubmit={handleSaveSettings} className="settings-form">
          <div className="form-group">
            <label htmlFor="inputLocation">Physical Location</label>
            <input
              id="inputLocation"
              type="text"
              value={location}
              onChange={(e) => setLocation(e.target.value)}
              placeholder="e.g. Curva Medellin, Cebu City, Philippines"
            />
          </div>
          <div className="form-group">
            <label htmlFor="inputEmail">Support Email</label>
            <input
              id="inputEmail"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="e.g. support@presyohan.com"
            />
          </div>
          <div className="form-group">
            <label htmlFor="inputPhone">Contact Number</label>
            <input
              id="inputPhone"
              type="text"
              value={number}
              onChange={(e) => setNumber(e.target.value)}
              placeholder="e.g. +639 430 8387"
            />
          </div>
          <button type="submit" className="save-btn" disabled={savingSettings}>
            {savingSettings ? 'Saving Settings...' : 'Save Changes'}
          </button>
        </form>
      </div>

      {/* Right Panel: Feedbacks & Ratings Log */}
      <div className="admin-card" style={{ padding: '24px' }}>
        <div className="tab-row">
          <button
            className={`tab-btn ${activeTab === 'comments' ? 'active' : ''}`}
            onClick={() => setActiveTab('comments')}
          >
            Feedbacks &amp; Inquiries ({messages.filter(m => m.parent_id === null).length})
          </button>
          <button
            className={`tab-btn ${activeTab === 'ratings' ? 'active' : ''}`}
            onClick={() => setActiveTab('ratings')}
          >
            User Ratings ({ratings.length})
          </button>
        </div>

        {activeTab === 'ratings' ? (
          <div className="ratings-list">
            {ratings.length === 0 ? (
              <p style={{ color: '#64748b', fontSize: '0.9rem', textAlign: 'center', padding: '20px' }}>No user ratings submitted yet.</p>
            ) : (
              ratings.map(rating => {
                const author = usersMap[rating.user_id] || { name: 'User', email: rating.user_id.slice(0, 8), avatar_url: '' };
                return (
                  <div key={rating.id} className="rating-item">
                    <div className="rating-header">
                      <div className="user-info-block">
                        <img
                          src={author.avatar_url || '/avatar_default.png'}
                          alt="User Avatar"
                          className="user-avatar"
                          onError={(e) => { e.target.src = '/avatar_default.png'; }}
                        />
                        <div className="user-details">
                          <span className="user-name">{author.name?.toUpperCase() || 'USER'}</span>
                          <span className="user-email">{author.email}</span>
                        </div>
                      </div>
                      {renderStars(rating.rating)}
                    </div>
                    {rating.reason ? (
                      <p className="rating-reason">"{rating.reason}"</p>
                    ) : (
                      <p className="rating-reason" style={{ color: '#94a3b8', fontStyle: 'italic' }}>No reason feedback text provided.</p>
                    )}
                    <div className="rating-date">{formatDate(rating.created_at)}</div>
                  </div>
                );
              })
            )}
          </div>
        ) : (
          <div className="comments-thread-view">
            {messages.length === 0 ? (
              <p style={{ color: '#64748b', fontSize: '0.9rem', textAlign: 'center', padding: '20px' }}>No inquiries or comments posted yet.</p>
            ) : (
              buildThreadTrees()
            )}
          </div>
        )}
      </div>
    </div>
  );
}
