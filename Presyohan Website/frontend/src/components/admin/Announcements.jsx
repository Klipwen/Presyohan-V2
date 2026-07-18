import React, { useState, useEffect } from 'react';
import { supabase } from '../../config/supabaseClient';
import launcherImg from '../../assets/icon_presyohan_launcher.png';

export default function Announcements() {
  const [announcements, setAnnouncements] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('all'); // all, active, scheduled, inactive

  // Creator configuration state
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [isActive, setIsActive] = useState(true);
  const [buttonLabel, setButtonLabel] = useState('Close');
  const [editingId, setEditingId] = useState(null);

  // Template settings
  const [templateType, setTemplateType] = useState('wizard');
  const [templateData, setTemplateData] = useState({
    positive_label: 'OK',
    positive_action: '',
    negative_label: 'Later',
    negative_action: '',
    feedback_category: 'general',
    poll_options: ['Yes, definitely', 'No, not really'],
    allow_multiselect: false,
    steps: [
      { title: 'Welcome to Presyohan V2', content: 'Explore our brand new layout and features designed for convenience.', input_type: 'none', input_options: [], input_placeholder: '', input_required: false }
    ],
    version_name: '2.0.0',
    version_code: 20,
    whats_new: 'UI optimizations and bug fixes.',
    is_forced: false,
    download_url: '',
    ignore_new_user_cooldown: false
  });

  // Targeting details
  const [targetingType, setTargetingType] = useState('all');
  const [targetRoles, setTargetRoles] = useState([]);
  const [targetStoreId, setTargetStoreId] = useState('');
  const [targetUserId, setTargetUserId] = useState('');
  const [startAt, setStartAt] = useState('');
  const [endAt, setEndAt] = useState('');
  const [recurrencePattern, setRecurrencePattern] = useState('none');

  // Directory lists
  const [storesList, setStoresList] = useState([]);
  const [usersList, setUsersList] = useState([]);

  // Live Phone preview interactive state
  const [activePreviewSlide, setActivePreviewSlide] = useState(0);
  const [testRating, setTestRating] = useState(0);
  const [actionLoading, setActionLoading] = useState(null);
  const mockupBtnBg = templateData.button_color === 'orange' ? '#ff8c00' : '#219EBC';

  // Fetch announcements list
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

  // Fetch targeting stores & users lists
  const loadTargetingDirectories = async () => {
    try {
      const { data: stores } = await supabase
        .from('stores')
        .select('id, name, branch')
        .order('name', { ascending: true });
      setStoresList(stores || []);

      const { data: users } = await supabase
        .from('app_users')
        .select('id, name, email')
        .order('name', { ascending: true });
      setUsersList(users || []);
    } catch (err) {
      console.error('Failed to load directories:', err);
    }
  };

  useEffect(() => {
    loadAnnouncements();
    loadTargetingDirectories();
  }, []);

  const formatDatetimeForInput = (timestamp) => {
    if (!timestamp) return '';
    const date = new Date(timestamp);
    const tzoffset = date.getTimezoneOffset() * 60000;
    return (new Date(date.getTime() - tzoffset)).toISOString().slice(0, 16);
  };

  // Toggle active status
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
      console.error('Status update failed:', err);
      alert('Error updating status: ' + err.message);
    } finally {
      setActionLoading(null);
    }
  };

  // Edit action: populate creation fields
  const handleEdit = (item) => {
    setEditingId(item.id);
    setTitle(item.title);
    setContent(item.content);
    setIsActive(item.is_active);
    setButtonLabel(item.button_label || 'Close');
    setTemplateType(item.template_type || 'simple');
    
    setTemplateData({
      positive_label: 'OK',
      positive_action: '',
      negative_label: 'Later',
      negative_action: '',
      feedback_category: 'general',
      poll_options: ['Yes, definitely', 'No, not really'],
      allow_multiselect: false,
      steps: [],
      version_name: '2.0.0',
      version_code: 20,
      whats_new: 'UI optimizations and bug fixes.',
      is_forced: false,
      download_url: '',
      ignore_new_user_cooldown: false,
      ...(item.template_data || {})
    });

    setTargetingType(item.targeting_type || 'all');
    setTargetRoles(item.target_roles || []);
    setTargetStoreId(item.target_store_id || '');
    setTargetUserId(item.target_user_id || '');
    setStartAt(formatDatetimeForInput(item.start_at));
    setEndAt(formatDatetimeForInput(item.end_at));
    setRecurrencePattern(item.recurrence_pattern || 'none');
    setActivePreviewSlide(0);
    setTestRating(0);
    
    // Scroll editor to view
    const editor = document.getElementById('announcement-editor');
    if (editor) {
      editor.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  };

  // Copy as new broadcast template details
  const handleCopyAsNew = (item) => {
    setEditingId(null);
    setTitle(item.title);
    setContent(item.content);
    setIsActive(true);
    setButtonLabel(item.button_label || 'Close');
    setTemplateType(item.template_type || 'simple');
    setTemplateData({
      positive_label: 'OK',
      positive_action: '',
      negative_label: 'Later',
      negative_action: '',
      feedback_category: 'general',
      poll_options: ['Yes, definitely', 'No, not really'],
      allow_multiselect: false,
      steps: [],
      version_name: '2.0.0',
      version_code: 20,
      whats_new: 'UI optimizations and bug fixes.',
      is_forced: false,
      download_url: '',
      ...(item.template_data || {})
    });
    setTargetingType(item.targeting_type || 'all');
    setTargetRoles(item.target_roles || []);
    setTargetStoreId(item.target_store_id || '');
    setTargetUserId(item.target_user_id || '');
    setStartAt(formatDatetimeForInput(new Date()));
    setEndAt('');
    setRecurrencePattern(item.recurrence_pattern || 'none');
    setActivePreviewSlide(0);
    setTestRating(0);

    const editor = document.getElementById('announcement-editor');
    if (editor) {
      editor.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  };

  // Delete announcement
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
        handleResetForm();
      }
    } catch (err) {
      console.error('Delete failed:', err);
      alert('Error deleting announcement: ' + err.message);
    } finally {
      setActionLoading(null);
    }
  };

  const [showResponsesModal, setShowResponsesModal] = useState(false);
  const [selectedAnnouncementForResponses, setSelectedAnnouncementForResponses] = useState(null);
  const [responsesList, setResponsesList] = useState([]);
  const [responsesLoading, setResponsesLoading] = useState(false);

  const loadResponses = async (announcementId) => {
    try {
      setResponsesLoading(true);
      const { data, error } = await supabase
        .from('poll_responses')
        .select('*')
        .eq('announcement_id', announcementId)
        .order('created_at', { ascending: false });
      if (error) throw error;
      setResponsesList(data || []);
    } catch (err) {
      console.error('Failed to load responses:', err);
      alert('Failed to load responses: ' + err.message);
    } finally {
      setResponsesLoading(false);
    }
  };

  const handleResetForm = () => {
    setEditingId(null);
    setTitle('');
    setContent('');
    setIsActive(true);
    setButtonLabel('Close');
    setTemplateType('wizard');
    setTemplateData({
      positive_label: 'OK',
      positive_action: '',
      negative_label: 'Later',
      negative_action: '',
      feedback_category: 'general',
      poll_options: ['Yes, definitely', 'No, not really'],
      allow_multiselect: false,
      steps: [
        { title: 'Welcome to Presyohan V2', content: 'Explore our brand new layout and features designed for convenience.', input_type: 'none', input_options: [], input_placeholder: '', input_required: false }
      ],
      version_name: '2.0.0',
      version_code: 20,
      whats_new: 'UI optimizations and bug fixes.',
      is_forced: false,
      download_url: '',
      ignore_new_user_cooldown: false
    });
    setTargetingType('all');
    setTargetRoles([]);
    setTargetStoreId('');
    setTargetUserId('');
    setStartAt('');
    setEndAt('');
    setRecurrencePattern('none');
    setActivePreviewSlide(0);
    setTestRating(0);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    let finalTitle = title;
    let finalContent = content;

    if (templateType === 'feedback') {
      finalTitle = 'Got any questions or feedback?';
      finalContent = 'Tell us what we can improve about the app to make your experience better';
    } else if (templateType === 'star_ratings') {
      finalTitle = 'Rate our App';
      finalContent = 'How is your experience?';
    } else if (templateType === 'wizard') {
      finalTitle = 'Multi-step Slides';
      finalContent = 'Read through the slides.';
    }

    if (!finalTitle.trim() || !finalContent.trim()) return;

    try {
      setActionLoading('submit');
      const { data: { user } } = await supabase.auth.getUser();

      const payload = {
        title: finalTitle.trim(),
        content: finalContent.trim(),
        is_active: isActive,
        button_label: buttonLabel.trim() || 'Close',
        template_type: templateType,
        template_data: templateData,
        targeting_type: targetingType,
        target_roles: targetingType === 'roles' ? targetRoles : null,
        target_store_id: (targetingType === 'roles' || targetingType === 'suki') && targetStoreId ? targetStoreId : null,
        target_user_id: targetingType === 'specific' && targetUserId ? targetUserId : null,
        start_at: startAt ? new Date(startAt).toISOString() : new Date().toISOString(),
        end_at: endAt ? new Date(endAt).toISOString() : null,
        recurrence_pattern: recurrencePattern,
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

      handleResetForm();
      await loadAnnouncements();
    } catch (err) {
      console.error('Failed to save announcement:', err);
      alert('Error saving announcement: ' + err.message);
    } finally {
      setActionLoading(null);
    }
  };

  const handleRoleCheckboxChange = (role, checked) => {
    if (checked) {
      setTargetRoles(prev => [...prev, role]);
    } else {
      setTargetRoles(prev => prev.filter(r => r !== role));
    }
  };

  const updateTemplateField = (key, val) => {
    setTemplateData(prev => ({ ...prev, [key]: val }));
  };

  // Metrics computations
  const totalCount = announcements.length;
  const activeCount = announcements.filter(a => a.is_active).length;
  const scheduledCount = announcements.filter(a => {
    return a.is_active && a.start_at && new Date(a.start_at) > new Date();
  }).length;
  const inactiveCount = totalCount - activeCount;

  // Filtered lists for rendering
  const filteredAnnouncements = announcements.filter(item => {
    const matchesSearch = item.title.toLowerCase().includes(searchQuery.toLowerCase()) || 
                          item.content.toLowerCase().includes(searchQuery.toLowerCase());
    
    if (statusFilter === 'active') return matchesSearch && item.is_active;
    if (statusFilter === 'inactive') return matchesSearch && !item.is_active;
    if (statusFilter === 'scheduled') {
      const now = new Date();
      return matchesSearch && item.is_active && item.start_at && new Date(item.start_at) > now;
    }
    return matchesSearch;
  });

  return (
    <div style={{ 
      display: 'flex', 
      flexDirection: 'column',
      gap: '32px', 
      fontFamily: 'Inter, system-ui, -apple-system, sans-serif',
      color: '#334155',
      maxWidth: '1280px',
      margin: '0 auto',
      padding: '8px'
    }}>
      
      {/* SECTION 1: Interactive Creator Panel & Mockup View */}
      <div id="announcement-editor" style={{ 
        display: 'grid', 
        gridTemplateColumns: '1fr 340px', 
        gap: '32px', 
        alignItems: 'start'
      }}>
        
        {/* Creator Form */}
        <div style={{ 
          backgroundColor: '#ffffff', 
          borderRadius: '20px', 
          padding: '30px', 
          boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.05), 0 8px 10px -6px rgba(0, 0, 0, 0.05)',
          border: '1px solid #f1f5f9'
        }}>
          <div style={{ borderBottom: '1px solid #f1f5f9', paddingBottom: '16px', marginBottom: '24px' }}>
            <h2 style={{ margin: '0 0 6px 0', fontSize: '1.5rem', fontWeight: 800, color: '#0f172a' }}>
              {editingId ? 'Edit Campaign Broadcast' : 'Design Custom Announcement'}
            </h2>
            <p style={{ margin: 0, fontSize: '0.875rem', color: '#64748b' }}>
              Build targeted interactive layouts, version checks, or maintenance popups in real-time.
            </p>
          </div>

          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            
            {/* Input 1: Basic text fields */}
            {templateType !== 'feedback' && templateType !== 'star_ratings' && templateType !== 'wizard' && (
              <div>
                <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 800, color: '#475569', marginBottom: '6px', letterSpacing: '0.5px' }}>
                  1. BASIC TEXT CONTENT
                </label>
                <input
                  type="text"
                  required
                  className="admin-search-input"
                  style={{ paddingLeft: '16px', height: '44px', marginBottom: '10px', fontSize: '0.9rem', borderRadius: '8px' }}
                  placeholder="Broadcast Header Title..."
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                />
                <textarea
                  className="admin-search-input"
                  style={{ paddingLeft: '16px', height: '90px', resize: 'none', paddingTop: '12px', fontSize: '0.9rem', borderRadius: '8px' }}
                  placeholder="Message description details body..."
                  value={content}
                  onChange={(e) => setContent(e.target.value)}
                  required
                />
              </div>
            )}

            {/* Input 2: Layout Type & configuration options */}
            <div style={{ backgroundColor: '#f8fafc', borderRadius: '12px', padding: '20px', border: '1px solid #f1f5f9' }}>
              <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 800, color: '#475569', marginBottom: '8px', letterSpacing: '0.5px' }}>
                2. DIALOG LAYOUT & CUSTOM WIDGETS
              </label>
              <select
                className="admin-search-input"
                style={{ paddingLeft: '12px', height: '42px', marginBottom: '16px', fontSize: '0.875rem', borderRadius: '8px', backgroundColor: '#ffffff' }}
                value={templateType}
                onChange={(e) => {
                  const val = e.target.value;
                  setTemplateType(val);
                  setActivePreviewSlide(0);
                  setTestRating(0);
                  if (val === 'feedback') {
                    setTitle('Got any questions or feedback?');
                    setContent('Tell us what we can improve about the app to make your experience better');
                  } else if (val === 'star_ratings') {
                    setTitle('Rate our App');
                    setContent('How is your experience?');
                  } else if (val === 'wizard') {
                    setTitle('Multi-step Slides');
                    setContent('Read through the slides.');
                  }
                }}
              >
                <option value="two_buttons">Action Choice (Two customizable action buttons)</option>
                <option value="feedback">Feedback Form (Input comments + Contact Us navigation)</option>
                <option value="star_ratings">App Star Ratings (Interactive stars + comment input)</option>
                <option value="wizard">Slides Carousel / Survey (Single or Multi-step)</option>
                <option value="maintenance">Scheduled Maintenance (Requires client shutdown)</option>
              </select>

              {/* Layout-specific dynamic options */}
              {templateType === 'two_buttons' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                    <div>
                      <label style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 600 }}>Positive Action Label</label>
                      <input
                        type="text"
                        className="admin-search-input"
                        style={{ paddingLeft: '10px', height: '34px', fontSize: '0.85rem', backgroundColor: '#ffffff' }}
                        value={templateData.positive_label || ''}
                        onChange={(e) => updateTemplateField('positive_label', e.target.value)}
                      />
                    </div>
                    <div>
                      <label style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 600 }}>Negative Action Label</label>
                      <input
                        type="text"
                        className="admin-search-input"
                        style={{ paddingLeft: '10px', height: '34px', fontSize: '0.85rem', backgroundColor: '#ffffff' }}
                        value={templateData.negative_label || ''}
                        onChange={(e) => updateTemplateField('negative_label', e.target.value)}
                      />
                    </div>
                  </div>

                  {/* Positive Button Configurator */}
                  <div style={{ borderTop: '1px dashed #e2e8f0', paddingTop: '10px' }}>
                    <span style={{ fontSize: '0.75rem', fontWeight: 700, color: '#475569', display: 'block', marginBottom: '6px' }}>
                      POSITIVE BUTTON ACTION CONFIG
                    </span>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px', marginBottom: '8px' }}>
                      <div>
                        <label style={{ fontSize: '0.7rem', color: '#64748b', fontWeight: 600 }}>Action Type</label>
                        <select
                          className="admin-search-input"
                          style={{ paddingLeft: '10px', height: '34px', fontSize: '0.85rem', backgroundColor: '#ffffff' }}
                          value={templateData.positive_action_type || 'url'}
                          onChange={(e) => {
                            const type = e.target.value;
                            setTemplateData(prev => {
                              const updated = {
                                ...prev,
                                positive_action_type: type,
                                positive_action: type === 'screen' ? 'settings' : ''
                              };
                              // Auto-update filter target key to match positive action screen
                              if (type === 'screen') {
                                updated.target_navigation_screen = 'settings';
                              } else {
                                delete updated.target_navigation_screen;
                              }
                              return updated;
                            });
                          }}
                        >
                          <option value="url">Open Web Page / Deep Link</option>
                          <option value="screen">Navigate to App Screen</option>
                        </select>
                      </div>

                      {templateData.positive_action_type === 'screen' ? (
                        <div>
                          <label style={{ fontSize: '0.7rem', color: '#64748b', fontWeight: 600 }}>Target Screen</label>
                          <select
                            className="admin-search-input"
                            style={{ paddingLeft: '10px', height: '34px', fontSize: '0.85rem', backgroundColor: '#ffffff' }}
                            value={templateData.positive_action || 'settings'}
                            onChange={(e) => {
                              const screenKey = e.target.value;
                              setTemplateData(prev => ({
                                ...prev,
                                positive_action: screenKey,
                                target_navigation_screen: screenKey // Sync filter target key
                              }));
                            }}
                          >
                            <option value="settings">App Settings (Internet Price Search Toggle)</option>
                            <option value="customer_home">Regular User Portal (Search Prices Dashboard)</option>
                            <option value="store_home">Store Portal (Create/Join/View Store List Screen)</option>
                            <option value="manage_members">Manage Members Screen (Staff Settings - Owner Only)</option>
                            <option value="manage_store">Manage Store Details Screen (Owner Only)</option>
                            <option value="account_security">User Account & Security Settings</option>
                            <option value="edit_profile">Edit User Profile Details (Name, Photo)</option>
                            <option value="notifications">App Notification Inbox / updates center</option>
                            <option value="memberships">Store Memberships & Loyalty Programs</option>
                            <option value="contact_us">Contact Us / Support Helpdesk</option>
                            <option value="manage_categories">Manage Product Categories (Store context required)</option>
                            <option value="store_qr">View/Share Store QR Code (Store context required)</option>
                            <option value="manage_items">Manage Store Products & Prices (Store context required)</option>
                            <option value="add_multiple_items">Bulk Add / Import Products (Store context required)</option>
                          </select>
                        </div>
                      ) : (
                        <div>
                          <label style={{ fontSize: '0.7rem', color: '#64748b', fontWeight: 600 }}>Web URL Link</label>
                          <input
                            type="text"
                            className="admin-search-input"
                            style={{ paddingLeft: '10px', height: '34px', fontSize: '0.85rem', backgroundColor: '#ffffff' }}
                            placeholder="e.g. https://play.google.com"
                            value={templateData.positive_action || ''}
                            onChange={(e) => updateTemplateField('positive_action', e.target.value)}
                          />
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Negative Button Configurator */}
                  <div style={{ borderTop: '1px dashed #e2e8f0', paddingTop: '10px' }}>
                    <span style={{ fontSize: '0.75rem', fontWeight: 700, color: '#475569', display: 'block', marginBottom: '6px' }}>
                      NEGATIVE BUTTON ACTION CONFIG
                    </span>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                      <div>
                        <label style={{ fontSize: '0.7rem', color: '#64748b', fontWeight: 600 }}>Action Type</label>
                        <select
                          className="admin-search-input"
                          style={{ paddingLeft: '10px', height: '34px', fontSize: '0.85rem', backgroundColor: '#ffffff' }}
                          value={templateData.negative_action_type || 'url'}
                          onChange={(e) => {
                            const type = e.target.value;
                            setTemplateData(prev => ({
                              ...prev,
                              negative_action_type: type,
                              negative_action: type === 'screen' ? 'settings' : ''
                            }));
                          }}
                        >
                          <option value="url">Open Web Page / Deep Link</option>
                          <option value="screen">Navigate to App Screen</option>
                        </select>
                      </div>

                      {templateData.negative_action_type === 'screen' ? (
                        <div>
                          <label style={{ fontSize: '0.7rem', color: '#64748b', fontWeight: 600 }}>Target Screen</label>
                          <select
                            className="admin-search-input"
                            style={{ paddingLeft: '10px', height: '34px', fontSize: '0.85rem', backgroundColor: '#ffffff' }}
                            value={templateData.negative_action || 'settings'}
                            onChange={(e) => updateTemplateField('negative_action', e.target.value)}
                          >
                            <option value="settings">App Settings (Internet Price Search Toggle)</option>
                            <option value="customer_home">Regular User Portal (Search Prices Dashboard)</option>
                            <option value="store_home">Store Portal (Create/Join/View Store List Screen)</option>
                            <option value="manage_members">Manage Members Screen (Staff Settings - Owner Only)</option>
                            <option value="manage_store">Manage Store Details Screen (Owner Only)</option>
                            <option value="account_security">User Account & Security Settings</option>
                            <option value="edit_profile">Edit User Profile Details (Name, Photo)</option>
                            <option value="notifications">App Notification Inbox / updates center</option>
                            <option value="memberships">Store Memberships & Loyalty Programs</option>
                            <option value="contact_us">Contact Us / Support Helpdesk</option>
                            <option value="manage_categories">Manage Product Categories (Store context required)</option>
                            <option value="store_qr">View/Share Store QR Code (Store context required)</option>
                            <option value="manage_items">Manage Store Products & Prices (Store context required)</option>
                            <option value="add_multiple_items">Bulk Add / Import Products (Store context required)</option>
                          </select>
                        </div>
                      ) : (
                        <div>
                          <label style={{ fontSize: '0.7rem', color: '#64748b', fontWeight: 600 }}>Web URL Link</label>
                          <input
                            type="text"
                            className="admin-search-input"
                            style={{ paddingLeft: '10px', height: '34px', fontSize: '0.85rem', backgroundColor: '#ffffff' }}
                            placeholder="e.g. https://play.google.com"
                            value={templateData.negative_action || ''}
                            onChange={(e) => updateTemplateField('negative_action', e.target.value)}
                          />
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              )}

              {templateType === 'feedback' && (
                <div>
                  <label style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 600, display: 'block', marginBottom: '4px' }}>Topic Classification Category</label>
                  <select
                    className="admin-search-input"
                    style={{ paddingLeft: '10px', height: '34px', fontSize: '0.85rem', backgroundColor: '#ffffff' }}
                    value={templateData.feedback_category || 'general'}
                    onChange={(e) => updateTemplateField('feedback_category', e.target.value)}
                  >
                    <option value="general">General Reviews</option>
                    <option value="bug">Bug report & Troubleshooting</option>
                    <option value="feature">New features requests</option>
                  </select>
                </div>
              )}

              {templateType === 'poll' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', backgroundColor: '#ffffff', padding: '10px 14px', borderRadius: '8px', border: '1px solid #f1f5f9' }}>
                    <input
                      type="checkbox"
                      id="allowMultiselect"
                      checked={templateData.allow_multiselect || false}
                      onChange={(e) => updateTemplateField('allow_multiselect', e.target.checked)}
                      style={{ width: '16px', height: '16px', cursor: 'pointer' }}
                    />
                    <label htmlFor="allowMultiselect" style={{ fontSize: '0.8rem', color: '#475569', fontWeight: 600, cursor: 'pointer' }}>
                      Allow users to check multiple vote options
                    </label>
                  </div>
                  <div>
                    <label style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 600, display: 'block', marginBottom: '6px' }}>Vote Choices:</label>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                      {(templateData.poll_options || []).map((opt, idx) => (
                        <div key={idx} style={{ display: 'flex', gap: '6px' }}>
                          <input
                            type="text"
                            className="admin-search-input"
                            style={{ paddingLeft: '10px', height: '34px', fontSize: '0.85rem', backgroundColor: '#ffffff', flex: 1 }}
                            value={opt}
                            onChange={(e) => {
                              const copy = [...(templateData.poll_options || [])];
                              copy[idx] = e.target.value;
                              updateTemplateField('poll_options', copy);
                            }}
                          />
                          <button
                            type="button"
                            style={{ padding: '0 12px', border: '1px solid #fee2e2', borderRadius: '8px', backgroundColor: '#fee2e2', color: '#ef4444', fontWeight: 700, cursor: 'pointer' }}
                            onClick={() => {
                              const copy = (templateData.poll_options || []).filter((_, i) => i !== idx);
                              updateTemplateField('poll_options', copy);
                            }}
                          >
                            ×
                          </button>
                        </div>
                      ))}
                    </div>
                    <button
                      type="button"
                      style={{ fontSize: '0.8rem', color: '#ff8c00', background: 'none', border: 'none', cursor: 'pointer', fontWeight: 700, marginTop: '8px' }}
                      onClick={() => {
                        const copy = [...(templateData.poll_options || []), ''];
                        updateTemplateField('poll_options', copy);
                      }}
                    >
                      + Add New Option
                    </button>
                  </div>
                </div>
              )}

              {templateType === 'wizard' && (
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '12px 16px', backgroundColor: '#eff6ff', borderRadius: '12px', border: '1px solid #dbeafe', marginBottom: '14px' }}>
                    <div>
                      <span style={{ display: 'block', fontSize: '0.85rem', fontWeight: 700, color: '#1e3a8a' }}>Force Step Completion</span>
                      <span style={{ fontSize: '0.72rem', color: '#3b82f6' }}>Prevent users from closing the dialog until all steps are finished</span>
                    </div>
                    <div 
                      style={{
                        width: '50px',
                        height: '26px',
                        borderRadius: '13px',
                        backgroundColor: templateData.prevent_dismiss ? '#3b82f6' : '#cbd5e1',
                        position: 'relative',
                        cursor: 'pointer',
                        transition: 'background-color 0.2s'
                      }}
                      onClick={() => updateTemplateField('prevent_dismiss', !templateData.prevent_dismiss)}
                    >
                      <div style={{
                        width: '20px',
                        height: '20px',
                        borderRadius: '50%',
                        backgroundColor: '#ffffff',
                        position: 'absolute',
                        top: '3px',
                        left: templateData.prevent_dismiss ? '27px' : '3px',
                        transition: 'left 0.2s',
                        boxShadow: '0 1px 3px rgba(0,0,0,0.2)'
                      }} />
                    </div>
                  </div>

                  <label style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 600, display: 'block', marginBottom: '8px' }}>Tutorial Slide Carousel Pages:</label>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                    {(templateData.steps || []).map((step, idx) => (
                      <div key={idx} style={{ border: '1px solid #e2e8f0', borderRadius: '10px', padding: '12px', backgroundColor: '#ffffff' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px', alignItems: 'center' }}>
                          <span style={{ fontSize: '0.75rem', fontWeight: 800, color: '#ff8c00' }}>Slide Step #{idx + 1}</span>
                          <button
                            type="button"
                            style={{ fontSize: '0.75rem', border: 'none', color: '#ef4444', backgroundColor: 'transparent', cursor: 'pointer', fontWeight: 700 }}
                            onClick={() => {
                              const copy = (templateData.steps || []).filter((_, i) => i !== idx);
                              updateTemplateField('steps', copy);
                              setActivePreviewSlide(0);
                            }}
                          >
                            Remove Page
                          </button>
                        </div>
                        <input
                          type="text"
                          className="admin-search-input"
                          style={{ paddingLeft: '8px', height: '30px', marginBottom: '6px', fontSize: '0.8rem', backgroundColor: '#fafafa' }}
                          placeholder="Page Title text..."
                          value={step.title || ''}
                          onChange={(e) => {
                            const copy = [...(templateData.steps || [])];
                            copy[idx] = { ...step, title: e.target.value };
                            updateTemplateField('steps', copy);
                          }}
                        />
                        <textarea
                          className="admin-search-input"
                          style={{ paddingLeft: '8px', height: '44px', fontSize: '0.8rem', resize: 'none', paddingTop: '4px', backgroundColor: '#fafafa', marginBottom: '6px' }}
                          placeholder="Description details..."
                          value={step.content || ''}
                          onChange={(e) => {
                            const copy = [...(templateData.steps || [])];
                            copy[idx] = { ...step, content: e.target.value };
                            updateTemplateField('steps', copy);
                          }}
                        />

                        {/* Slide step interactive input configurator */}
                        <div style={{ marginTop: '6px', borderTop: '1px dashed #e2e8f0', paddingTop: '6px' }}>
                          <label style={{ fontSize: '0.7rem', color: '#64748b', fontWeight: 700, display: 'block', marginBottom: '2px' }}>Interactive Survey (Optional)</label>
                          <select
                            className="admin-search-input"
                            style={{ paddingLeft: '8px', height: '28px', fontSize: '0.75rem', backgroundColor: '#ffffff', marginBottom: '6px' }}
                            value={step.input_type || 'none'}
                            onChange={(e) => {
                              const copy = [...(templateData.steps || [])];
                              copy[idx] = { 
                                ...step, 
                                input_type: e.target.value,
                                input_options: e.target.value === 'choose' || e.target.value === 'select' ? (step.input_options || ['Yes', 'No']) : [],
                                input_placeholder: e.target.value === 'text' ? (step.input_placeholder || 'Enter response...') : '',
                                input_required: step.input_required || false
                              };
                              updateTemplateField('steps', copy);
                            }}
                          >
                            <option value="none">None (Regular slide info)</option>
                            <option value="text">Text Response Box</option>
                            <option value="choose">Single-select Choice (Radio)</option>
                            <option value="select">Multi-select Choice (Checkbox)</option>
                          </select>
                        </div>

                        {step.input_type === 'text' && (
                          <div style={{ display: 'flex', gap: '8px', alignItems: 'center', marginBottom: '4px' }}>
                            <div style={{ flex: 2 }}>
                              <input
                                type="text"
                                className="admin-search-input"
                                style={{ paddingLeft: '8px', height: '26px', fontSize: '0.75rem', backgroundColor: '#fafafa' }}
                                placeholder="Text placeholder..."
                                value={step.input_placeholder || ''}
                                onChange={(e) => {
                                  const copy = [...(templateData.steps || [])];
                                  copy[idx] = { ...step, input_placeholder: e.target.value };
                                  updateTemplateField('steps', copy);
                                }}
                              />
                            </div>
                            <div style={{ flex: 1, display: 'flex', alignItems: 'center', gap: '4px' }}>
                              <input
                                type="checkbox"
                                id={`req_${idx}`}
                                checked={step.input_required || false}
                                onChange={(e) => {
                                  const copy = [...(templateData.steps || [])];
                                  copy[idx] = { ...step, input_required: e.target.checked };
                                  updateTemplateField('steps', copy);
                                }}
                                style={{ cursor: 'pointer' }}
                              />
                              <label htmlFor={`req_${idx}`} style={{ fontSize: '0.7rem', color: '#64748b', cursor: 'pointer', fontWeight: 600 }}>Required</label>
                            </div>
                          </div>
                        )}

                        {(step.input_type === 'choose' || step.input_type === 'select') && (
                          <div style={{ backgroundColor: '#f8fafc', padding: '6px', borderRadius: '6px', border: '1px solid #e2e8f0', marginTop: '4px' }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '4px' }}>
                              <span style={{ fontSize: '0.7rem', fontWeight: 'bold', color: '#475569' }}>Choices list:</span>
                              <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                                <input
                                  type="checkbox"
                                  id={`req_${idx}`}
                                  checked={step.input_required || false}
                                  onChange={(e) => {
                                    const copy = [...(templateData.steps || [])];
                                    copy[idx] = { ...step, input_required: e.target.checked };
                                    updateTemplateField('steps', copy);
                                  }}
                                  style={{ cursor: 'pointer' }}
                                />
                                <label htmlFor={`req_${idx}`} style={{ fontSize: '0.7rem', color: '#64748b', cursor: 'pointer', fontWeight: 600 }}>Required</label>
                              </div>
                            </div>
                            {(step.input_options || []).map((opt, optIdx) => (
                              <div key={optIdx} style={{ display: 'flex', gap: '4px', marginBottom: '4px' }}>
                                <input
                                  type="text"
                                  className="admin-search-input"
                                  style={{ paddingLeft: '8px', height: '24px', fontSize: '0.75rem', backgroundColor: '#ffffff' }}
                                  value={opt}
                                  onChange={(e) => {
                                    const copy = [...(templateData.steps || [])];
                                    const opts = [...(step.input_options || [])];
                                    opts[optIdx] = e.target.value;
                                    copy[idx] = { ...step, input_options: opts };
                                    updateTemplateField('steps', copy);
                                  }}
                                />
                                <button
                                  type="button"
                                  style={{ padding: '0 4px', fontSize: '0.75rem', border: 'none', borderRadius: '4px', backgroundColor: '#fee2e2', color: '#ef4444', cursor: 'pointer' }}
                                  onClick={() => {
                                    const copy = [...(templateData.steps || [])];
                                    const opts = (step.input_options || []).filter((_, oI) => oI !== optIdx);
                                    copy[idx] = { ...step, input_options: opts };
                                    updateTemplateField('steps', copy);
                                  }}
                                >
                                  ×
                                </button>
                              </div>
                            ))}
                            <button
                              type="button"
                              style={{ fontSize: '0.7rem', color: '#ff8c00', background: 'none', border: 'none', cursor: 'pointer', fontWeight: 700, padding: 0 }}
                              onClick={() => {
                                const copy = [...(templateData.steps || [])];
                                const opts = [...(step.input_options || []), ''];
                                copy[idx] = { ...step, input_options: opts };
                                updateTemplateField('steps', copy);
                              }}
                            >
                              + Add Option
                            </button>
                          </div>
                        )}

                      </div>
                    ))}
                  </div>
                  <button
                    type="button"
                    style={{ fontSize: '0.8rem', color: '#ff8c00', background: 'none', border: 'none', cursor: 'pointer', fontWeight: 700, marginTop: '8px' }}
                    onClick={() => {
                      const copy = [...(templateData.steps || []), { title: '', content: '', input_type: 'none' }];
                      updateTemplateField('steps', copy);
                    }}
                  >
                    + Add New Slide Page
                  </button>
                </div>
              )}

              {templateType === 'version_check' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                    <div>
                      <label style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 600 }}>Release Version Name</label>
                      <input
                        type="text"
                        className="admin-search-input"
                        style={{ paddingLeft: '8px', height: '34px', fontSize: '0.85rem', backgroundColor: '#ffffff' }}
                        placeholder="e.g. 3.2.0"
                        value={templateData.version_name || ''}
                        onChange={(e) => updateTemplateField('version_name', e.target.value)}
                      />
                    </div>
                    <div>
                      <label style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 600 }}>Version Code Integer</label>
                      <input
                        type="number"
                        className="admin-search-input"
                        style={{ paddingLeft: '8px', height: '34px', fontSize: '0.85rem', backgroundColor: '#ffffff' }}
                        placeholder="e.g. 32"
                        value={templateData.version_code || ''}
                        onChange={(e) => updateTemplateField('version_code', parseInt(e.target.value) || '')}
                      />
                    </div>
                  </div>
                  <div>
                    <label style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 600 }}>Release Changelog Notes</label>
                    <textarea
                      className="admin-search-input"
                      style={{ paddingLeft: '8px', height: '50px', resize: 'none', paddingTop: '4px', fontSize: '0.85rem', backgroundColor: '#ffffff' }}
                      placeholder="e.g. Enhanced performance and dynamic popups..."
                      value={templateData.whats_new || ''}
                      onChange={(e) => updateTemplateField('whats_new', e.target.value)}
                    />
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', backgroundColor: '#ffffff', padding: '10px 14px', borderRadius: '8px', border: '1px solid #f1f5f9' }}>
                    <input
                      type="checkbox"
                      id="isForced"
                      checked={templateData.is_forced || false}
                      onChange={(e) => updateTemplateField('is_forced', e.target.checked)}
                      style={{ width: '16px', height: '16px', cursor: 'pointer' }}
                    />
                    <label htmlFor="isForced" style={{ fontSize: '0.8rem', color: '#475569', fontWeight: 600, cursor: 'pointer' }}>
                      Force Upgrade (Cannot close/dismiss updater)
                    </label>
                  </div>
                  <div>
                    <label style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 600 }}>Direct APK Download URL</label>
                    <input
                      type="text"
                      className="admin-search-input"
                      style={{ paddingLeft: '8px', height: '34px', fontSize: '0.85rem', backgroundColor: '#ffffff' }}
                      placeholder="https://direct-link-to-apk.apk"
                      value={templateData.download_url || ''}
                      onChange={(e) => updateTemplateField('download_url', e.target.value)}
                    />
                  </div>
                </div>
              )}

              {templateType === 'maintenance' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                  {/* Persistent Pop-up Toggle */}
                  <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '12px 16px',
                    backgroundColor: '#fff7ed',
                    borderRadius: '12px',
                    border: '1px solid #fed7aa'
                  }}>
                    <div>
                      <span style={{ display: 'block', fontSize: '0.85rem', fontWeight: 700, color: '#9a3412' }}>🔁 Persistent Pop-up</span>
                      <span style={{ fontSize: '0.72rem', color: '#c2410c' }}>Dialog re-appears on every app open (never dismissed)</span>
                    </div>
                    <div
                      style={{
                        width: '50px',
                        height: '26px',
                        borderRadius: '13px',
                        backgroundColor: templateData.persist_on_every_open ? '#f97316' : '#cbd5e1',
                        position: 'relative',
                        cursor: 'pointer',
                        transition: 'background-color 0.2s',
                        flexShrink: 0
                      }}
                      onClick={() => updateTemplateField('persist_on_every_open', !templateData.persist_on_every_open)}
                    >
                      <div style={{
                        width: '20px',
                        height: '20px',
                        borderRadius: '50%',
                        backgroundColor: '#ffffff',
                        position: 'absolute',
                        top: '3px',
                        left: templateData.persist_on_every_open ? '27px' : '3px',
                        transition: 'left 0.2s',
                        boxShadow: '0 1px 3px rgba(0,0,0,0.2)'
                      }} />
                    </div>
                  </div>

                  {/* Show Before Auth Toggle */}
                  <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '12px 16px',
                    backgroundColor: '#f0f9ff',
                    borderRadius: '12px',
                    border: '1px solid #bae6fd'
                  }}>
                    <div>
                      <span style={{ display: 'block', fontSize: '0.85rem', fontWeight: 700, color: '#0c4a6e' }}>🚪 Show on Splash &amp; Login</span>
                      <span style={{ fontSize: '0.72rem', color: '#0369a1' }}>Broadcasts before login — shown on Splash &amp; Login screens</span>
                    </div>
                    <div
                      style={{
                        width: '50px',
                        height: '26px',
                        borderRadius: '13px',
                        backgroundColor: templateData.show_before_auth ? '#0ea5e9' : '#cbd5e1',
                        position: 'relative',
                        cursor: 'pointer',
                        transition: 'background-color 0.2s',
                        flexShrink: 0
                      }}
                      onClick={() => updateTemplateField('show_before_auth', !templateData.show_before_auth)}
                    >
                      <div style={{
                        width: '20px',
                        height: '20px',
                        borderRadius: '50%',
                        backgroundColor: '#ffffff',
                        position: 'absolute',
                        top: '3px',
                        left: templateData.show_before_auth ? '27px' : '3px',
                        transition: 'left 0.2s',
                        boxShadow: '0 1px 3px rgba(0,0,0,0.2)'
                      }} />
                    </div>
                  </div>
                </div>
              )}
            </div>

            {/* Input 3: Recipients targeting parameters */}
            <div style={{ backgroundColor: '#f8fafc', borderRadius: '12px', padding: '20px', border: '1px solid #f1f5f9' }}>
              <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 800, color: '#475569', marginBottom: '8px', letterSpacing: '0.5px' }}>
                3. AUDIENCE RECIPIENTS TARGETING
              </label>
              <select
                className="admin-search-input"
                style={{ paddingLeft: '12px', height: '42px', marginBottom: '12px', fontSize: '0.875rem', borderRadius: '8px', backgroundColor: '#ffffff' }}
                value={targetingType}
                onChange={(e) => {
                  setTargetingType(e.target.value);
                  setTargetStoreId('');
                  setTargetUserId('');
                }}
              >
                <option value="all">Broad Broadcast (All registered accounts & store staff)</option>
                <option value="roles">Specific Role (e.g. Owners, Managers, Employees, Sukis)</option>
                <option value="suki">All Sukis of a Specific Store</option>
                <option value="specific">Specific Single User Account</option>
              </select>

              {/* Dynamic selector depending on Audience Group */}
              {(targetingType === 'roles' || targetingType === 'suki') && (
                <div style={{ marginBottom: '12px' }}>
                  <label style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 600, display: 'block', marginBottom: '4px' }}>Target Store</label>
                  <select
                    required
                    className="admin-search-input"
                    style={{ paddingLeft: '12px', height: '36px', fontSize: '0.85rem', backgroundColor: '#ffffff' }}
                    value={targetStoreId}
                    onChange={(e) => setTargetStoreId(e.target.value)}
                  >
                    <option value="">-- Select Store --</option>
                    {storesList.map(st => (
                      <option key={st.id} value={st.id}>{st.name} ({st.branch || 'Main'})</option>
                    ))}
                  </select>
                </div>
              )}

              {targetingType === 'roles' && (
                <div style={{ marginBottom: '4px' }}>
                  <label style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 600, display: 'block', marginBottom: '6px' }}>Target Roles</label>
                  <div style={{ display: 'flex', gap: '16px' }}>
                    {['owner', 'manager', 'employee', 'suki'].map(role => (
                      <label key={role} style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '0.8rem', textTransform: 'capitalize', cursor: 'pointer' }}>
                        <input
                          type="checkbox"
                          checked={targetRoles.includes(role)}
                          onChange={(e) => handleRoleCheckboxChange(role, e.target.checked)}
                          style={{ cursor: 'pointer' }}
                        />
                        {role === 'employee' ? 'Sales Staff' : role === 'suki' ? 'Suki' : role}
                      </label>
                    ))}
                  </div>
                </div>
              )}

              {targetingType === 'specific' && (
                <div>
                  <label style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 600, display: 'block', marginBottom: '4px' }}>Select Target Account</label>
                  <select
                    required
                    className="admin-search-input"
                    style={{ paddingLeft: '12px', height: '36px', fontSize: '0.85rem', backgroundColor: '#ffffff' }}
                    value={targetUserId}
                    onChange={(e) => setTargetUserId(e.target.value)}
                  >
                    <option value="">-- Choose User Profile --</option>
                    {usersList.map(u => (
                      <option key={u.id} value={u.id}>{u.name || 'Unnamed User'} ({u.email})</option>
                    ))}
                  </select>
                </div>
              )}
            </div>

            {/* Input 4: Delivery schedule parameters */}
            <div style={{ backgroundColor: '#f8fafc', borderRadius: '12px', padding: '20px', border: '1px solid #f1f5f9' }}>
              <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 800, color: '#475569', marginBottom: '8px', letterSpacing: '0.5px' }}>
                4. CAMPAIGN DELIVERY SCHEDULE
              </label>
              
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px', marginBottom: '12px' }}>
                <div>
                  <label style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 600 }}>Start Publishing Time</label>
                  <input
                    type="datetime-local"
                    className="admin-search-input"
                    style={{ paddingLeft: '8px', height: '36px', fontSize: '0.85rem', backgroundColor: '#ffffff' }}
                    value={startAt}
                    onChange={(e) => setStartAt(e.target.value)}
                  />
                </div>
                <div>
                  <label style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 600 }}>End Publishing Time</label>
                  <input
                    type="datetime-local"
                    className="admin-search-input"
                    style={{ paddingLeft: '8px', height: '36px', fontSize: '0.85rem', backgroundColor: '#ffffff' }}
                    value={endAt}
                    onChange={(e) => setEndAt(e.target.value)}
                  />
                </div>
              </div>

              <div>
                <label style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 600, display: 'block', marginBottom: '4px' }}>Automated Recurrence Cycle</label>
                <select
                  className="admin-search-input"
                  style={{ paddingLeft: '10px', height: '36px', fontSize: '0.85rem', backgroundColor: '#ffffff' }}
                  value={recurrencePattern}
                  onChange={(e) => setRecurrencePattern(e.target.value)}
                >
                  <option value="none">One-time notification trigger</option>
                  <option value="daily">Repeat Daily</option>
                  <option value="15_days">Repeat Every 15 Days</option>
                  <option value="monthly">Repeat Monthly</option>
                </select>
              </div>
            </div>

            {/* Default actions label config */}
            {templateType !== 'star_ratings' && templateType !== 'maintenance' && (
              <div style={{ display: 'grid', gridTemplateColumns: '1.5fr 1fr', gap: '12px' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 800, color: '#475569', marginBottom: '6px', letterSpacing: '0.5px' }}>
                    DISMISS BUTTON LABEL
                  </label>
                  <input
                    type="text"
                    required
                    className="admin-search-input"
                    style={{ paddingLeft: '16px', height: '44px', fontSize: '0.9rem', borderRadius: '8px' }}
                    value={buttonLabel}
                    onChange={(e) => setButtonLabel(e.target.value)}
                  />
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 800, color: '#475569', marginBottom: '6px', letterSpacing: '0.5px' }}>
                    BUTTON COLOR THEME
                  </label>
                  <select
                    className="admin-search-input"
                    style={{ paddingLeft: '12px', height: '44px', fontSize: '0.9rem', borderRadius: '8px', backgroundColor: '#ffffff' }}
                    value={templateData.button_color || 'teal'}
                    onChange={(e) => updateTemplateField('button_color', e.target.value)}
                  >
                    <option value="teal">Presyohan Teal (#219EBC)</option>
                    <option value="orange">Presyohan Orange (#FB8500)</option>
                  </select>
                </div>
              </div>
            )}

            {/* Custom switch toggler for new user cooldown bypass */}
            {templateType !== 'maintenance' && (
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '14px 18px', backgroundColor: '#fdf2f8', borderRadius: '12px', border: '1px solid #fce7f3' }}>
                <div>
                  <span style={{ display: 'block', fontSize: '0.875rem', fontWeight: 700, color: '#9d174d' }}>Bypass New User Cooldown</span>
                  <span style={{ fontSize: '0.75rem', color: '#be185d' }}>Ignore the 5-minute new-user cooldown for this critical alert</span>
                </div>
                <div 
                  style={{
                    width: '50px',
                    height: '26px',
                    borderRadius: '13px',
                    backgroundColor: templateData.ignore_new_user_cooldown ? '#ec4899' : '#cbd5e1',
                    position: 'relative',
                    cursor: 'pointer',
                    transition: 'background-color 0.2s'
                  }}
                  onClick={() => updateTemplateField('ignore_new_user_cooldown', !templateData.ignore_new_user_cooldown)}
                >
                  <div style={{
                    width: '20px',
                    height: '20px',
                    borderRadius: '50%',
                    backgroundColor: '#ffffff',
                    position: 'absolute',
                    top: '3px',
                    left: templateData.ignore_new_user_cooldown ? '27px' : '3px',
                    transition: 'left 0.2s',
                    boxShadow: '0 1px 3px rgba(0,0,0,0.15)'
                  }} />
                </div>
              </div>
            )}

            {/* Custom switch toggler for status */}
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '14px 18px', backgroundColor: '#f0fdf4', borderRadius: '12px', border: '1px solid #dcfce7' }}>
              <div>
                <span style={{ display: 'block', fontSize: '0.875rem', fontWeight: 700, color: '#14532d' }}>Set Broadcast Status Active</span>
                <span style={{ fontSize: '0.75rem', color: '#166534' }}>Announcements display in real-time when active</span>
              </div>
              <div 
                style={{
                  width: '50px',
                  height: '26px',
                  borderRadius: '13px',
                  backgroundColor: isActive ? '#10b981' : '#cbd5e1',
                  position: 'relative',
                  cursor: 'pointer',
                  transition: 'background-color 0.2s'
                }}
                onClick={() => setIsActive(!isActive)}
              >
                <div style={{
                  width: '20px',
                  height: '20px',
                  borderRadius: '50%',
                  backgroundColor: '#ffffff',
                  position: 'absolute',
                  top: '3px',
                  left: isActive ? '27px' : '3px',
                  transition: 'left 0.2s',
                  boxShadow: '0 1px 3px rgba(0,0,0,0.15)'
                }} />
              </div>
            </div>

            {/* Submission buttons */}
            <div style={{ display: 'flex', gap: '12px', marginTop: '8px' }}>
              {editingId && (
                <button
                  type="button"
                  style={{
                    flex: 1,
                    height: '46px',
                    borderRadius: '8px',
                    border: '1px solid #cbd5e1',
                    color: '#64748b',
                    backgroundColor: '#ffffff',
                    fontWeight: 700,
                    cursor: 'pointer',
                    fontSize: '0.9rem'
                  }}
                  onClick={handleResetForm}
                >
                  Cancel Edit
                </button>
              )}
              <button
                type="submit"
                style={{
                  flex: editingId ? 2 : 1,
                  height: '46px',
                  borderRadius: '8px',
                  border: 'none',
                  color: '#ffffff',
                  backgroundColor: '#ff8c00',
                  fontWeight: 800,
                  cursor: 'pointer',
                  fontSize: '0.95rem',
                  boxShadow: '0 4px 6px -1px rgba(251, 133, 0, 0.2)'
                }}
                disabled={actionLoading === 'submit'}
              >
                {actionLoading === 'submit' ? 'Processing...' : (editingId ? 'Save Campaign Changes' : 'Launch Broadcast')}
              </button>
            </div>
          </form>
        </div>

        {/* Real-time Phone preview (Sticky) */}
        <div style={{ 
          position: 'sticky', 
          top: '24px', 
          display: 'flex', 
          flexDirection: 'column', 
          alignItems: 'center',
          gap: '12px',
          boxSizing: 'border-box'
        }}>
          <span style={{ fontSize: '0.75rem', fontWeight: 800, textTransform: 'uppercase', color: '#64748b', letterSpacing: '0.8px' }}>
            📱 LIVE MOCKUP PREVIEW
          </span>

          {/* Phone container */}
          <div style={{
            width: '300px',
            height: '560px',
            backgroundColor: '#0f172a',
            borderRadius: '40px',
            padding: '12px',
            boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
            position: 'relative',
            display: 'flex',
            flexDirection: 'column',
            boxSizing: 'border-box',
            border: '2px solid #334155'
          }}>
            {/* Notch */}
            <div style={{
              width: '100px',
              height: '18px',
              backgroundColor: '#0f172a',
              borderBottomLeftRadius: '12px',
              borderBottomRightRadius: '12px',
              position: 'absolute',
              top: '12px',
              left: '50%',
              transform: 'translateX(-50%)',
              zIndex: 10,
              display: 'flex',
              justifyContent: 'center',
              alignItems: 'center'
            }}>
              <div style={{ width: '6px', height: '6px', borderRadius: '50%', backgroundColor: '#1e293b' }} />
            </div>

            {/* Screen */}
            <div style={{
              flex: 1,
              backgroundColor: '#e2e8f0',
              borderRadius: '28px',
              overflow: 'hidden',
              position: 'relative',
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'center',
              alignItems: 'center',
              padding: '16px',
              boxSizing: 'border-box'
            }}>
              
              {/* Top status bar icons */}
              <div style={{
                position: 'absolute',
                top: 0,
                left: 0,
                right: 0,
                height: '24px',
                padding: '0 16px',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                fontSize: '10px',
                color: '#64748b',
                fontWeight: 600
              }}>
                <span>06:00</span>
                <div style={{ display: 'flex', gap: '4px' }}>
                  <span>📶</span>
                  <span>🔋</span>
                </div>
              </div>

              {/* Dynamic Dialog Card */}
              <div style={{
                width: '100%',
                backgroundColor: '#ffffff',
                borderRadius: '20px',
                padding: '16px',
                boxShadow: '0 15px 30px -5px rgba(0,0,0,0.15)',
                display: 'flex',
                flexDirection: 'column',
                boxSizing: 'border-box'
              }}>
                {/* Launcher Identity */}
                <div style={{ display: 'flex', alignItems: 'center', marginBottom: '10px' }}>
                  <img 
                    src={launcherImg} 
                    alt="Launcher Logo" 
                    style={{ width: '22px', height: '22px', objectFit: 'contain' }} 
                  />
                  <div style={{ display: 'flex', flexDirection: 'column', marginLeft: '4px', lineHeight: 1 }}>
                    <span style={{ fontSize: '6px', fontWeight: 'bold', color: '#ffc107', marginBottom: '-2px' }}>atong</span>
                    <div style={{ display: 'flex', fontWeight: 'bold', fontSize: '15px' }}>
                      <span style={{ color: '#FB8500' }}>presyo</span>
                      <span style={{ color: '#219EBC' }}>han?</span>
                    </div>
                  </div>
                </div>

                {/* Inner Content Area wrapper */}
                <div style={{
                  backgroundColor: '#EDF7FA',
                  borderRadius: '14px',
                  padding: '12px',
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  textAlign: 'center',
                  marginBottom: '12px',
                  boxSizing: 'border-box'
                }}>
                  
                  {templateType === 'wizard' ? (
                    (() => {
                      const steps = templateData.steps || [];
                      const activeStep = steps[activePreviewSlide] || { title: 'Slide Title', content: 'Carousel content...' };
                      return (
                        <div style={{ width: '100%' }}>
                          <h4 style={{ fontSize: '12px', fontWeight: 800, color: '#475569', margin: '0 0 4px 0' }}>
                            {activeStep.title || 'Slide Title'}
                          </h4>
                          <p style={{ fontSize: '9.5px', color: '#64748b', margin: '0 0 10px 0', lineHeight: 1.4 }}>
                            {activeStep.content || 'Content description details...'}
                          </p>

                          {activeStep.input_type === 'text' && (
                            <input
                              disabled
                              placeholder={activeStep.input_placeholder || 'Enter response...'}
                              style={{ width: '100%', height: '24px', borderRadius: '6px', border: '1px solid #cbd5e1', padding: '0 6px', fontSize: '9px', boxSizing: 'border-box', marginBottom: '8px' }}
                            />
                          )}
                          {(activeStep.input_type === 'choose' || activeStep.input_type === 'select') && (
                            <div style={{ display: 'flex', flexDirection: 'column', gap: '3px', marginBottom: '8px', textAlign: 'left', width: '100%' }}>
                              {(activeStep.input_options || []).map((opt, oIdx) => (
                                <label key={oIdx} style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '8.5px', color: '#475569' }}>
                                  <input type={activeStep.input_type === 'select' ? 'checkbox' : 'radio'} disabled name={`wizard_preview_${activePreviewSlide}`} style={{ margin: 0 }} />
                                  {opt || `Choice ${oIdx + 1}`}
                                </label>
                              ))}
                            </div>
                          )}

                          {/* Indicator Stepper lines */}
                          {steps.length > 1 && (
                            <div style={{ display: 'flex', gap: '4px', justifyContent: 'center', width: '100%', marginBottom: '4px' }}>
                              {steps.map((_, i) => (
                                <div 
                                  key={i} 
                                  style={{ 
                                    height: '5px', 
                                    flex: 1, 
                                    backgroundColor: i <= activePreviewSlide 
                                      ? '#eab308' 
                                      : '#cbd5e1', 
                                    borderRadius: '3px',
                                    maxWidth: '30px'
                                  }} 
                                />
                              ))}
                            </div>
                          )}
                        </div>
                      );
                    })()
                  ) : templateType === 'feedback' ? (
                    <div style={{ width: '100%' }}>
                      <h4 style={{ fontSize: '12px', fontWeight: 800, color: '#475569', margin: '0 0 4px 0' }}>
                        {title || 'Got any questions or feedback?'}
                      </h4>
                      <p style={{ fontSize: '9.5px', color: '#64748b', margin: '0 0 8px 0', lineHeight: 1.3 }}>
                        {content || 'Tell us what we can improve about the app to make your experience better'}
                      </p>
                    </div>
                  ) : templateType === 'star_ratings' ? (
                    <div style={{ width: '100%' }}>
                      <h4 style={{ fontSize: '12px', fontWeight: 800, color: '#475569', margin: '0 0 4px 0' }}>
                        {title || 'Rate our App'}
                      </h4>
                      <p style={{ fontSize: '9.5px', color: '#64748b', margin: '0 0 8px 0', lineHeight: 1.3 }}>
                        {content || 'How is your experience?'}
                      </p>
                      <div style={{ display: 'flex', gap: '4px', justifyContent: 'center', marginBottom: '8px' }}>
                        {[1, 2, 3, 4, 5].map(st => (
                          <span 
                            key={st} 
                            style={{ fontSize: '18px', color: st <= testRating ? '#ffc107' : '#cbd5e1', cursor: 'pointer' }}
                            onClick={() => setTestRating(st)}
                          >
                            ★
                          </span>
                        ))}
                      </div>
                      <input 
                        disabled 
                        placeholder="Write an optional comment..." 
                        style={{ width: '100%', height: '24px', borderRadius: '6px', border: '1px solid #cbd5e1', padding: '0 6px', fontSize: '9px', boxSizing: 'border-box' }}
                      />
                    </div>
                  ) : templateType === 'poll' ? (
                    <div style={{ width: '100%', textAlign: 'left' }}>
                      <h4 style={{ fontSize: '12px', fontWeight: 800, color: '#475569', margin: '0 0 4px 0', textAlign: 'center' }}>
                        {title || 'Opinion Poll'}
                      </h4>
                      <p style={{ fontSize: '9.5px', color: '#64748b', margin: '0 0 8px 0', textAlign: 'center', lineHeight: 1.3 }}>
                        {content || 'Cast your vote below:'}
                      </p>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                        {(templateData.poll_options || ['Choice 1', 'Choice 2']).map((opt, idx) => (
                          <label key={idx} style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '9px', color: '#475569', backgroundColor: '#ffffff', padding: '4px 6px', borderRadius: '6px', border: '1px solid #e2e8f0' }}>
                            <input type={templateData.allow_multiselect ? "checkbox" : "radio"} disabled name="preview_mob_poll" />
                            {opt || `Option ${idx + 1}`}
                          </label>
                        ))}
                      </div>
                    </div>
                  ) : templateType === 'version_check' ? (
                    <div style={{ width: '100%', textAlign: 'left' }}>
                      <h4 style={{ fontSize: '12px', fontWeight: 800, color: '#475569', margin: '0 0 2px 0', textAlign: 'center' }}>Update Available</h4>
                      <h5 style={{ fontSize: '10px', color: '#ff8c00', margin: '0 0 6px 0', textAlign: 'center' }}>
                        Version {templateData.version_name} ({templateData.version_code})
                      </h5>
                      <div style={{ backgroundColor: '#ffffff', borderRadius: '8px', padding: '8px', border: '1px solid #e2e8f0' }}>
                        <span style={{ fontSize: '8px', fontWeight: 'bold', color: '#475569' }}>What's New:</span>
                        <p style={{ fontSize: '8.5px', color: '#64748b', margin: 0, lineHeight: 1.2 }}>{templateData.whats_new}</p>
                      </div>
                    </div>
                  ) : templateType === 'maintenance' ? (
                    <div style={{ width: '100%' }}>
                      <h4 style={{ fontSize: '12px', fontWeight: 800, color: '#219EBC', margin: '0 0 4px 0' }}>
                        🔧 {title || 'Scheduled Maintenance'}
                      </h4>
                      <p style={{ fontSize: '9.5px', color: '#64748b', margin: '0 0 8px 0', lineHeight: 1.3 }}>
                        {content || 'System is currently undergoing improvements.'}
                      </p>
                    </div>
                  ) : (
                    <div>
                      <h4 style={{ fontSize: '13px', fontWeight: 800, color: '#475569', margin: '0 0 4px 0' }}>{title || 'Header'}</h4>
                      <p style={{ fontSize: '10px', color: '#64748b', margin: 0, lineHeight: 1.3 }}>{content || 'Message details body...'}</p>
                    </div>
                  )}
                </div>

                {/* Actions row */}
                {templateType === 'wizard' ? (
                  <div style={{ display: 'flex', gap: '6px' }}>
                    <button 
                      type="button"
                      onClick={() => {
                        if (activePreviewSlide === 0) {
                          setActivePreviewSlide(0);
                        } else {
                          setActivePreviewSlide(p => Math.max(0, p - 1));
                        }
                      }}
                      style={{
                        flex: 1,
                        height: '30px',
                        backgroundColor: '#219EBC',
                        color: '#ffffff',
                        border: 'none',
                        borderRadius: '15px',
                        fontSize: '10px',
                        fontWeight: 'bold',
                        cursor: 'pointer',
                        display: (activePreviewSlide === 0 && templateData.prevent_dismiss) ? 'none' : 'block'
                      }}
                    >
                      {activePreviewSlide === 0 ? 'Close' : 'Back'}
                    </button>
                    <button 
                      type="button"
                      onClick={() => {
                        const steps = templateData.steps || [];
                        if (activePreviewSlide < steps.length - 1) {
                          setActivePreviewSlide(p => p + 1);
                        } else {
                          setActivePreviewSlide(0);
                        }
                      }}
                      style={{
                        flex: 1,
                        height: '30px',
                        backgroundColor: '#ff8c00',
                        color: '#ffffff',
                        border: 'none',
                        borderRadius: '15px',
                        fontSize: '10px',
                        fontWeight: 'bold',
                        cursor: 'pointer'
                      }}
                    >
                      {activePreviewSlide < (templateData.steps || []).length - 1 ? 'Next' : (buttonLabel || 'Finish')}
                    </button>
                  </div>
                ) : templateType === 'two_buttons' ? (
                  <div style={{ display: 'flex', gap: '6px' }}>
                    <button type="button" style={{ flex: 1, height: '30px', backgroundColor: '#219EBC', color: '#ffffff', border: 'none', borderRadius: '15px', fontSize: '10px', fontWeight: 'bold' }}>
                      {templateData.negative_label || 'Cancel'}
                    </button>
                    <button type="button" style={{ flex: 1, height: '30px', backgroundColor: '#ff8c00', color: '#ffffff', border: 'none', borderRadius: '15px', fontSize: '10px', fontWeight: 'bold' }}>
                      {templateData.positive_label || 'Rate Now'}
                    </button>
                  </div>
                ) : templateType === 'feedback' ? (
                  <div style={{ display: 'flex', gap: '6px' }}>
                    <button type="button" style={{ flex: 1, height: '30px', backgroundColor: '#219EBC', color: '#ffffff', border: 'none', borderRadius: '15px', fontSize: '10px', fontWeight: 'bold' }}>
                      No, I'm fine
                    </button>
                    <button type="button" style={{ flex: 1, height: '30px', backgroundColor: '#FB8500', color: '#ffffff', border: 'none', borderRadius: '15px', fontSize: '10px', fontWeight: 'bold' }}>
                      Yes, I Have
                    </button>
                  </div>
                ) : templateType === 'star_ratings' ? (
                  <div style={{ display: 'flex', gap: '6px' }}>
                    <button type="button" style={{ flex: 1, height: '30px', backgroundColor: '#219EBC', color: '#ffffff', border: 'none', borderRadius: '15px', fontSize: '10px', fontWeight: 'bold' }}>
                      Rate Later
                    </button>
                    <button type="button" style={{ flex: 1, height: '30px', backgroundColor: '#FB8500', color: '#ffffff', border: 'none', borderRadius: '15px', fontSize: '10px', fontWeight: 'bold' }}>
                      Submit Rate
                    </button>
                  </div>
                ) : templateType === 'version_check' ? (
                  <div style={{ display: 'flex', gap: '6px' }}>
                    {!templateData.is_forced && (
                      <button type="button" style={{ flex: 1, height: '30px', backgroundColor: '#219EBC', color: '#ffffff', border: 'none', borderRadius: '15px', fontSize: '10px', fontWeight: 'bold' }}>
                        Later
                      </button>
                    )}
                    <button type="button" style={{ flex: 1, height: '30px', backgroundColor: '#ff8c00', color: '#ffffff', border: 'none', borderRadius: '15px', fontSize: '10px', fontWeight: 'bold' }}>
                      Update Now
                    </button>
                  </div>
                ) : templateType === 'maintenance' ? (
                  <button type="button" style={{ width: '100%', height: '30px', backgroundColor: '#219EBC', color: '#ffffff', border: 'none', borderRadius: '15px', fontSize: '11px', fontWeight: 'bold' }}>
                    Close App
                  </button>
                ) : (
                  <button type="button" style={{
                    width: '100%',
                    height: '30px',
                    backgroundColor: mockupBtnBg,
                    color: '#ffffff',
                    border: 'none',
                    borderRadius: '15px',
                    fontSize: '11px',
                    fontWeight: 'bold',
                    boxShadow: `0 4px 6px ${mockupBtnBg === '#ff8c00' ? 'rgba(251, 133, 0, 0.15)' : 'rgba(33, 158, 188, 0.15)'}`
                  }}>
                    {buttonLabel || 'Close'}
                  </button>
                )}
              </div>
            </div>
            {/* Bottom Indicator */}
            <div style={{ width: '90px', height: '4px', backgroundColor: '#334155', borderRadius: '2px', alignSelf: 'center', marginBottom: '4px' }} />
          </div>
        </div>
      </div>

      {/* SECTION 2: Full Width Campaign Dashboard */}
      <div style={{ 
        backgroundColor: '#ffffff', 
        borderRadius: '20px', 
        padding: '30px', 
        boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.05), 0 8px 10px -6px rgba(0, 0, 0, 0.05)',
        border: '1px solid #f1f5f9',
        marginTop: '8px'
      }}>
        
        {/* Metric Summary Cards Grid */}
        <div style={{ 
          display: 'grid', 
          gridTemplateColumns: 'repeat(4, 1fr)', 
          gap: '16px', 
          marginBottom: '30px' 
        }}>
          <div style={{ backgroundColor: '#eff6ff', borderRadius: '12px', padding: '16px', border: '1px solid #dbeafe', display: 'flex', flexDirection: 'column' }}>
            <span style={{ fontSize: '0.8rem', fontWeight: 700, color: '#1e40af', textTransform: 'uppercase', letterSpacing: '0.5px' }}>Total Broadcasts</span>
            <span style={{ fontSize: '1.8rem', fontWeight: 800, color: '#1e3a8a', marginTop: '6px' }}>{totalCount}</span>
          </div>
          <div style={{ backgroundColor: '#ecfdf5', borderRadius: '12px', padding: '16px', border: '1px solid #d1fae5', display: 'flex', flexDirection: 'column' }}>
            <span style={{ fontSize: '0.8rem', fontWeight: 700, color: '#065f46', textTransform: 'uppercase', letterSpacing: '0.5px' }}>Active Now</span>
            <span style={{ fontSize: '1.8rem', fontWeight: 800, color: '#064e3b', marginTop: '6px' }}>{activeCount}</span>
          </div>
          <div style={{ backgroundColor: '#fffbeb', borderRadius: '12px', padding: '16px', border: '1px solid #fef3c7', display: 'flex', flexDirection: 'column' }}>
            <span style={{ fontSize: '0.8rem', fontWeight: 700, color: '#92400e', textTransform: 'uppercase', letterSpacing: '0.5px' }}>Future Scheduled</span>
            <span style={{ fontSize: '1.8rem', fontWeight: 800, color: '#78350f', marginTop: '6px' }}>{scheduledCount}</span>
          </div>
          <div style={{ backgroundColor: '#f8fafc', borderRadius: '12px', padding: '16px', border: '1px solid #e2e8f0', display: 'flex', flexDirection: 'column' }}>
            <span style={{ fontSize: '0.8rem', fontWeight: 700, color: '#475569', textTransform: 'uppercase', letterSpacing: '0.5px' }}>Paused / Drafts</span>
            <span style={{ fontSize: '1.8rem', fontWeight: 800, color: '#1e293b', marginTop: '6px' }}>{inactiveCount}</span>
          </div>
        </div>

        {/* Header Controls Row */}
        <div style={{ 
          display: 'flex', 
          justifyContent: 'space-between', 
          alignItems: 'center', 
          marginBottom: '20px',
          paddingBottom: '16px',
          borderBottom: '1px solid #f1f5f9'
        }}>
          <div>
            <h3 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 800, color: '#0f172a' }}>Campaign History</h3>
            <p style={{ margin: '2px 0 0 0', fontSize: '0.8rem', color: '#64748b' }}>Monitor active alerts, repeat settings, and audience counts</p>
          </div>
          
          <div style={{ display: 'flex', gap: '8px' }}>
            <input
              type="text"
              className="admin-search-input"
              style={{ paddingLeft: '14px', height: '38px', width: '220px', fontSize: '0.85rem' }}
              placeholder="Search by keywords..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
            <select
              className="admin-search-input"
              style={{ paddingLeft: '10px', height: '38px', width: '130px', fontSize: '0.85rem', backgroundColor: '#ffffff' }}
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
            >
              <option value="all">All Campaigns</option>
              <option value="active">Active Only</option>
              <option value="inactive">Paused Only</option>
              <option value="scheduled">Scheduled</option>
            </select>
          </div>
        </div>

        {/* Directory Grid cards list */}
        <div style={{ 
          display: 'grid', 
          gridTemplateColumns: 'repeat(auto-fill, minmax(360px, 1fr))', 
          gap: '20px' 
        }}>
          {filteredAnnouncements.map((item) => {
            const now = new Date();
            const isScheduledFuture = item.is_active && item.start_at && new Date(item.start_at) > now;
            
            return (
              <div 
                key={item.id} 
                style={{
                  border: editingId === item.id ? '2px solid #ff8c00' : '1px solid #e2e8f0',
                  borderRadius: '14px',
                  padding: '20px',
                  backgroundColor: '#ffffff',
                  transition: 'transform 0.15s, box-shadow 0.15s',
                  boxShadow: '0 1px 3px rgba(0,0,0,0.02)',
                  display: 'flex',
                  flexDirection: 'column',
                  justifyContent: 'space-between',
                  gap: '12px'
                }}
              >
                {/* Header */}
                <div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '8px' }}>
                    <span style={{ 
                      fontSize: '0.7rem', 
                      fontWeight: 800, 
                      textTransform: 'uppercase', 
                      color: '#219ebc', 
                      backgroundColor: '#e0f2fe', 
                      padding: '2px 8px', 
                      borderRadius: '6px'
                    }}>
                      {item.template_type.replace('_', ' ')}
                    </span>
                    
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                      <div style={{
                        width: '8px',
                        height: '8px',
                        borderRadius: '50%',
                        backgroundColor: isScheduledFuture ? '#f59e0b' : (item.is_active ? '#10b981' : '#94a3b8')
                      }} />
                      <span style={{ 
                        fontSize: '0.75rem', 
                        fontWeight: 700, 
                        color: isScheduledFuture ? '#b45309' : (item.is_active ? '#047857' : '#64748b')
                      }}>
                        {isScheduledFuture ? 'Scheduled' : (item.is_active ? 'Active' : 'Paused')}
                      </span>
                    </div>
                  </div>

                  <h4 style={{ margin: '0 0 6px 0', fontSize: '1.05rem', fontWeight: 800, color: '#1e293b' }}>{item.title}</h4>
                  <p style={{ 
                    margin: 0, 
                    fontSize: '0.85rem', 
                    color: '#64748b', 
                    lineHeight: 1.4,
                    display: '-webkit-box',
                    WebkitLineClamp: 3,
                    WebkitBoxOrient: 'vertical',
                    overflow: 'hidden'
                  }}>
                    {item.content}
                  </p>
                </div>

                {/* Footer metrics & actions */}
                <div style={{ 
                  borderTop: '1px solid #f1f5f9', 
                  paddingTop: '12px',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '8px',
                  fontSize: '0.75rem',
                  color: '#64748b'
                }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <div>
                      <span>Audience: </span>
                      <strong style={{ color: '#334155', textTransform: 'capitalize' }}>{item.targeting_type}</strong>
                    </div>
                    {item.recurrence_pattern && item.recurrence_pattern !== 'none' && (
                      <div style={{ color: '#059669', fontWeight: 700 }}>
                        ↺ {item.recurrence_pattern}
                      </div>
                    )}
                  </div>

                  {item.start_at && (
                    <div style={{ fontSize: '0.7rem', color: '#94a3b8' }}>
                      Start: {new Date(item.start_at).toLocaleString()}
                    </div>
                  )}

                  {/* Actions Row */}
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginTop: '8px' }}>
                    {/* Row 1: Analytics / Answers */}
                    {((['poll', 'two_buttons', 'feedback'].includes(item.template_type)) || 
                      (item.template_type === 'wizard' && (item.template_data?.steps || []).some(s => s.input_type && s.input_type !== 'none'))) && (
                      <button 
                        style={{
                          width: '100%',
                          padding: '6px 12px',
                          borderRadius: '8px',
                          border: '1px solid #dbeafe',
                          backgroundColor: '#eff6ff',
                          color: '#2563eb',
                          fontWeight: 700,
                          cursor: 'pointer',
                          fontSize: '0.75rem',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          gap: '6px',
                          transition: 'background-color 0.2s',
                          boxSizing: 'border-box'
                        }}
                        type="button"
                        onClick={() => {
                          setSelectedAnnouncementForResponses(item);
                          setShowResponsesModal(true);
                          loadResponses(item.id);
                        }}
                      >
                        📊 View User Answers & Responses
                      </button>
                    )}

                    {/* Row 2: Management Controls */}
                    <div style={{ display: 'flex', gap: '6px', width: '100%', boxSizing: 'border-box' }}>
                      <button 
                        style={{
                          flex: 1,
                          padding: '5px 0',
                          borderRadius: '6px',
                          border: '1px solid #e2e8f0',
                          backgroundColor: '#ffffff',
                          color: '#475569',
                          fontWeight: 700,
                          cursor: 'pointer',
                          fontSize: '0.7rem',
                          textAlign: 'center',
                          whiteSpace: 'nowrap'
                        }}
                        onClick={() => handleEdit(item)}
                      >
                        Configure
                      </button>
                      <button 
                        style={{
                          flex: 1,
                          padding: '5px 0',
                          borderRadius: '6px',
                          border: '1px solid #ffedd5',
                          backgroundColor: '#fff7ed',
                          color: '#ea580c',
                          fontWeight: 700,
                          cursor: 'pointer',
                          fontSize: '0.7rem',
                          textAlign: 'center',
                          whiteSpace: 'nowrap'
                        }}
                        onClick={() => handleCopyAsNew(item)}
                      >
                        Reuse
                      </button>
                      <button 
                        style={{
                          flex: 1,
                          padding: '5px 0',
                          borderRadius: '6px',
                          border: '1px solid #cbd5e1',
                          backgroundColor: '#f1f5f9',
                          color: item.is_active ? '#0f766e' : '#475569',
                          fontWeight: 700,
                          cursor: 'pointer',
                          fontSize: '0.7rem',
                          textAlign: 'center',
                          whiteSpace: 'nowrap'
                        }}
                        onClick={() => handleToggleStatus(item)}
                      >
                        {item.is_active ? 'Pause' : 'Activate'}
                      </button>
                      <button 
                        style={{
                          flex: 1,
                          padding: '5px 0',
                          borderRadius: '6px',
                          border: '1px solid #fee2e2',
                          backgroundColor: '#fee2e2',
                          color: '#ef4444',
                          fontWeight: 700,
                          cursor: 'pointer',
                          fontSize: '0.7rem',
                          textAlign: 'center',
                          whiteSpace: 'nowrap'
                        }}
                        onClick={() => handleDelete(item)}
                      >
                        Delete
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
          {filteredAnnouncements.length === 0 && (
            <div style={{ gridColumn: '1 / -1', textAlign: 'center', padding: '48px', color: '#94a3b8' }}>
              No campaigns matching standard filters were found in history.
            </div>
          )}
        </div>
      </div>
      
      {showResponsesModal && selectedAnnouncementForResponses && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(15, 23, 42, 0.65)',
          backdropFilter: 'blur(4px)',
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          zIndex: 9999,
          padding: '20px'
        }}>
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '16px',
            width: '100%',
            maxWidth: '650px',
            maxHeight: '85vh',
            display: 'flex',
            flexDirection: 'column',
            boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
            overflow: 'hidden'
          }}>
            {/* Modal Header */}
            <div style={{
              padding: '20px 24px',
              borderBottom: '1px solid #e2e8f0',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              backgroundColor: '#f8fafc'
            }}>
              <div>
                <h3 style={{ margin: 0, fontSize: '1.2rem', fontWeight: 800, color: '#1e293b' }}>
                  User Responses Log
                </h3>
                <span style={{ fontSize: '0.8rem', color: '#64748b', marginTop: '2px', display: 'block' }}>
                  Campaign: "{selectedAnnouncementForResponses.title}"
                </span>
              </div>
              <button
                style={{
                  border: 'none',
                  background: 'none',
                  fontSize: '1.5rem',
                  fontWeight: 300,
                  color: '#64748b',
                  cursor: 'pointer',
                  padding: '4px',
                  lineHeight: 1
                }}
                onClick={() => {
                  setShowResponsesModal(false);
                  setSelectedAnnouncementForResponses(null);
                  setResponsesList([]);
                }}
              >
                &times;
              </button>
            </div>

            {/* Modal Body */}
            <div style={{ padding: '24px', overflowY: 'auto', flex: 1, display: 'flex', flexDirection: 'column', gap: '20px' }}>
              {responsesLoading ? (
                <div style={{ textAlign: 'center', padding: '40px 0', color: '#64748b' }}>
                  <div className="spinner" style={{ display: 'inline-block', width: '24px', height: '24px', border: '3px solid #e2e8f0', borderTop: '3px solid #219ebc', borderRadius: '50%', animation: 'spin 1s linear infinite', marginBottom: '12px' }}></div>
                  <p style={{ margin: 0, fontSize: '0.9rem' }}>Loading responses from database...</p>
                  <style>{`
                    @keyframes spin {
                      0% { transform: rotate(0deg); }
                      100% { transform: rotate(360deg); }
                    }
                  `}</style>
                </div>
              ) : (
                <>
                  {/* Results Summary Chart */}
                  {responsesList.length > 0 && (
                    <div style={{
                      backgroundColor: '#f0fdf4',
                      border: '1px solid #bbf7d0',
                      borderRadius: '12px',
                      padding: '16px',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '12px'
                    }}>
                      <h4 style={{ margin: 0, fontSize: '0.9rem', fontWeight: 800, color: '#166534', display: 'flex', justifyContent: 'space-between' }}>
                        <span>📊 Response Summary</span>
                        <span style={{ fontSize: '0.8rem', fontWeight: 500, color: '#15803d' }}>
                          Total Votes: {responsesList.length}
                        </span>
                      </h4>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                        {(() => {
                          const optionCounts = {};
                          responsesList.forEach(r => {
                            (r.selected_options || []).forEach(opt => {
                              optionCounts[opt] = (optionCounts[opt] || 0) + 1;
                            });
                          });

                          return Object.entries(optionCounts)
                            .sort((a, b) => b[1] - a[1])
                            .map(([opt, count]) => {
                              const pct = Math.round((count / responsesList.length) * 100);
                              return (
                                <div key={opt} style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.825rem', color: '#1e293b', fontWeight: 600 }}>
                                    <span>{opt}</span>
                                    <span>{count} votes ({pct}%)</span>
                                  </div>
                                  <div style={{ width: '100%', height: '8px', backgroundColor: '#e2e8f0', borderRadius: '4px', overflow: 'hidden' }}>
                                    <div style={{ width: `${pct}%`, height: '100%', backgroundColor: '#22c55e', borderRadius: '4px' }}></div>
                                  </div>
                                </div>
                              );
                            });
                        })()}
                      </div>
                    </div>
                  )}

                  {/* Individual Responses List */}
                  <div>
                    <h4 style={{ margin: '0 0 12px 0', fontSize: '0.9rem', fontWeight: 800, color: '#475569' }}>
                      📋 Responses Feed ({responsesList.length})
                    </h4>
                    {responsesList.length === 0 ? (
                      <div style={{ textAlign: 'center', padding: '30px 0', border: '1px dashed #cbd5e1', borderRadius: '12px', color: '#94a3b8' }}>
                        No user responses recorded for this dialog yet.
                      </div>
                    ) : (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                        {responsesList.map(response => {
                          const user = usersList.find(u => u.id === response.user_id) || { name: 'App User', email: 'authenticated_user', role: 'suki' };
                          return (
                            <div key={response.id} style={{
                              padding: '12px 16px',
                              border: '1px solid #f1f5f9',
                              backgroundColor: '#fafafa',
                              borderRadius: '10px',
                              display: 'flex',
                              justifyContent: 'space-between',
                              alignItems: 'center',
                              gap: '12px'
                            }}>
                              <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                                <span style={{ fontSize: '0.85rem', fontWeight: 700, color: '#1e293b' }}>
                                  {user.name}
                                </span>
                                <span style={{ fontSize: '0.725rem', color: '#64748b' }}>
                                  Role: <span style={{ textTransform: 'capitalize', fontWeight: 600 }}>{user.role}</span>
                                </span>
                              </div>
                              
                              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '2px' }}>
                                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px', justifyContent: 'flex-end' }}>
                                  {(response.selected_options || []).map((opt, oidx) => (
                                    <span key={oidx} style={{
                                      fontSize: '0.75rem',
                                      fontWeight: 800,
                                      backgroundColor: '#ffedd5',
                                      color: '#ea580c',
                                      border: '1px solid #fed7aa',
                                      padding: '2px 8px',
                                      borderRadius: '6px'
                                    }}>
                                      {opt}
                                    </span>
                                  ))}
                                </div>
                                <span style={{ fontSize: '0.675rem', color: '#94a3b8' }}>
                                  {new Date(response.created_at).toLocaleString()}
                                </span>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </div>
                </>
              )}
            </div>

            {/* Modal Footer */}
            <div style={{
              padding: '16px 24px',
              borderTop: '1px solid #e2e8f0',
              display: 'flex',
              justifyContent: 'flex-end',
              backgroundColor: '#f8fafc'
            }}>
              <button
                style={{
                  padding: '8px 16px',
                  borderRadius: '8px',
                  border: '1px solid #cbd5e1',
                  backgroundColor: '#ffffff',
                  color: '#475569',
                  fontWeight: 700,
                  cursor: 'pointer',
                  fontSize: '0.85rem'
                }}
                onClick={() => {
                  setShowResponsesModal(false);
                  setSelectedAnnouncementForResponses(null);
                  setResponsesList([]);
                }}
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
