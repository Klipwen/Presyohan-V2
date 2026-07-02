import React, { useState, useEffect } from 'react';
import { supabase } from '../../config/supabaseClient';
import launcherImg from '../../assets/icon_presyohan_launcher.png';

export default function Announcements() {
  const [announcements, setAnnouncements] = useState([]);
  const [loading, setLoading] = useState(true);
  
  // Editor form state
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [isActive, setIsActive] = useState(true);
  const [editingId, setEditingId] = useState(null);
  const [buttonLabel, setButtonLabel] = useState('Close');
  const [showPreview, setShowPreview] = useState(false);
  
  const [actionLoading, setActionLoading] = useState(null); // 'submit' or announcementId

  const loadAnnouncements = async () => {
    try {
      setLoading(true);
      const { data, error } = await supabase
        .from('announcements')
        .select('*')
        .order('created_at', { ascending: false });

      if (error) throw error;
      setAnnouncements(data || []);
    } catch (err) {
      console.error('Failed to load announcements:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadAnnouncements();
  }, []);

  const handleToggleStatus = async (item) => {
    try {
      setActionLoading(item.id);
      const newStatus = !item.is_active;
      const { error } = await supabase
        .from('announcements')
        .update({ is_active: newStatus })
        .eq('id', item.id);

      if (error) throw error;
      setAnnouncements(prev => prev.map(a => a.id === item.id ? { ...a, is_active: newStatus } : a));
    } catch (err) {
      console.error('Failed to toggle announcement status:', err);
      alert('Error updating status: ' + err.message);
    } finally {
      setActionLoading(null);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!title.trim() || !content.trim()) return;

    try {
      setActionLoading('submit');
      const { data: { user } } = await supabase.auth.getUser();

      const payload = {
        title: title.trim(),
        content: content.trim(),
        is_active: isActive,
        button_label: buttonLabel.trim() || 'Close',
        created_by: user?.id || null
      };

      let query;
      if (editingId) {
        query = supabase
          .from('announcements')
          .update(payload)
          .eq('id', editingId);
      } else {
        query = supabase
          .from('announcements')
          .insert(payload);
      }

      const { error } = await query;
      if (error) throw error;

      setTitle('');
      setContent('');
      setIsActive(true);
      setButtonLabel('Close');
      setEditingId(null);
      await loadAnnouncements();
    } catch (err) {
      console.error('Failed to submit announcement:', err);
      alert('Error saving announcement: ' + err.message);
    } finally {
      setActionLoading(null);
    }
  };

  const handleEdit = (item) => {
    setEditingId(item.id);
    setTitle(item.title);
    setContent(item.content);
    setIsActive(item.is_active);
    setButtonLabel(item.button_label || 'Close');
  };

  const handleCopyAsNew = (item) => {
    setEditingId(null);
    setTitle(item.title);
    setContent(item.content);
    setIsActive(true);
    setButtonLabel(item.button_label || 'Close');
    
    // Smooth scroll the broadcaster form into view
    const formElement = document.querySelector('form');
    if (formElement) {
      formElement.scrollIntoView({ behavior: 'smooth' });
    }
  };

  const handleDelete = async (item) => {
    if (!window.confirm(`Are you sure you want to delete announcement "${item.title}"?`)) {
      return;
    }

    try {
      setActionLoading(item.id);
      const { error } = await supabase
        .from('announcements')
        .delete()
        .eq('id', item.id);

      if (error) throw error;
      setAnnouncements(prev => prev.filter(a => a.id !== item.id));
      if (editingId === item.id) {
        setTitle('');
        setContent('');
        setIsActive(true);
        setButtonLabel('Close');
        setEditingId(null);
      }
    } catch (err) {
      console.error('Failed to delete announcement:', err);
      alert('Error deleting announcement: ' + err.message);
    } finally {
      setActionLoading(null);
    }
  };

  if (loading) {
    return (
      <div style={{ padding: '40px', textAlign: 'center', color: '#64748b' }}>
        Loading active system announcements...
      </div>
    );
  }

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '1.2fr 1fr', gap: '32px', alignItems: 'start' }}>
      {/* Announcements List */}
      <div className="admin-card" style={{ padding: '24px' }}>
        <h3 style={{ marginBottom: '16px' }}>Active Broadcasts</h3>
        <div className="admin-table-container">
          <table className="admin-table">
            <thead>
              <tr>
                <th>Announcement</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {announcements.map((item) => (
                <tr key={item.id}>
                  <td>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      <span style={{ fontWeight: 700, color: '#0f172a' }}>{item.title}</span>
                      <span style={{ 
                        fontSize: '0.8rem', 
                        color: '#64748b', 
                        maxWidth: '220px', 
                        display: '-webkit-box',
                        WebkitLineClamp: 2,
                        WebkitBoxOrient: 'vertical',
                        overflow: 'hidden'
                      }}>
                        {item.content}
                      </span>
                      <span style={{ fontSize: '0.7rem', color: '#94a3b8', marginTop: '2px' }}>
                        Published: {new Date(item.created_at).toLocaleDateString()}
                      </span>
                    </div>
                  </td>
                  <td>
                    <span 
                      className={`admin-badge ${item.is_active ? 'active' : 'suspended'}`}
                      style={{ cursor: 'pointer' }}
                      onClick={() => handleToggleStatus(item)}
                      title="Click to toggle status"
                    >
                      {actionLoading === item.id ? '...' : (item.is_active ? 'Active' : 'Inactive')}
                    </span>
                  </td>
                  <td>
                    <div className="admin-actions-cell">
                      <button className="admin-btn-action" onClick={() => handleEdit(item)}>
                        Edit
                      </button>
                      <button 
                        className="admin-btn-action" 
                        style={{ border: '1px solid #fb8500', color: '#fb8500' }}
                        onClick={() => handleCopyAsNew(item)}
                      >
                        Broadcast Again
                      </button>
                      <button 
                        className="admin-btn-action suspend" 
                        disabled={actionLoading !== null}
                        onClick={() => handleDelete(item)}
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {announcements.length === 0 && (
                <tr>
                  <td colSpan="3" style={{ textAlign: 'center', color: '#94a3b8', padding: '32px' }}>
                    No announcements broadcasted yet. Write one on the right side to publish.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Broadcaster Editor Form */}
      <div className="admin-card" style={{ padding: '24px' }}>
        <h3 style={{ marginBottom: '16px' }}>{editingId ? 'Edit Announcement' : 'Broadcast New Announcement'}</h3>
        <form onSubmit={handleSubmit}>
          <div style={{ marginBottom: '12px' }}>
            <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#475569', marginBottom: '6px' }}>Broadcast Title</label>
            <input
              type="text"
              required
              className="admin-search-input"
              style={{ paddingLeft: '16px' }}
              placeholder="e.g. Server Maintenance Notice"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
            />
          </div>

          <div style={{ marginBottom: '16px' }}>
            <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#475569', marginBottom: '6px' }}>Announcement Body</label>
            <textarea
              className="admin-search-input"
              style={{ paddingLeft: '16px', height: '120px', resize: 'none', paddingTop: '10px' }}
              placeholder="Write the full broadcast content here. This will be displayed to all users upon launching the application..."
              value={content}
              onChange={(e) => setContent(e.target.value)}
              required
            />
          </div>

          <div style={{ marginBottom: '16px' }}>
            <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#475569', marginBottom: '6px' }}>Button Label</label>
            <input
              type="text"
              required
              className="admin-search-input"
              style={{ paddingLeft: '16px' }}
              placeholder="e.g. Close"
              value={buttonLabel}
              onChange={(e) => setButtonLabel(e.target.value)}
            />
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '20px' }}>
            <input
              type="checkbox"
              id="isActive"
              checked={isActive}
              onChange={(e) => setIsActive(e.target.checked)}
              style={{ width: '16px', height: '16px', cursor: 'pointer' }}
            />
            <label htmlFor="isActive" style={{ fontSize: '0.85rem', color: '#334155', fontWeight: 600, cursor: 'pointer' }}>
              Publish Active (visible in client applications)
            </label>
          </div>

          <button
            type="button"
            onClick={() => setShowPreview(true)}
            style={{
              width: '100%',
              padding: '12px',
              backgroundColor: 'transparent',
              border: '2px solid #ff8c00',
              color: '#ff8c00',
              borderRadius: '8px',
              fontSize: '15px',
              fontWeight: 600,
              cursor: 'pointer',
              marginBottom: '12px',
              transition: 'background-color 0.2s'
            }}
          >
            Show Announcement Preview
          </button>

          <div style={{ display: 'flex', gap: '12px' }}>
            {editingId && (
              <button
                type="button"
                className="admin-btn-action"
                style={{ flex: 1 }}
                onClick={() => {
                  setEditingId(null);
                  setTitle('');
                  setContent('');
                  setIsActive(true);
                  setButtonLabel('Close');
                }}
              >
                Cancel Edit
              </button>
            )}
            <button
              type="submit"
              className="admin-btn-primary"
              style={{ flex: 2 }}
              disabled={actionLoading === 'submit'}
            >
              {actionLoading === 'submit' ? 'Publishing...' : (editingId ? 'Save Broadcast' : 'Publish Broadcast')}
            </button>
          </div>
        </form>
      </div>

      {/* Live Broadcast Preview Modal */}
      {showPreview && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(15, 23, 42, 0.75)',
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          zIndex: 9999,
          backdropFilter: 'blur(4px)'
        }}>
          <div style={{
            position: 'relative',
            width: '420px',
            height: '420px',
            backgroundColor: '#fff',
            borderRadius: '24px',
            padding: '24px',
            boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.5)',
            display: 'flex',
            flexDirection: 'column',
            boxSizing: 'border-box',
            fontFamily: 'Inter, system-ui, -apple-system, sans-serif'
          }}>
            {/* Header Logo and Label */}
            <div style={{
              display: 'flex',
              alignItems: 'center',
              marginBottom: '16px'
            }}>
              <img 
                src={launcherImg} 
                alt="Presyohan Logo" 
                style={{ width: '32px', height: '32px', objectFit: 'contain' }} 
              />
              <div style={{
                display: 'flex',
                flexDirection: 'column',
                marginLeft: '6px',
                lineHeight: 1
              }}>
                <span style={{
                  fontSize: '8px',
                  fontWeight: 'bold',
                  color: '#FFEB3B',
                  marginBottom: '-4px',
                  textTransform: 'lowercase'
                }}>atong</span>
                <div style={{ display: 'flex', fontWeight: 'bold', fontSize: '22px' }}>
                  <span style={{ color: '#FB8500' }}>presyo</span>
                  <span style={{ color: '#219EBC' }}>han?</span>
                </div>
              </div>
            </div>

            {/* Light Blue Rounded Container for Header and Body */}
            <div style={{
              flex: 1,
              backgroundColor: '#EDF7FA',
              borderRadius: '20px',
              padding: '20px',
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'center',
              alignItems: 'center',
              textAlign: 'center',
              marginBottom: '20px',
              boxSizing: 'border-box',
              overflowY: 'auto'
            }}>
              <h3 style={{
                fontSize: '22px',
                fontWeight: 'bold',
                color: '#6B7280',
                margin: '0 0 10px 0',
                wordBreak: 'break-word'
              }}>{title || 'Header'}</h3>
              
              <p style={{
                fontSize: '15px',
                color: '#8A8A8A',
                margin: 0,
                lineHeight: 1.5,
                wordBreak: 'break-word'
              }}>{content || 'Body texts'}</p>
            </div>

            {/* Teal Action Button */}
            <button type="button" style={{
              width: '100%',
              height: '48px',
              backgroundColor: '#219EBC',
              color: '#fff',
              border: 'none',
              borderRadius: '24px',
              fontSize: '16px',
              fontWeight: 'bold',
              cursor: 'pointer',
              boxShadow: '0 4px 10px rgba(33, 158, 188, 0.3)'
            }}>
              {buttonLabel || 'Close'}
            </button>

            {/* Close button outside preview */}
            <button 
              type="button"
              onClick={() => setShowPreview(false)}
              style={{
                position: 'absolute',
                top: '-20px',
                right: '-20px',
                width: '40px',
                height: '40px',
                borderRadius: '50%',
                backgroundColor: '#fff',
                border: 'none',
                color: '#334155',
                fontSize: '20px',
                fontWeight: 'bold',
                cursor: 'pointer',
                display: 'flex',
                justifyContent: 'center',
                alignItems: 'center',
                boxShadow: '0 4px 10px rgba(0,0,0,0.1)'
              }}
            >
              ×
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
