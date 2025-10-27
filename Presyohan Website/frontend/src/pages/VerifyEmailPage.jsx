import React, { useEffect, useRef, useState } from 'react';
import Header from '../components/layout/Header';
import Footer from '../components/layout/Footer';
import '../styles/VerifyEmailPage.css';

export default function VerifyEmailPage() {
  const inputsRef = useRef([]);
  const [code, setCode] = useState(['', '', '', '', '', '']);
  const [feedback, setFeedback] = useState('');
  const [cooldown, setCooldown] = useState(0);

  useEffect(() => {
    inputsRef.current[0]?.focus();
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
    if (v && idx < inputsRef.current.length - 1) {
      inputsRef.current[idx + 1]?.focus();
    }
  };

  const onKeyDownDigit = (idx, e) => {
    if (e.key === 'Backspace' && !code[idx] && idx > 0) {
      inputsRef.current[idx - 1]?.focus();
    }
  };

  const verifyCode = (e) => {
    e.preventDefault();
    const joined = code.join('');
    if (joined.length !== 6) {
      setFeedback('Please enter the 6-digit code.');
      return;
    }
    // Placeholder: perform verification via API
    setFeedback('Code submitted. (Hook up API to verify)');
  };

  const resendCode = () => {
    if (cooldown > 0) return;
    // Placeholder: call API to resend code
    setCooldown(30);
    setFeedback('A new code was sent to your email.');
  };

  return (
    <div className="page-root verify-page-root">
      <Header />

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
          <div className="verify-container">
            <div className="verify-icon">
              <svg viewBox="0 0 24 24" fill="none">
                <circle cx="12" cy="12" r="10" stroke="#ff8c00" strokeWidth="2" />
                <path d="M12 6v6l4 2" stroke="#ff8c00" strokeWidth="2" strokeLinecap="round" />
              </svg>
            </div>

            <h2>Verify your email</h2>
            <p className="verify-message">
              We've sent a 6 digit code to <span className="email">callejhuan@gmail.com</span>
            </p>

            <div className="code-inputs">
              {code.map((digit, idx) => (
                <input
                  key={idx}
                  type="text"
                  className="code-input"
                  maxLength={1}
                  value={digit}
                  onChange={(e) => onChangeDigit(idx, e.target.value)}
                  onKeyDown={(e) => onKeyDownDigit(idx, e)}
                  ref={(el) => (inputsRef.current[idx] = el)}
                />
              ))}
            </div>

            <p className="resend-link">
              Don't get the code?{' '}
              <a
                href="#"
                onClick={(e) => {
                  e.preventDefault();
                  resendCode();
                }}
              >
                {cooldown > 0 ? `Resend in ${cooldown}s` : 'Click to resend'}
              </a>
            </p>

            <button className="verify-btn" onClick={verifyCode}>Verify</button>
            {feedback && <p className="feedback">{feedback}</p>}
          </div>
        </div>
      </div>

      <Footer />
    </div>
  );
}