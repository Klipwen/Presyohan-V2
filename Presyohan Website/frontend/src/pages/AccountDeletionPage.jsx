import React, { useState, useEffect } from 'react';
import { useSearchParams, useNavigate, Link } from 'react-router-dom';
import { supabase } from '../config/supabaseClient';
import presyohanLogo from '../assets/ic_launcher.png';
import '../styles/AccountDeletion.css';

// Bespoke Lucide-style SVG Vector Icons (Strictly Zero Emojis)
const Icons = {
  Shield: () => (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
    </svg>
  ),
  Lock: () => (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
      <path d="M7 11V7a5 5 0 0 1 10 0v4" />
    </svg>
  ),
  Trash: () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="3 6 5 6 21 6" />
      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
      <line x1="10" y1="11" x2="10" y2="17" />
      <line x1="14" y1="11" x2="14" y2="17" />
    </svg>
  ),
  AlertTriangle: () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
      <line x1="12" y1="9" x2="12" y2="13" />
      <line x1="12" y1="17" x2="12.01" y2="17" />
    </svg>
  ),
  ArrowLeft: () => (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
      <line x1="19" y1="12" x2="5" y2="12" />
      <polyline points="12 19 5 12 12 5" />
    </svg>
  ),
  Check: () => (
    <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="20 6 9 17 4 12" />
    </svg>
  ),
  CheckCircleBig: () => (
    <svg width="44" height="44" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
      <polyline points="22 4 12 14.01 9 11.01" />
    </svg>
  ),
  User: () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
      <circle cx="12" cy="7" r="4" />
    </svg>
  ),
  ExternalApp: () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
      <polyline points="15 3 21 3 21 9" />
      <line x1="10" y1="14" x2="21" y2="3" />
    </svg>
  ),
  Google: () => (
    <svg width="16" height="16" viewBox="0 0 24 24">
      <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
      <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
      <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z" />
      <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z" />
    </svg>
  )
};

export default function AccountDeletionPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const urlUid = searchParams.get('uid') || '';
  const urlEmail = searchParams.get('email') || '';

  // Auth & user state
  const [sessionUser, setSessionUser] = useState(null);
  const [userProfile, setUserProfile] = useState(null);
  const [loadingUser, setLoadingUser] = useState(true);

  // Form states
  const [loginEmail, setLoginEmail] = useState(urlEmail);
  const [loginPassword, setLoginPassword] = useState('');
  const [authSubmitting, setAuthSubmitting] = useState(false);

  // Confirmation states
  const [confirmationInput, setConfirmationInput] = useState('');
  const [reason, setReason] = useState('No longer needed');
  const [otherReasonText, setOtherReasonText] = useState('');
  const [reauthPassword, setReauthPassword] = useState('');
  const [isDeleting, setIsDeleting] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [deletionSuccess, setDeletionSuccess] = useState(false);
  const [deletedTimestamp, setDeletedTimestamp] = useState('');

  // 1. Check existing session & fetch user profile
  useEffect(() => {
    async function initSession() {
      setLoadingUser(true);
      try {
        const { data: { session } } = await supabase.auth.getSession();
        if (session?.user) {
          setSessionUser(session.user);
          const { data: profile } = await supabase
            .from('app_users')
            .select('id, full_name, email, phone, role, subscription_tier')
            .eq('id', session.user.id)
            .maybeSingle();

          if (profile) {
            setUserProfile(profile);
          }
        }
      } catch (err) {
        console.error('Error fetching session:', err);
      } finally {
        setLoadingUser(false);
      }
    }
    initSession();
  }, []);

  // 2. Handle Inline Login for Unauthenticated Users
  const handleInlineLogin = async (e) => {
    e.preventDefault();
    setErrorMessage('');
    if (!loginEmail.trim() || !loginPassword.trim()) {
      setErrorMessage('Please enter both your account email and password.');
      return;
    }

    setAuthSubmitting(true);
    try {
      const { data, error } = await supabase.auth.signInWithPassword({
        email: loginEmail.trim(),
        password: loginPassword.trim()
      });

      if (error) {
        throw error;
      }

      if (data?.user) {
        setSessionUser(data.user);
        const { data: profile } = await supabase
          .from('app_users')
          .select('id, full_name, email, phone, role, subscription_tier')
          .eq('id', data.user.id)
          .maybeSingle();
        setUserProfile(profile || { email: data.user.email });
      }
    } catch (err) {
      setErrorMessage(err.message || 'Authentication failed. Please verify your credentials.');
    } finally {
      setAuthSubmitting(false);
    }
  };

  // 3. Handle Google OAuth Sign In
  const handleGoogleSignIn = async () => {
    setErrorMessage('');
    try {
      const { error } = await supabase.auth.signInWithOAuth({
        provider: 'google',
        options: {
          redirectTo: `${window.location.origin}/delete-account`
        }
      });
      if (error) throw error;
    } catch (err) {
      setErrorMessage(err.message || 'Google sign in failed.');
    }
  };

  // 4. Handle Permanent Account Deletion Execution
  const handlePermanentDeletion = async (e) => {
    e.preventDefault();
    setErrorMessage('');

    if (confirmationInput.trim().toUpperCase() !== 'DELETE') {
      setErrorMessage('Please type DELETE exactly in the confirmation field.');
      return;
    }

    if (!sessionUser) {
      setErrorMessage('Active user authentication is required to execute deletion.');
      return;
    }

    setIsDeleting(true);
    try {
      // Execute the database purge RPC
      const { data, error } = await supabase.rpc('delete_user_account');

      if (error) {
        throw new Error(error.message || 'Failed to complete database account purge.');
      }

      if (data && data.success === false) {
        throw new Error(data.error || data.message || 'Failed to complete account purge.');
      }

      // Record deletion timestamp
      const nowFormatted = new Date().toLocaleString('en-US', {
        timeZone: 'Asia/Manila',
        dateStyle: 'medium',
        timeStyle: 'short'
      });
      setDeletedTimestamp(nowFormatted);

      // Sign out from Supabase Auth
      await supabase.auth.signOut();
      setSessionUser(null);
      setUserProfile(null);
      setDeletionSuccess(true);
    } catch (err) {
      console.error('Account deletion error:', err);
      setErrorMessage(err.message || 'Unable to delete account. Please contact Presyohan support.');
    } finally {
      setIsDeleting(false);
    }
  };

  // 5. Success Screen
  if (deletionSuccess) {
    return (
      <div className="del-page-body">
        <div className="del-container">
          <div className="del-success-card">
            <div className="del-success-icon-disc">
              <Icons.CheckCircleBig />
            </div>
            <h1 className="del-success-heading">Account Purge Complete</h1>
            <p className="del-success-text">
              Your Presyohan account, user profile, associated personal identifiers, and access permissions
              have been permanently deleted in accordance with data privacy standards.
            </p>

            <div className="del-receipt-box">
              <div className="del-receipt-row">
                <span>Deletion Status:</span>
                <strong style={{ color: 'var(--accent-emerald)' }}>Permanently Purged</strong>
              </div>
              <div className="del-receipt-row">
                <span>Timestamp (PHT):</span>
                <span>{deletedTimestamp || 'Completed'}</span>
              </div>
              <div className="del-receipt-row">
                <span>Personal Data Retained:</span>
                <span>None (0 Records)</span>
              </div>
              <div className="del-receipt-row">
                <span>Authentication Record:</span>
                <span>Expunged</span>
              </div>
              <div className="del-receipt-row">
                <span>Store Team Continuity:</span>
                <span>Orphaned records detached</span>
              </div>
            </div>

            <a href="presyohan://app" className="del-btn-app-return">
              <Icons.ExternalApp />
              <span>RETURN TO PRESYOHAN APP</span>
            </a>

            <div>
              <Link to="/" className="del-btn-web-home">
                Return to Presyohan Web Homepage
              </Link>
            </div>
          </div>
        </div>
      </div>
    );
  }

  // 6. Main 2-Column Split Console
  return (
    <div className="del-page-body">
      <div className="del-container">
        {/* Header Navigation */}
        <header className="del-nav-header">
          <div className="del-nav-left">
            <button type="button" onClick={() => navigate(-1)} className="del-btn-back">
              <Icons.ArrowLeft />
              <span>Back</span>
            </button>

            <Link to="/" className="del-brand-lockup">
              <img src={presyohanLogo} alt="Presyohan Logo" className="del-brand-logo-img" />
              <div className="del-brand-text-col">
                <span className="del-brand-atong">atong</span>
                <span className="del-brand-main">
                  <span className="del-brand-presyo">presyo</span>
                  <span className="del-brand-han">han?</span>
                </span>
              </div>
            </Link>
          </div>

          <div className="del-compliance-badge">
            <Icons.Shield />
            <span>Google Play &amp; Privacy Policy Compliant</span>
          </div>
        </header>

        {/* 2-Column Grid Layout */}
        <div className="del-grid-layout">
          {/* Left Column: Data Purging Policy & Transparency Breakdown */}
          <div className="del-info-card">
            <div className="del-info-header">
              <h1 className="del-info-title">Account &amp; Data Deletion</h1>
              <p className="del-info-subtitle">
                Request permanent removal of your account, authentication records, and associated personal information.
              </p>
            </div>

            {/* 2x2 Purge Breakdown Capacity Grid */}
            <div className="del-capacity-box">
              <div className="del-capacity-label">DATA POINTS PERMANENTLY PURGED</div>
              <div className="del-capacity-grid">
                <div className="del-capacity-cell">
                  <div className="del-capacity-name">USER PROFILE</div>
                  <div className="del-capacity-val">Full Details</div>
                </div>
                <div className="del-capacity-cell">
                  <div className="del-capacity-name">STORE MEMBERSHIPS</div>
                  <div className="del-capacity-val">Revoked</div>
                </div>
                <div className="del-capacity-cell">
                  <div className="del-capacity-name">SUKI PARTNERS</div>
                  <div className="del-capacity-val">Unlinked</div>
                </div>
                <div className="del-capacity-cell">
                  <div className="del-capacity-name">AUTH CREDENTIALS</div>
                  <div className="del-capacity-val">Expunged</div>
                </div>
              </div>
            </div>

            {/* Section 1: Data Categories Subject to Deletion */}
            <div className="del-section-header">
              <Icons.Trash />
              <span>DATA SCHEDULED FOR PERMANENT REMOVAL</span>
            </div>
            <div className="del-data-list">
              <div className="del-data-row">
                <div className="del-data-dot">
                  <Icons.Check />
                </div>
                <span>
                  <strong>Personal Identity &amp; Profile:</strong> Name, registered email address, contact numbers, and profile avatars stored in <code>app_users</code>.
                </span>
              </div>
              <div className="del-data-row">
                <div className="del-data-dot">
                  <Icons.Check />
                </div>
                <span>
                  <strong>Store Memberships &amp; Roles:</strong> All staff, cashier, and manager assignments across private and partner stores.
                </span>
              </div>
              <div className="del-data-row">
                <div className="del-data-dot">
                  <Icons.Check />
                </div>
                <span>
                  <strong>Suki &amp; Customer Relationships:</strong> Suki partner associations, favorite store connections, and customer pairing channels.
                </span>
              </div>
              <div className="del-data-row">
                <div className="del-data-dot">
                  <Icons.Check />
                </div>
                <span>
                  <strong>Notifications &amp; Activity:</strong> In-app notifications sent or received, rating reviews, and support correspondence.
                </span>
              </div>
              <div className="del-data-row">
                <div className="del-data-dot">
                  <Icons.Check />
                </div>
                <span>
                  <strong>Authentication Security:</strong> Login passwords, Google OAuth tokens, and active sessions stored in <code>auth.users</code>.
                </span>
              </div>
            </div>

            {/* Section 2: Store Data Continuity Note */}
            <div className="del-section-header teal-header">
              <Icons.Shield />
              <span>SHARED STORE INVENTORY CONTINUITY</span>
            </div>
            <div className="del-notice-box">
              <span className="del-notice-title">Shared Business Data:</span>
              Product catalog items and historical price lists created within collaborative stores remain accessible to surviving store team members to prevent disruption to business operations. Stores owned solely by your account are safely detached and archived.
            </div>

            {/* Section 3: Irreversibility Policy */}
            <div className="del-section-header danger-header">
              <Icons.AlertTriangle />
              <span>IRREVERSIBLE ACTION</span>
            </div>
            <div className="del-data-list">
              <div className="del-data-row">
                <div className="del-data-dot" style={{ backgroundColor: 'var(--presyo-orange-warm)' }}>
                  <Icons.Check />
                </div>
                <span>
                  Account deletion executes immediately upon confirmation. Once deleted, this action cannot be reversed, and subscription benefits or trial privileges cannot be transferred.
                </span>
              </div>
            </div>
          </div>

          {/* Right Column: Verification & Deletion Console */}
          <div className="del-console-card">
            <div className="del-console-label">Identity Verification Console</div>

            {/* State A: User is Authenticated */}
            {sessionUser ? (
              <form onSubmit={handlePermanentDeletion}>
                <div className="del-user-pill">
                  <div className="del-user-avatar-disc">
                    <Icons.User />
                  </div>
                  <div className="del-user-meta">
                    <span className="del-user-name">{userProfile?.full_name || 'Authenticated User'}</span>
                    <span className="del-user-email">{sessionUser.email}</span>
                    <span className="del-user-status">Verified Active Session</span>
                  </div>
                </div>

                <div className="del-form-group">
                  <label className="del-input-label">Reason for leaving (Optional)</label>
                  <select
                    className="del-select"
                    value={reason}
                    onChange={(e) => setReason(e.target.value)}
                  >
                    <option value="No longer needed">No longer needed</option>
                    <option value="Switching to another platform">Switching to another platform</option>
                    <option value="Privacy / Data concerns">Privacy / Data concerns</option>
                    <option value="App performance / Technical issues">App performance / Technical issues</option>
                    <option value="Other">Other reason</option>
                  </select>
                </div>

                {reason === 'Other' && (
                  <div className="del-form-group">
                    <input
                      type="text"
                      className="del-input"
                      placeholder="Please specify reason..."
                      value={otherReasonText}
                      onChange={(e) => setOtherReasonText(e.target.value)}
                    />
                  </div>
                )}

                <div className="del-danger-box">
                  <Icons.AlertTriangle />
                  <span>
                    Warning: All personal data and permissions will be permanently purged immediately.
                  </span>
                </div>

                <div className="del-form-group">
                  <label className="del-input-label">
                    Type <strong>DELETE</strong> below to confirm permanent removal:
                  </label>
                  <input
                    type="text"
                    className="del-input"
                    placeholder="Type DELETE"
                    value={confirmationInput}
                    onChange={(e) => setConfirmationInput(e.target.value)}
                    required
                  />
                </div>

                {errorMessage && (
                  <div className="del-error-alert">
                    {errorMessage}
                  </div>
                )}

                <button
                  type="submit"
                  disabled={isDeleting || confirmationInput.trim().toUpperCase() !== 'DELETE'}
                  className="del-btn-danger"
                >
                  <Icons.Trash />
                  <span>{isDeleting ? 'PURGING ACCOUNT RECORDS...' : 'PERMANENTLY DELETE MY ACCOUNT'}</span>
                </button>

                <button
                  type="button"
                  onClick={() => navigate(-1)}
                  className="del-btn-secondary"
                >
                  <span>Cancel and Keep Account</span>
                </button>
              </form>
            ) : (
              /* State B: User is NOT Authenticated -> In-console Sign-in Verification */
              <div>
                <p style={{ fontSize: '13px', color: 'var(--slate-600)', lineHeight: '1.45', marginBottom: '14px' }}>
                  Please sign in to the Presyohan account you wish to permanently delete. Once authenticated, you will confirm the final deletion.
                </p>

                <form onSubmit={handleInlineLogin}>
                  <div className="del-form-group">
                    <label className="del-input-label">Account Email Address</label>
                    <input
                      type="email"
                      className="del-input"
                      placeholder="name@example.com"
                      value={loginEmail}
                      onChange={(e) => setLoginEmail(e.target.value)}
                      required
                    />
                  </div>

                  <div className="del-form-group">
                    <label className="del-input-label">Password</label>
                    <input
                      type="password"
                      className="del-input"
                      placeholder="Enter password"
                      value={loginPassword}
                      onChange={(e) => setLoginPassword(e.target.value)}
                      required
                    />
                  </div>

                  {errorMessage && (
                    <div className="del-error-alert">
                      {errorMessage}
                    </div>
                  )}

                  <button
                    type="submit"
                    disabled={authSubmitting}
                    className="del-btn-secondary"
                    style={{ backgroundColor: 'var(--presyo-orange)', color: '#ffffff', fontWeight: 800 }}
                  >
                    <Icons.Lock />
                    <span>{authSubmitting ? 'Verifying Account...' : 'Sign In to Proceed with Deletion'}</span>
                  </button>
                </form>

                <div style={{ textAlign: 'center', margin: '14px 0', fontSize: '12px', color: 'var(--slate-400)', fontWeight: 700 }}>
                  OR
                </div>

                <button
                  type="button"
                  onClick={handleGoogleSignIn}
                  className="del-btn-secondary"
                >
                  <Icons.Google />
                  <span>Verify with Google Account</span>
                </button>

                <Link to="/" className="del-cancel-link">
                  Cancel and return to homepage
                </Link>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
