import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { supabase } from '../config/supabaseClient';
import presyohanLogo from '../assets/presyohan_logo.png';
import '../styles/AdminGatekeeper.css';

export default function AdminGatekeeper() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [passcode, setPasscode] = useState('');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const navigate = useNavigate();

  // If already logged in and passcode already verified, go straight to dashboard
  useEffect(() => {
    const checkSession = async () => {
      const isVerified = sessionStorage.getItem('admin_passcode_passed') === 'true';
      if (isVerified) {
        const { data: { session } } = await supabase.auth.getSession();
        if (session) {
          let { data: profile } = await supabase
            .from('app_users')
            .select('role')
            .eq('id', session.user.id)
            .maybeSingle();

          if (!profile) {
            try {
              const { data: fallbackProfile } = await supabase
                .from('app_users')
                .select('role')
                .eq('auth_uid', session.user.id)
                .maybeSingle();
              if (fallbackProfile) {
                profile = fallbackProfile;
              }
            } catch (_) {}
          }

          if (profile?.role === 'admin') {
            navigate('/admin/dashboard', { replace: true });
          }
        }
      }
    };
    checkSession();
  }, [navigate]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setIsSubmitting(true);

    const configuredPasscode = import.meta.env.VITE_ADMIN_GATEKEEPER_PASSCODE || 'iwanttoopen';

    // 1. Check security passcode
    if (passcode !== configuredPasscode) {
      setError('Incorrect security passcode.');
      setIsSubmitting(false);
      return;
    }

    // 2. Perform Supabase Login
    const { data, error: loginErr } = await supabase.auth.signInWithPassword({
      email: email.trim(),
      password: password
    });

    if (loginErr) {
      setError(loginErr.message || 'Login failed. Please check your credentials.');
      setIsSubmitting(false);
      return;
    }

    if (data?.session) {
      // 3. Check role
      let { data: profile, error: roleErr } = await supabase
        .from('app_users')
        .select('role')
        .eq('id', data.session.user.id)
        .maybeSingle();

      if (!profile && !roleErr) {
        try {
          const { data: fallbackProfile, error: fallbackErr } = await supabase
            .from('app_users')
            .select('role')
            .eq('auth_uid', data.session.user.id)
            .maybeSingle();
          if (fallbackProfile) {
            profile = fallbackProfile;
          }
          if (fallbackErr) {
            roleErr = fallbackErr;
          }
        } catch (_) {}
      }

      if (!roleErr && profile?.role === 'admin') {
        sessionStorage.setItem('admin_passcode_passed', 'true');
        navigate('/admin/dashboard', { replace: true });
      } else {
        setError('Access Denied: You do not have administrator permissions.');
        await supabase.auth.signOut();
        setIsSubmitting(false);
      }
    } else {
      setError('Unexpected error during session establishment.');
      setIsSubmitting(false);
    }
  };

  return (
    <div className="gatekeeper-root">
      <div className="gatekeeper-card">
        <div className="gatekeeper-logo-container">
          <img src={presyohanLogo} alt="Presyohan Logo" />
        </div>
        <h1>
          <span className="presyo">presyo</span><span className="han">han?</span>
        </h1>
        <h2>Admin Portal Login</h2>
        <p>Log in with your administrator credentials and security passcode.</p>
        
        <form onSubmit={handleSubmit} className="gatekeeper-form">
          <div className="gatekeeper-input-group">
            <input
              type="email"
              placeholder="Admin Email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="gatekeeper-input"
              disabled={isSubmitting}
              required
            />
          </div>

          <div className="gatekeeper-input-group">
            <input
              type="password"
              placeholder="Account Password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="gatekeeper-input"
              disabled={isSubmitting}
              required
            />
          </div>

          <div className="gatekeeper-input-group">
            <input
              type="password"
              placeholder="Security Passcode"
              value={passcode}
              onChange={(e) => setPasscode(e.target.value)}
              className="gatekeeper-input"
              disabled={isSubmitting}
              required
            />
          </div>

          <button 
            type="submit" 
            className="gatekeeper-btn"
            disabled={isSubmitting}
          >
            {isSubmitting ? 'Logging in...' : 'Sign In as Admin'}
          </button>
        </form>

        {error && <div className="gatekeeper-error">{error}</div>}
      </div>
    </div>
  );
}
