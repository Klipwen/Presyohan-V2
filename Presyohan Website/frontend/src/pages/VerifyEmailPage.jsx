import React, { useEffect, useRef, useState } from 'react';
import AuthHeader from '../components/layout/AuthHeader';
import Footer from '../components/layout/Footer';
import '../styles/VerifyEmailPage.css';
import presyohanLogo from '../assets/presyohan_logo.png';
import { supabase } from '../config/supabaseClient';
import { useNavigate } from 'react-router-dom';

export default function VerifyEmailPage() {
  const inputsRef = useRef([]);
  const [code, setCode] = useState(['', '', '', '', '', '']);
  const [feedback, setFeedback] = useState('');
  const [cooldown, setCooldown] = useState(0);
  const [isVerifying, setIsVerifying] = useState(false);
  const [email, setEmail] = useState('');
  const [flow, setFlow] = useState('email');
  const [invalidCode, setInvalidCode] = useState(false);
  const navigate = useNavigate();

  // Resolve app origin from env when available; fallback to current origin.
  // If env mistakenly points to localhost, prefer current origin to avoid dev URL on production.
  const getAppOrigin = () => {
    const configured = import.meta.env.VITE_PUBLIC_APP_URL;
    if (configured) {
      try {
        const u = new URL(configured);
        if (u.hostname === 'localhost') return window.location.origin;
        return `${u.protocol}//${u.host}`;
      } catch {
        // Not a valid URL, ignore and fallback
      }
    }
    return window.location.origin;
  };

  useEffect(() => {
    inputsRef.current[0]?.focus();
  }, []);

  useEffect(() => {
    // Read email and flow from query or localStorage
    const params = new URLSearchParams(window.location.search);
    const e = params.get('email') || localStorage.getItem('pendingEmail');
    const f = params.get('flow') || localStorage.getItem('pendingVerificationType') || 'email';
    if (e) {
      setEmail(e);
      localStorage.setItem('pendingEmail', e);
    } else {
      setFeedback('Missing email. Please log in again.');
    }
    setFlow(f);
    localStorage.setItem('pendingVerificationType', f);
  }, []);

  useEffect(() => {
    let timer;
    if (cooldown > 0) {
      timer = setTimeout(() => setCooldown(cooldown - 1), 1000);
    }
    return () => clearTimeout(timer);
  }, [cooldown]);

  const onChangeDigit = (idx, value) => {
    const v = value.replace(/\D/g, '').slice(0, 1);
    const next = [...code];
    next[idx] = v;
    setCode(next);
    if (invalidCode) setInvalidCode(false);
    if (v && idx < inputsRef.current.length - 1) {
      inputsRef.current[idx + 1]?.focus();
    }
  };

  const onKeyDownDigit = (idx, e) => {
    if (e.key === 'Backspace' && !code[idx] && idx > 0) {
      inputsRef.current[idx - 1]?.focus();
    }
  };

  const verifyCode = async (e) => {
    e.preventDefault();
    const joined = code.join('');
    if (joined.length !== 6) {
      setFeedback('Please enter the 6-digit code.');
      setInvalidCode(true);
      return;
    }
    if (!email) {
      setFeedback('Missing email. Please return to login.');
      return;
    }
    setFeedback('');
    setIsVerifying(true);
    try {
      // Always verify using 'email' type because we request codes via signInWithOtp.
      const { data, error } = await supabase.auth.verifyOtp({
        type: 'email',
        email,
        token: joined
      });
      if (error) {
        setFeedback(error.message);
        setIsVerifying(false);
        return;
      }

      // After verifyOtp we should have a session. If not, attempt a fallback sign-in.
      const sessionRes = await supabase.auth.getSession();
  if (sessionRes?.data?.session) {
        // If we have a pending name saved from signup, update the user's
        // profile now that the session exists.
        const pendingName = localStorage.getItem('pendingName');
        const pendingPassword = localStorage.getItem('pendingPassword');
        if (pendingName) {
          try {
            await supabase.auth.updateUser({ data: { name: pendingName } });
            localStorage.removeItem('pendingName');
          } catch (err) {
            // Non-fatal: continue to navigate even if update fails.
            console.warn('Failed to update user name after verify:', err);
          }
        }
        if (pendingPassword) {
          try {
            await supabase.auth.updateUser({ password: pendingPassword });
            localStorage.removeItem('pendingPassword');
          } catch (err) {
            console.warn('Failed to set password after verify:', err);
          }
        }

        try {
          const user = sessionRes?.data?.session?.user;
          if (user) {
            const { data: existing } = await supabase
              .from('app_users')
              .select('id, name, email, avatar_url')
              .eq('id', user.id)
              .maybeSingle();
            const finalName = existing?.name ?? (pendingName || user.user_metadata?.name || null);
            const finalAvatar = existing?.avatar_url ?? (user.user_metadata?.avatar_url || null);
            if (existing?.id) {
              await supabase
                .from('app_users')
                .update({ email: user.email ?? existing.email })
                .eq('id', user.id);
            } else {
              await supabase
                .from('app_users')
                .insert({ id: user.id, email: user.email, name: finalName, avatar_url: finalAvatar });
            }
            await supabase.auth.updateUser({ data: { name: finalName, avatar_url: finalAvatar } });
          }
        } catch (err) {
          console.warn('Failed to persist app_users profile:', err);
        }

        // If we just set a password, verify it by signing out and signing back in.
        if (pendingPassword) {
          try {
            await supabase.auth.signOut();
            const { data: pwLogin, error: pwErr } = await supabase.auth.signInWithPassword({ email, password: pendingPassword });
            if (pwErr) {
              setFeedback(`Password verification failed: ${pwErr.message}`);
              setIsVerifying(false);
              return;
            }
          } catch (err) {
            setFeedback(`Password verification error: ${err.message}`);
            setIsVerifying(false);
            return;
          }
        }
        navigate('/stores', { replace: true });
        setIsVerifying(false);
        return;
      }

      // Small grace period to allow the session to initialize if needed.
      await new Promise((res) => setTimeout(res, 250));
      const postVerifySession = await supabase.auth.getSession();
      if (!postVerifySession?.data?.session) {
        setFeedback('Verified successfully. If you are not redirected, please try again.');
      }

      // After fallback signin, update name if present
      const pendingName2 = localStorage.getItem('pendingName');
      if (pendingName2) {
        try {
          await supabase.auth.updateUser({ data: { name: pendingName2 } });
          localStorage.removeItem('pendingName');
        } catch (err) {
          console.warn('Failed to update user name after fallback signin:', err);
        }
      }
      navigate('/stores', { replace: true });
      setIsVerifying(false);
    } catch (err) {
      setFeedback(err.message || String(err));
      setIsVerifying(false);
    }
  };

  const resendCode = async () => {
    if (cooldown > 0) return;
    if (!email) {
      setFeedback('Missing email. Please return to login.');
      return;
    }
    try {
      // Use signInWithOtp to request a fresh OTP. shouldCreateUser=true for signup flows.
      const { error } = await supabase.auth.signInWithOtp({
        email,
        options: { 
          shouldCreateUser: flow === 'signup',
          // Ensure magic link redirects to deployed site, not localhost
          emailRedirectTo: `${getAppOrigin()}/auth/callback`
        }
      });
      if (error) throw error;
      // Increase cooldown to reduce rapid resend attempts and give more time.
      setCooldown(60);
      setFeedback('A new 6-digit code was sent to your email.');
    } catch (err) {
      setFeedback(err.message);
    }
  };

  return (
    <div className="page-root verify-page-root">
      <AuthHeader />

      <div className="wrapper">
        <div className="left-section">
          <div className="brand">
              <img src={presyohanLogo} alt="Presyohan Logo" />
               <p>atong</p>
            <h1>
              <span className="presyo">presyo</span><span className="han">han?</span>
            </h1>
          </div>
        </div>

        <div className="right-section">
          <div className="verify-container">
            <div className="verify-icon">
              <svg viewBox="0 0 24 24" fill="none">
                <circle cx="12" cy="12" r="10" stroke="#ff8c00" strokeWidth="2" />
                <path d="M12 6v6l4 2" stroke="#ff8c00" strokeWidth="2" strokeLinecap="round" />
              </svg>
            </div>

            <h2>Verify your email</h2>
            <p className="verify-message">
              {flow === 'signup' ? (
                <>Enter the 6 digit code we sent to <span className="email">{email || 'your email'}</span> to confirm your account.</>
              ) : (
                <>Enter the 6 digit code we sent to <span className="email">{email || 'your email'}</span> to complete login.</>
              )}
            </p>

            <div className="code-inputs">
            {code.map((digit, idx) => (
              <input
                key={idx}
                type="text"
                className={`code-input ${invalidCode ? 'input-error' : ''}`}
                maxLength={1}
                value={digit}
                onChange={(e) => onChangeDigit(idx, e.target.value)}
                onKeyDown={(e) => onKeyDownDigit(idx, e)}
                ref={(el) => (inputsRef.current[idx] = el)}
                aria-invalid={invalidCode}
              />
            ))}
            </div>

            <p className="resend-link">
              Don't get the code?{' '}
              <a
                href="#"
                onClick={(e) => {
                  e.preventDefault();
                  if (!isVerifying && cooldown === 0) resendCode();
                }}
                aria-disabled={isVerifying || cooldown > 0}
                style={{ pointerEvents: isVerifying || cooldown > 0 ? 'none' : 'auto', opacity: isVerifying || cooldown > 0 ? 0.6 : 1 }}
              >
                {cooldown > 0 ? `Resend in ${cooldown}s` : (isVerifying ? 'Sending...' : 'Click to resend')}
              </a>
            </p>

            <button className="verify-btn" onClick={verifyCode} disabled={isVerifying}>
              {isVerifying ? 'Verifying…' : 'Verify'}
            </button>
            {feedback && <p className={`feedback ${invalidCode ? 'error' : 'info'}`}>{feedback}</p>}
          </div>
        </div>
      </div>

      <Footer />
    </div>
  );
}
