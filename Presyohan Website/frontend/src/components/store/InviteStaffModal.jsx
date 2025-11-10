import React, { useEffect, useRef, useState } from 'react';
import { supabase } from '../../config/supabaseClient';
import addStaffIcon from '../../assets/icon_add_staff.png';
import storeIcon from '../../assets/icon_store.png';

export default function InviteStaffModal({ open, onClose, storeId, storeName, isOwner }) {
  const [email, setEmail] = useState('');
  const [role, setRole] = useState('employee');
  const [code, setCode] = useState('');
  const [codeCreatedAt, setCodeCreatedAt] = useState(null);
  const [loadingCode, setLoadingCode] = useState(false);
  const [inviting, setInviting] = useState(false);
  const emailRef = useRef(null);
  const CODE_EXPIRY_MS = 24 * 60 * 60 * 1000; // 24 hours
  const [timeLeftMs, setTimeLeftMs] = useState(null);
  const [inviteError, setInviteError] = useState('');
  const [emailStatus, setEmailStatus] = useState({ valid: false, exists: null, checking: false, message: '' });
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
      emailRef.current?.focus();
    } else {
      setEmail('');
      setRole('employee');
      setEmailStatus({ valid: false, exists: null, checking: false, message: '' });
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
  }, [codeCreatedAt]);

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

  // Validate email and check existence (debounced)
  useEffect(() => {
    setInviteError('');
    if (!email) {
      setEmailStatus({ valid: false, exists: null, checking: false, message: '' });
      return;
    }
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    const valid = emailRegex.test(email);
    if (!valid) {
      setEmailStatus({ valid: false, exists: null, checking: false, message: 'Enter a valid email' });
      return;
    }
    let active = true;
    setEmailStatus((s) => ({ ...s, valid: true, checking: true, message: '' }));
    const id = setTimeout(async () => {
      try {
        const { data, error } = await supabase.rpc('email_exists', { p_email: email });
        if (!active) return;
        if (error) throw error;
        const exists = !!data;
        setEmailStatus({ valid: true, exists, checking: false, message: exists ? '' : 'No account found. Ask them to sign up.' });
      } catch (e) {
        setEmailStatus({ valid: true, exists: null, checking: false, message: 'Could not verify email right now' });
      }
    }, 400);
    return () => { active = false; clearTimeout(id); };
  }, [email]);

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
    if (!storeId) return;
    if (!emailStatus.valid || emailStatus.checking || emailStatus.exists === false) return;
    try {
      setInviting(true);
      setInviteError('');
      const { error } = await supabase.rpc('send_store_invitation', {
        p_store_id: storeId,
        p_email: email,
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

          {/* Email Input */}
          <form onSubmit={sendInvite} style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            <div>
              <label style={{
                display: 'block',
                fontSize: '0.85rem',
                fontWeight: '600',
                color: '#666',
                marginBottom: '8px'
              }}>Email</label>
              <input
                ref={emailRef}
                type="email"
                placeholder="Input email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                style={{
                  width: '100%',
                  padding: '14px',
                  border: '2px solid #e0e0e0',
                  borderRadius: '10px',
                  fontSize: '1rem',
                  outline: 'none',
                  boxSizing: 'border-box'
                }}
              />
              {email && (
                <div style={{
                  marginTop: '6px',
                  fontSize: '0.85rem',
                  color: emailStatus.checking ? '#666' : (emailStatus.valid && emailStatus.exists) ? '#2e7d32' : '#d32f2f'
                }}>
                  {emailStatus.checking ? 'Checking email…' : (emailStatus.message || (emailStatus.valid && emailStatus.exists ? 'Account found' : ''))}
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
                disabled={inviting || !emailStatus.valid || emailStatus.checking || emailStatus.exists === false}
                style={{
                  flex: 1,
                  padding: '14px',
                  background: 'linear-gradient(135deg, #ffb800 0%, #ff8c00 100%)',
                  color: 'white',
                  border: 'none',
                  borderRadius: '25px',
                  fontSize: '1rem',
                  fontWeight: '700',
                  cursor: (inviting || !emailStatus.valid || emailStatus.checking || emailStatus.exists === false) ? 'not-allowed' : 'pointer',
                  opacity: (inviting || !emailStatus.valid || emailStatus.checking || emailStatus.exists === false) ? 0.7 : 1
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

      {/* Responsive Styles */}
      <style>{`
        @media (max-width: 480px) {
          input, select {
            font-size: 16px !important;
          }
        }
      `}</style>
    </div>
  );
}