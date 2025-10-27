import React, { useState } from 'react';
import Header from '../components/layout/Header';
import Footer from '../components/layout/Footer';
import '../styles/LoginSignUpPage.css';

export default function LoginSignupPage() {
  const [activeTab, setActiveTab] = useState('login');
  const [loginPasswordVisible, setLoginPasswordVisible] = useState(false);
  const [signupPasswordVisible, setSignupPasswordVisible] = useState(false);
  const [signupConfirmVisible, setSignupConfirmVisible] = useState(false);

  const toggleTab = (tab) => setActiveTab(tab);

  const handleLogin = (e) => {
    e.preventDefault();
    const email = e.target.loginEmail.value.trim();
    const password = e.target.loginPassword.value;
    if (!email || !password) {
      alert('Please enter your email and password.');
      return;
    }
    alert(`Login placeholder with email: ${email}`);
  };

  const handleSignup = (e) => {
    e.preventDefault();
    const name = e.target.signupName.value.trim();
    const email = e.target.signupEmail.value.trim();
    const password = e.target.signupPassword.value;
    const confirmPassword = e.target.signupConfirmPassword.value;
    if (!name || !email || !password || !confirmPassword) {
      alert('Please fill out all fields.');
      return;
    }
    if (password !== confirmPassword) {
      alert('Passwords do not match! Please make sure both passwords are the same.');
      return;
    }
    alert(`Signup placeholder for ${name} with email: ${email}`);
  };

  return (
    <div className="page-root login-page-root">
      <Header theme='white' />

      <div className="wrapper">
        <div className="left-section">
          <div className="brand">
            <div className="logo-container">
              <img src="/ic_launcher.png" alt="Presyohan Logo" />
            </div>
            <h1>
              <span className="presyo">presyo</span><span className="han">han?</span>
            </h1>
            <p>Find the best prices in one place!</p>
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
                <form onSubmit={handleLogin}>
                  <input name="loginEmail" id="loginEmail" type="email" placeholder="Email" required />
                  <div className="password-field">
                    <input
                      name="loginPassword"
                      id="loginPassword"
                      type={loginPasswordVisible ? 'text' : 'password'}
                      placeholder="Password"
                      required
                    />
                    <button type="button" className="toggle-password" onClick={() => setLoginPasswordVisible(v => !v)}>
                      {loginPasswordVisible ? '🙈' : '👁️'}
                    </button>
                  </div>
                  <div className="forgot-password">
                    <a href="#">Forgot Password?</a>
                  </div>
                  <button type="submit" className="btn">Login</button>
                  <div className="divider">or sign in with</div>
                  <div className="social-login">
                    <button type="button" className="social-btn" onClick={() => alert('Google login would be implemented here')}>
                      <svg viewBox="0 0 24 24">
                        <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
                        <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
                        <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
                        <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
                      </svg>
                    </button>
                    <button type="button" className="social-btn" onClick={() => alert('Facebook login would be implemented here')}>
                      <svg viewBox="0 0 24 24">
                        <path fill="#1877F2" d="M24 12.073c0-6.627-5.373-12-12-12s-12 5.373-12 12c0 5.99 4.388 10.954 10.125 11.854v-8.385H7.078v-3.47h3.047V9.43c0-3.007 1.792-4.669 4.533-4.669 1.312 0 2.686.235 2.686.235v2.953H15.83c-1.491 0-1.956.925-1.956 1.874v2.25h3.328l-.532 3.47h-2.796v8.385C19.612 23.027 24 18.062 24 12.073z"/>
                      </svg>
                    </button>
                    <button type="button" className="social-btn" onClick={() => alert('Microsoft login would be implemented here')}>
                      <svg viewBox="0 0 24 24">
                        <path fill="#f25022" d="M1 1h10v10H1z"/>
                        <path fill="#00a4ef" d="M13 1h10v10H13z"/>
                        <path fill="#7fba00" d="M1 13h10v10H1z"/>
                        <path fill="#ffb900" d="M13 13h10v10H13z"/>
                      </svg>
                    </button>
                  </div>
                  <div className="footer-text">
                    Don't have an account? <a href="#" onClick={(e) => { e.preventDefault(); toggleTab('signup'); }}>Sign Up</a>
                  </div>
                </form>
              </div>
            )}

            {activeTab === 'signup' && (
              <div id="signupForm" className="form-container active">
                <form onSubmit={handleSignup}>
                  <input name="signupName" id="signupName" type="text" placeholder="Name" required />
                  <input name="signupEmail" id="signupEmail" type="email" placeholder="Email" required />
                  <div className="password-field">
                    <input
                      name="signupPassword"
                      id="signupPassword"
                      type={signupPasswordVisible ? 'text' : 'password'}
                      placeholder="Password"
                      required
                    />
                    <button type="button" className="toggle-password" onClick={() => setSignupPasswordVisible(v => !v)}>
                      {signupPasswordVisible ? '🙈' : '👁️'}
                    </button>
                  </div>
                  <div className="password-field">
                    <input
                      name="signupConfirmPassword"
                      id="signupConfirmPassword"
                      type={signupConfirmVisible ? 'text' : 'password'}
                      placeholder="Confirm Password"
                      required
                    />
                    <button type="button" className="toggle-password" onClick={() => setSignupConfirmVisible(v => !v)}>
                      {signupConfirmVisible ? '🙈' : '👁️'}
                    </button>
                  </div>
                  <div className="footer-text">
                    Already have an account? <a href="#" onClick={(e) => { e.preventDefault(); toggleTab('login'); }}>Login</a>
                  </div>
                  <button type="submit" className="btn">Sign Up</button>
                </form>
              </div>
            )}
          </div>
        </div>
      </div>

      <Footer />
    </div>
  );
}