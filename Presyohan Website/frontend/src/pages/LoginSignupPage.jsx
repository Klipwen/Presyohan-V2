import React, { useState, useEffect } from 'react';
import AuthHeader from '../components/layout/AuthHeader';
import '../styles/LoginSignUpPage.css';
import presyohanLogo from '../assets/presyohan_logo.png';
import { supabase } from '../config/supabaseClient';
import { useNavigate, useLocation } from 'react-router-dom';

export default function LoginSignupPage() {
  const [activeTab, setActiveTab] = useState('login');
  const [loginPasswordVisible, setLoginPasswordVisible] = useState(false);
  const [signupPasswordVisible, setSignupPasswordVisible] = useState(false);
  const [signupConfirmVisible, setSignupConfirmVisible] = useState(false);
  // Controlled inputs + validation state
  const [loginEmail, setLoginEmail] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [signupName, setSignupName] = useState('');
  const [signupEmail, setSignupEmail] = useState('');
  const [signupPassword, setSignupPassword] = useState('');
  const [signupConfirmPassword, setSignupConfirmPassword] = useState('');

  const [loginErrors, setLoginErrors] = useState({});
  const [signupErrors, setSignupErrors] = useState({});
  const [signupEmailExists, setSignupEmailExists] = useState(false);
  const [loginTouched, setLoginTouched] = useState({});
  const [signupTouched, setSignupTouched] = useState({});
  const [loginFormError, setLoginFormError] = useState('');
  const [signupFormError, setSignupFormError] = useState('');
  const [isSubmittingFacebook, setIsSubmittingFacebook] = useState(false);
  const [isSubmittingLogin, setIsSubmittingLogin] = useState(false);
  const [isSubmittingSignup, setIsSubmittingSignup] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();

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

  // Ensure the page starts at the very top when navigated to
  useEffect(() => {
    window.scrollTo({ top: 0, left: 0, behavior: 'auto' });
  }, []);

  useEffect(() => {
    if (location.state?.oauthError) {
      setActiveTab('login');
      setLoginFormError(location.state.oauthError);
      navigate(location.pathname, { replace: true, state: {} });
    }
  }, [location, navigate]);

  const toggleTab = (tab) => setActiveTab(tab);

  const validateEmail = (value) => {
    const re = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return re.test(String(value).toLowerCase());
  };

  const computeLoginErrors = () => {
    const errs = {};
    if (!loginEmail) errs.email = 'Email is required.';
    else if (!validateEmail(loginEmail)) errs.email = 'Enter a valid email address.';
    if (!loginPassword) errs.password = 'Password is required.';
    else if (loginPassword.length < 8) errs.password = 'Password must be at least 8 characters.';
    return errs;
  };

  const computeSignupErrors = () => {
    const errs = {};
    if (!signupName) errs.name = 'Name is required.';
    else if (signupName.trim().length < 2) errs.name = 'Name must be at least 2 characters.';
    if (!signupEmail) errs.email = 'Email is required.';
    else if (!validateEmail(signupEmail)) errs.email = 'Enter a valid email address.';
    else if (signupEmailExists) errs.email = 'An account already exists for this email. Please log in.';
    if (!signupPassword) errs.password = 'Password is required.';
    else if (signupPassword.length < 8) errs.password = 'Password must be at least 8 characters.';
    if (!signupConfirmPassword) errs.confirm = 'Please confirm your password.';
    else if (signupConfirmPassword !== signupPassword) errs.confirm = 'Passwords do not match.';
    return errs;
  };

  const handleLogin = async (e) => {
    e.preventDefault();
    setLoginTouched({ email: true, password: true });
    const errs = computeLoginErrors();
    setLoginErrors(errs);
    setLoginFormError('');
    if (Object.keys(errs).length > 0) return;
    const email = loginEmail.trim();
    const password = loginPassword;
    setIsSubmittingLogin(true);
    const { data, error } = await supabase.auth.signInWithPassword({
      email,
      password
    });
    if (error) {
      setLoginFormError(error.message || 'Login failed. Please try again.');
      setIsSubmittingLogin(false);
      return;
    }
    if (data?.session) {
      navigate('/stores', { replace: true });
    }
    setIsSubmittingLogin(false);
  };

  const handleSignup = async (e) => {
    e.preventDefault();
    setSignupTouched({ name: true, email: true, password: true, confirm: true });
    const errs = computeSignupErrors();
    setSignupErrors(errs);
    setSignupFormError('');
    if (Object.keys(errs).length > 0) return;
    const name = signupName.trim();
    const email = signupEmail.trim();
    const password = signupPassword;
    try {
      // Request an email OTP and create the user if it doesn't exist. This
      // ensures the template with {{ .Token }} is used and the user receives
      // a numeric code to paste into the Verify page.
      setIsSubmittingSignup(true);
      const { error } = await supabase.auth.signInWithOtp({
        email,
        options: { 
          shouldCreateUser: true,
          // Ensure magic link redirects to deployed site, not localhost
          emailRedirectTo: `${getAppOrigin()}/auth/callback`
        }
      });
      if (error) {
        setSignupFormError(error.message || 'Signup failed. Please try again.');
        setIsSubmittingSignup(false);
        return;
      }

      // Save the pending name so we can set it once the user is verified.
      localStorage.setItem('pendingEmail', email);
      localStorage.setItem('pendingName', name);
      localStorage.setItem('pendingPassword', password);
      // We request OTP via signInWithOtp, so verification type should be 'email'.
      localStorage.setItem('pendingVerificationType', 'email');
      navigate(`/verify-email?email=${encodeURIComponent(email)}&flow=email`, { replace: true });
    } catch (err) {
      setSignupFormError(err.message || 'Unexpected error during signup.');
    } finally {
      setIsSubmittingSignup(false);
    }
  };

  const signInWithGoogle = async () => {
    try {
      const redirectTo = `${getAppOrigin()}/auth/callback`;
      const { error } = await supabase.auth.signInWithOAuth({
        provider: 'google',
        options: {
          redirectTo,
          queryParams: { prompt: 'select_account' }
        }
      });
      if (error) {
        setLoginFormError(error.message || 'Google sign-in failed.');
      }
      // on success Supabase will redirect to the callback
    } catch (err) {
      setLoginFormError(err.message || 'Unexpected error during Google sign-in.');
    }
  };

  const signInWithFacebook = async () => {
    try {
      setLoginFormError('');
      setIsSubmittingFacebook(true);
      const redirectTo = `${getAppOrigin()}/auth/callback`;
      const { error } = await supabase.auth.signInWithOAuth({
        provider: 'facebook',
        options: {
          redirectTo,
          scopes: 'email,public_profile',
          queryParams: {
            display: 'popup',
            auth_type: 'rerequest'
          }
        }
      });
      if (error) {
        throw error;
      }
    } catch (err) {
      setLoginFormError(err.message || 'Unexpected error during Facebook sign-in.');
      setIsSubmittingFacebook(false);
    }
  };

  return (
      <div className="page-root login-page-root">
        <AuthHeader theme='white' />

        <div className="wrapper">
          <div className="left-section">
          <div className="brand">
              <img src={presyohanLogo} alt="Presyohan Logo" />
            <h1>
              <p>Atong</p>
              <span className="presyo">presyo</span><span className="han">han?</span>
            </h1>
          </div>
        </div>

        <div className="right-section">
          <div className="auth-container">
            <div className="tabs">
              <button className={`tab ${activeTab === 'login' ? 'active' : ''}`} onClick={() => toggleTab('login')}>Login</button>
              <button className={`tab ${activeTab === 'signup' ? 'active' : ''}`} onClick={() => toggleTab('signup')}>Sign Up</button>
            </div>

            {activeTab === 'login' && (
              <div id="loginForm" className="form-container active">
                <form onSubmit={handleLogin} noValidate>
                  <input
                    name="loginEmail"
                    id="loginEmail"
                    type="email"
                    placeholder="Email"
                    value={loginEmail}
                    onChange={(e) => {
                      setLoginEmail(e.target.value);
                      if (loginTouched.email) setLoginErrors(computeLoginErrors());
                    }}
                    onBlur={() => setLoginTouched((t) => ({ ...t, email: true }))}
                    className={
                      loginTouched.email ? (loginErrors.email ? 'input-error' : 'input-valid') : ''
                    }
                    aria-invalid={!!(loginTouched.email && loginErrors.email)}
                    required
                  />
                  {loginTouched.email && loginErrors.email && (
                    <p className="error-text">{loginErrors.email}</p>
                  )}
                  <div className="password-field">
                    <input
                      name="loginPassword"
                      id="loginPassword"
                      type={loginPasswordVisible ? 'text' : 'password'}
                      placeholder="Password"
                      value={loginPassword}
                      onChange={(e) => {
                        setLoginPassword(e.target.value);
                        if (loginTouched.password) setLoginErrors(computeLoginErrors());
                      }}
                      onBlur={() => setLoginTouched((t) => ({ ...t, password: true }))}
                      className={
                        loginTouched.password ? (loginErrors.password ? 'input-error' : 'input-valid') : ''
                      }
                      aria-invalid={!!(loginTouched.password && loginErrors.password)}
                      required
                    />
                    <button type="button" className="toggle-password" onClick={() => setLoginPasswordVisible(v => !v)}>
                      {loginPasswordVisible ? '' : ''}
                    </button>
                  </div>
                  {loginTouched.password && loginErrors.password && (
                    <p className="error-text">{loginErrors.password}</p>
                  )}
                  <button type="submit" className="btn" disabled={isSubmittingLogin}>
                    {isSubmittingLogin ? 'Logging in…' : 'Login'}
                  </button>
                  <div className="divider">or sign in with</div>
                  <div className="social-login">
                    <button type="button" className="social-btn" onClick={signInWithGoogle}>
                      <svg viewBox="0 0 24 24">
                        <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
                        <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98 .66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
                        <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43 .35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22 .81-.62z"/>
                        <path fill="#EA4335" d="M12 5.38c1.62 0 3.06 .56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
                      </svg>
                    </button>
                    <button
                      type="button"
                      className="social-btn"
                      onClick={signInWithFacebook}
                      disabled={isSubmittingFacebook}
                      aria-busy={isSubmittingFacebook}
                      aria-label={isSubmittingFacebook ? 'Connecting to Facebook' : 'Sign in with Facebook'}
                      title="Sign in with Facebook"
                    >
                      <svg viewBox="0 0 24 24">
                        <path fill="#1877F2" d="M24 12.073c0-6.627-5.373-12-12-12s-12 5.373-12 12c0 5.99 4.388 10.954 10.125 11.854v-8.385H7.078v-3.47h3.047V9.43c0-3.007 1.792-4.669 4.533-4.669 1.312 0 2.686 .235 2.686 .235v2.953H15.83c-1.491 0-1.956 .925-1.956 1.874v2.25h3.328l-.532 3.47h-2.796v8.385C19.612 23.027 24 18.062 24 12.073z"/>
                      </svg>
                    </button>
                    <button type="button" className="social-btn" onClick={() => setLoginFormError('Microsoft login is not yet implemented.')}
                    >
                      <svg viewBox="0 0 24 24">
                        <path fill="#f25022" d="M1 1h10v10H1z"/>
                        <path fill="#00a4ef" d="M13 1h10v10H13z"/>
                        <path fill="#7fba00" d="M1 13h10v10H1z"/>
                        <path fill="#ffb900" d="M13 13h10v10H13z"/>
                      </svg>
                    </button>
                  </div>
                  {loginFormError && <div className="form-error">{loginFormError}</div>}
                  <div className="footer-text">
                    Don't have an account? <a href="#" onClick={(e) => { e.preventDefault(); toggleTab('signup'); }}>Sign Up</a>
                  </div>
                </form>
              </div>
            )}

            {activeTab === 'signup' && (
              <div id="signupForm" className="form-container active">
                <form onSubmit={handleSignup} noValidate>
                  <input
                    name="signupName"
                    id="signupName"
                    type="text"
                    placeholder="Name"
                    value={signupName}
                    onChange={(e) => {
                      setSignupName(e.target.value);
                      if (signupTouched.name) setSignupErrors(computeSignupErrors());
                    }}
                    onBlur={() => setSignupTouched((t) => ({ ...t, name: true }))}
                    className={signupTouched.name ? (signupErrors.name ? 'input-error' : 'input-valid') : ''}
                    aria-invalid={!!(signupTouched.name && signupErrors.name)}
                    required
                  />
                  {signupTouched.name && signupErrors.name && (
                    <p className="error-text">{signupErrors.name}</p>
                  )}
                  <input
                    name="signupEmail"
                    id="signupEmail"
                    type="email"
                    placeholder="Email"
                    value={signupEmail}
                    onChange={(e) => {
                      setSignupEmail(e.target.value);
                      if (signupTouched.email) setSignupErrors(computeSignupErrors());
                    }}
                    onBlur={() => setSignupTouched((t) => ({ ...t, email: true }))}
                    className={signupTouched.email ? (signupErrors.email ? 'input-error' : 'input-valid') : ''}
                    aria-invalid={!!(signupTouched.email && signupErrors.email)}
                    required
                  />
                  {signupTouched.email && signupErrors.email && (
                    <p className="error-text">{signupErrors.email}</p>
                  )}
                  <div className="password-field">
                    <input
                      name="signupPassword"
                      id="signupPassword"
                      type={signupPasswordVisible ? 'text' : 'password'}
                      placeholder="Password"
                      value={signupPassword}
                      onChange={(e) => {
                        setSignupPassword(e.target.value);
                        if (signupTouched.password) setSignupErrors(computeSignupErrors());
                      }}
                      onBlur={() => setSignupTouched((t) => ({ ...t, password: true }))}
                      className={signupTouched.password ? (signupErrors.password ? 'input-error' : 'input-valid') : ''}
                      aria-invalid={!!(signupTouched.password && signupErrors.password)}
                      required
                    />
                    <button type="button" className="toggle-password" onClick={() => setSignupPasswordVisible(v => !v)}>
                      {signupPasswordVisible ? '' : ''}
                    </button>
                  </div>
                  {signupTouched.password && signupErrors.password && (
                    <p className="error-text">{signupErrors.password}</p>
                  )}
                  <div className="password-field">
                    <input
                      name="signupConfirmPassword"
                      id="signupConfirmPassword"
                      type={signupConfirmVisible ? 'text' : 'password'}
                      placeholder="Confirm Password"
                      value={signupConfirmPassword}
                      onChange={(e) => {
                        setSignupConfirmPassword(e.target.value);
                        if (signupTouched.confirm) setSignupErrors(computeSignupErrors());
                      }}
                      onBlur={() => setSignupTouched((t) => ({ ...t, confirm: true }))}
                      className={signupTouched.confirm ? (signupErrors.confirm ? 'input-error' : 'input-valid') : ''}
                      aria-invalid={!!(signupTouched.confirm && signupErrors.confirm)}
                      required
                    />
                    <button type="button" className="toggle-password" onClick={() => setSignupConfirmVisible(v => !v)}>
                      {signupConfirmVisible ? '' : ''}
                    </button>
                  </div>
                  {signupTouched.confirm && signupErrors.confirm && (
                    <p className="error-text">{signupErrors.confirm}</p>
                  )}
                  <p className="helper-text">We’ll send a 6-digit code to confirm your account.</p>
                  {signupFormError && <div className="form-error">{signupFormError}</div>}
                  <div className="footer-text">
                    Already have an account? <a href="#" onClick={(e) => { e.preventDefault(); toggleTab('login'); }}>Login</a>
                  </div>
                  <button type="submit" className="btn" disabled={isSubmittingSignup}>
                    {isSubmittingSignup ? 'Sending code…' : 'Sign Up'}
                  </button>
                </form>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}