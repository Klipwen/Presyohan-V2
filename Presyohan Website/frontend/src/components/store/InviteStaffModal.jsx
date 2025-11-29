import React, { useEffect, useRef, useState } from 'react';
import { supabase } from '../../config/supabaseClient';
import addStaffIcon from '../../assets/icon_add_staff.png';
import storeIcon from '../../assets/icon_store.png';

export default function InviteStaffModal({ open, onClose, storeId, storeName, isOwner }) {
  const [searchTerm, setSearchTerm] = useState('');
  const [role, setRole] = useState('employee');
  const [code, setCode] = useState('');
  const [codeCreatedAt, setCodeCreatedAt] = useState(null);
  const [loadingCode, setLoadingCode] = useState(false);
  const [inviting, setInviting] = useState(false);
  const searchInputRef = useRef(null);
  const CODE_EXPIRY_MS = 24 * 60 * 60 * 1000; // 24 hours
  const [timeLeftMs, setTimeLeftMs] = useState(null);
  const [inviteError, setInviteError] = useState('');
  const [selectedUser, setSelectedUser] = useState(null);
  const [isSearching, setIsSearching] = useState(false);
  const [searchStatus, setSearchStatus] = useState('idle');
  // Some Supabase drivers return TIMESTAMP WITHOUT TIME ZONE strings (no 'Z'),
  // which JS interprets as local time. To avoid timezone offset shrinking the
  // countdown (e.g., showing ~16h left for UTC+8), normalize to UTC when needed.
  const parseInviteTimestampMs = (ts) => {
    if (!ts) return null;
    try {
      const s = typeof ts === 'string' ? ts : ts.toString();
      const looksZoned = /[zZ]|[+-]\d{2}:?\d{2}$/.test(s);
      const d = new Date(looksZoned ? s : s + 'Z');
      const ms = d.getTime();
      return Number.isNaN(ms) ? null : ms;
    } catch {
      return null;
    }
  };

  const isCodeValid = !!codeCreatedAt && timeLeftMs !== null && timeLeftMs > 0 && !!code;

  const formatDuration = (ms) => {
    const totalSeconds = Math.max(0, Math.floor(ms / 1000));
    const h = Math.floor(totalSeconds / 3600).toString().padStart(2, '0');
    const m = Math.floor((totalSeconds % 3600) / 60).toString().padStart(2, '0');
    const s = (totalSeconds % 60).toString().padStart(2, '0');
    return `${h}:${m}:${s}`;
  };

  useEffect(() => {
    const loadCode = async () => {
      if (!open || !storeId) return;
      setLoadingCode(true);
      try {
        const { data, error } = await supabase
          .from('stores')
          .select('invite_code, invite_code_created_at')
          .eq('id', storeId)
          .maybeSingle();
        if (error) throw error;
        setCode(data?.invite_code || '');
        setCodeCreatedAt(data?.invite_code_created_at || null);
        if (data?.invite_code_created_at) {
          const created = parseInviteTimestampMs(data.invite_code_created_at);
          setTimeLeftMs(Math.max(0, CODE_EXPIRY_MS - (Date.now() - created)));
        } else {
          setTimeLeftMs(null);
        }
      } catch (e) {
        // Keep empty if failed
        setCode('');
        setCodeCreatedAt(null);
        setTimeLeftMs(null);
        console.warn('Failed to load invite code', e);
      } finally {
        setLoadingCode(false);
      }
    };
    loadCode();
  }, [open, storeId]);

  useEffect(() => {
    if (open) {
      searchInputRef.current?.focus();
    } else {
      setSearchTerm('');
      setSelectedUser(null);
      setSearchStatus('idle');
      setRole('employee');
      setInviteError('');
    }
  }, [open]);

  // Tick countdown every second and clear code on expiry
  useEffect(() => {
    if (!codeCreatedAt) {
      setTimeLeftMs(null);
      return;
    }
    const created = parseInviteTimestampMs(codeCreatedAt);
    const tick = () => {
      const remaining = CODE_EXPIRY_MS - (Date.now() - created);
      setTimeLeftMs(Math.max(0, remaining));
      if (remaining <= 0) {
        // Clear code display when expired
        setCode('');
      }
    };
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [codeCreatedAt, CODE_EXPIRY_MS]);

  const regenerateCode = async () => {
    if (!storeId) return;
    try {
      setLoadingCode(true);
      const { data, error } = await supabase.rpc('regenerate_invite_code', { p_store_id: storeId });
      if (error) throw error;
      const row = Array.isArray(data) ? data[0] : data;
      setCode(row?.invite_code || '');
      setCodeCreatedAt(row?.invite_code_created_at || null);
      if (row?.invite_code_created_at) {
        const created = parseInviteTimestampMs(row.invite_code_created_at);
        setTimeLeftMs(Math.max(0, CODE_EXPIRY_MS - (Date.now() - created)));
      } else {
        setTimeLeftMs(null);
      }
    } catch (e) {
      console.warn('Failed to regenerate code', e);
    } finally {
      setLoadingCode(false);
    }
  };

  useEffect(() => {
    setInviteError('');
    if (!searchTerm.trim()) {
      setSearchStatus('idle');
      setSelectedUser(null);
      return;
    }
    setSearchStatus('searching');
    setIsSearching(true);
    setSelectedUser(null);
    const timerId = setTimeout(async () => {
      try {
        const { data, error } = await supabase.rpc('search_app_user', { search_term: searchTerm.trim() });
        if (error) throw error;
        if (data && data.length > 0) {
          setSelectedUser(data[0]);
          setSearchStatus('found');
        } else {
          setSearchStatus('not-found');
        }
      } catch (e) {
        console.error(e);
        setSearchStatus('error');
      } finally {
        setIsSearching(false);
      }
    }, 500);
    return () => clearTimeout(timerId);
  }, [searchTerm]);

  const copyCode = async () => {
    try {
      if (!code) return;
      await navigator.clipboard.writeText(code);
    } catch (e) {
      console.error('Failed to copy', e);
    }
  };

  const sendInvite = async (e) => {
    e?.preventDefault();
    if (!storeId || !selectedUser) return;
    try {
      setInviting(true);
      setInviteError('');
      const { error } = await supabase.rpc('send_store_invitation', {
        p_store_id: storeId,
        p_email: selectedUser.email,
        p_role: role
      });
      if (error) throw error;
      onClose?.();
    } catch (e) {
      setInviteError(e.message || 'Failed to send invitation');
    } finally {
      setInviting(false);
    }
  };

  if (!open) return null;

  return (
    <div style={{
      position: 'fixed',
      top: 0,
      left: 0,
      right: 0,
      bottom: 0,
      background: 'rgba(0, 0, 0, 0.5)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      zIndex: 9999,
      padding: '20px'
    }} onClick={(e) => { if (e.target === e.currentTarget) onClose?.(); }}>
      <div style={{
        background: 'white',
        borderRadius: '20px',
        maxWidth: '500px',
        width: '100%',
        boxShadow: '0 10px 40px rgba(0, 0, 0, 0.2)',
        overflow: 'hidden'
      }}>
        {/* Header */}
        <div style={{
          background: 'linear-gradient(135deg, #ffb800 0%, #ff8c00 100%)',
          padding: '20px',
          textAlign: 'center',
        }}>
          <h2 style={{
            margin: 0,
            fontSize: '1.5rem',
            color: 'white',
            fontWeight: '700'
          }}>Invite Staff</h2>
        </div>

        {/* Content */}
        <div style={{ padding: '30px' }}>
          {/* Icon and Store Name */}
          <div style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            marginBottom: '25px'
          }}>
            {/* Real icon: add staff inside orange circle */}
            <div style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              marginBottom: '15px',
              position: 'relative'
            }}>
              <img src={addStaffIcon} alt="Invite Staff" style={{ width: '100px', height: '100px'}} />
            </div>
            <div style={{
              fontSize: '1.2rem',
              fontWeight: '700',
              color: '#ff8c00'
            }}>{storeName || 'Store'}</div>
          </div>

          {/* Store Code Section */}
          <div style={{
            background: '#f5f4f3ff',
            padding: '20px',
            borderRadius: '15px',
            marginBottom: '25px'
          }}>
            <div style={{
              fontSize: '0.85rem',
              color: '#666',
              marginBottom: '10px',
              fontWeight: '600'
            }}>Store Code</div>
            
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: '15px',
              flexWrap: 'wrap'
            }}>
              {/* Real store icon: white on orange background */}
              <div style={{
                width: '50px',
                height: '50px',
                background: '#ff8c00',
                borderRadius: '10px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}>
                <img src={storeIcon} alt="Store" style={{ width: '28px', height: '28px', filter: 'brightness(0) invert(1)' }} />
              </div>

              {/* Code Display with top counter */}
              <div style={{ flex: 1, minWidth: '120px' }}>
                <div style={{ position: 'relative' }}>
                  {isCodeValid && (
                    <div style={{
                      position: 'absolute',
                      top: 6,
                      left: '50%',
                      transform: 'translateX(-50%)',
                      fontSize: '0.8rem',
                      color: '#666'
                    }}>
                      Expires in {formatDuration(timeLeftMs)}
                    </div>
                  )}
                  <div style={{
                    background: 'white',
                    color: '#00bcd4',
                    padding: '12px 20px',
                    paddingTop: isCodeValid ? '28px' : '12px',
                    borderRadius: '10px',
                    fontSize: '1.5rem',
                    fontWeight: '700',
                    letterSpacing: '3px',
                    textAlign: 'center'
                  }}>
                    {loadingCode ? '...' : (isCodeValid ? code : '')}
                  </div>
                </div>
              </div>

              {/* Copy Button */}
              <button
                onClick={copyCode}
                disabled={!isCodeValid}
                style={{
                  width: '45px',
                  height: '45px',
                  background: '#00bcd4',
                  border: 'none',
                  borderRadius: '10px',
                  cursor: isCodeValid ? 'pointer' : 'not-allowed',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  opacity: isCodeValid ? 1 : 0.5
                }}
              >
                <svg width="24" height="24" fill="white" viewBox="0 0 24 24">
                  <path d="M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z"/>
                </svg>
              </button>
            </div>

            {/* Regenerate Button */}
            {isOwner && (
              <button
                onClick={regenerateCode}
                disabled={loadingCode}
                style={{
                  marginTop: '15px',
                  width: '100%',
                  padding: '10px',
                  background: 'transparent',
                  border: '2px solid #ff8c00',
                  borderRadius: '10px',
                  color: '#ff8c00',
                  fontWeight: '600',
                  cursor: loadingCode ? 'not-allowed' : 'pointer',
                  fontSize: '0.9rem'
                }}
              >
                {loadingCode ? 'Regenerating...' : 'Regenerate Code'}
              </button>
            )}
          </div>

          <form onSubmit={sendInvite} style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            <div>
              <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: '600', color: '#666', marginBottom: '8px' }}>
                User ID or Email Address
              </label>
              <div style={{ position: 'relative' }}>
                <input
                  ref={searchInputRef}
                  type="text"
                  placeholder="enter valid user ID (PH25-xxxx) or email"
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  style={{
                    width: '100%', padding: '14px', paddingRight: '40px',
                    border: `2px solid ${searchStatus === 'found' ? '#ff8c00' : searchStatus === 'not-found' ? '#d32f2f' : '#e0e0e0'}`,
                    borderRadius: '10px', fontSize: '1rem', outline: 'none', boxSizing: 'border-box'
                  }}
                />
                <div style={{ position: 'absolute', right: '14px', top: '50%', transform: 'translateY(-50%)', display: 'flex', alignItems: 'center' }}>
                  {isSearching ? (
                    <div style={{ width: '20px', height: '20px', borderRadius: '50%', border: '2px solid #ccc', borderTopColor: '#ff8c00', animation: 'spin 1s linear infinite' }} />
                  ) : (
                    <svg width="20" height="20" viewBox="0 0 24 24" fill={searchStatus === 'found' ? '#ff8c00' : '#999'}>
                      <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/>
                    </svg>
                  )}
                </div>
              </div>

              {searchStatus === 'not-found' && (
                <div style={{ marginTop: '6px', fontSize: '0.85rem', color: '#d32f2f' }}>
                  No user found with this ID or Email.
                </div>
              )}

              {selectedUser && (
                <div style={{ 
                  marginTop: '10px', 
                  padding: '12px', 
                  background: '#fffbe6', 
                  border: '1px solid #ffe8b3', 
                  borderRadius: '12px', 
                  display: 'flex', 
                  alignItems: 'center', 
                  gap: '12px' 
                }}>
                  {selectedUser.avatar_url ? (
                    <img src={selectedUser.avatar_url} alt="Avatar" style={{ width: '40px', height: '40px', borderRadius: '50%', objectFit: 'cover' }} />
                  ) : (
                    <div style={{ width: '40px', height: '40px', borderRadius: '50%', background: '#e0e0e0', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#666' }}>
                      <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/></svg>
                    </div>
                  )}
                  <div style={{ overflow: 'hidden' }}>
                    <div style={{ fontWeight: '700', color: '#333', fontSize: '0.95rem' }}>{selectedUser.name || 'Unnamed User'}</div>
                    <div style={{ fontSize: '0.8rem', color: '#666', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                      <span style={{ fontWeight: '600', color: '#ff8c00' }}>{selectedUser.user_code}</span> • {selectedUser.email}
                    </div>
                  </div>
                </div>
              )}
            </div>

            {/* Role Selector */}
            <div>
              <label style={{
                display: 'block',
                fontSize: '0.85rem',
                fontWeight: '600',
                color: '#666',
                marginBottom: '8px'
              }}>Staff Role</label>
              <div style={{ position: 'relative' }}>
                <select
                  value={role}
                  onChange={(e) => setRole(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '14px',
                    border: '2px solid #e0e0e0',
                    borderRadius: '10px',
                    fontSize: '1rem',
                    outline: 'none',
                    appearance: 'none',
                    background: 'white',
                    cursor: 'pointer',
                    boxSizing: 'border-box',
                    color: '#ff8c00',
                    fontWeight: '500'
                  }}
                >
                  <option value="employee">View only price list</option>
                  <option value="manager">Manage prices</option>
                </select>
                <svg
                  style={{
                    position: 'absolute',
                    right: '15px',
                    top: '50%',
                    transform: 'translateY(-50%)',
                    pointerEvents: 'none'
                  }}
                  width="20"
                  height="20"
                  fill="#ff8c00"
                  viewBox="0 0 24 24"
                >
                  <path d="M7 10l5 5 5-5z"/>
                </svg>
              </div>
            </div>

            {/* Action Buttons */}
            <div style={{
              display: 'flex',
              gap: '15px',
              marginTop: '10px'
            }}>
              <button
                type="button"
                onClick={onClose}
                style={{
                  flex: 1,
                  padding: '14px',
                  background: '#00bcd4',
                  color: 'white',
                  border: 'none',
                  borderRadius: '25px',
                  fontSize: '1rem',
                  fontWeight: '700',
                  cursor: 'pointer'
                }}
              >
                Back
              </button>
              <button
                type="submit"
                disabled={inviting || !selectedUser}
                style={{
                  flex: 1,
                  padding: '14px',
                  background: 'linear-gradient(135deg, #ffb800 0%, #ff8c00 100%)',
                  color: 'white',
                  border: 'none',
                  borderRadius: '25px',
                  fontSize: '1rem',
                  fontWeight: '700',
                  cursor: (inviting || !selectedUser) ? 'not-allowed' : 'pointer',
                  opacity: (inviting || !selectedUser) ? 0.7 : 1
                }}
              >
                {inviting ? 'Inviting...' : 'Invite'}
              </button>
            </div>
            {inviteError && (
              <div style={{
                marginTop: '8px',
                color: '#d32f2f',
                fontSize: '0.9rem',
                textAlign: 'center'
              }}>
                {inviteError}
              </div>
            )}
          </form>
        </div>
      </div>

      <style>{`
        @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
        @media (max-width: 480px) {
          input, select {
            font-size: 16px !important;
          }
        }
      `}</style>
    </div>
  );
}
