import React, { useEffect, useRef, useState } from 'react';
import '../../styles/StoreModals.css';
import storeIcon from '../../assets/icon_store.png';

export default function JoinStore({ onJoin, onDismiss, actionStatus }) {
  const inputsRef = useRef([]);
  const [code, setCode] = useState(['', '', '', '', '', '']);
  const [invalid, setInvalid] = useState(false);

  useEffect(() => {
    inputsRef.current[0]?.focus();
  }, []);

  const onChangeDigit = (idx, value) => {
    const v = value.replace(/\D/g, '').slice(0, 1);
    const next = [...code];
    next[idx] = v;
    setCode(next);
    if (invalid) setInvalid(false);
    if (v && idx < inputsRef.current.length - 1) inputsRef.current[idx + 1]?.focus();
  };

  const onKeyDownDigit = (idx, e) => {
    if (e.key === 'Backspace' && !code[idx] && idx > 0) inputsRef.current[idx - 1]?.focus();
  };

  const submit = (ev) => {
    ev.preventDefault();
    const joined = code.join('');
    if (joined.length !== 6) {
      setInvalid(true);
      return;
    }
    onJoin?.(joined);
  };

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true">
      <div className="modal-card">
        <div className="modal-header-strip">
          <h2>Join Store</h2>
          {onDismiss && (
            <button className="close-btn" onClick={onDismiss} aria-label="Close"/>
          )}
        </div>

        <div className="modal-icon">
          <img src={storeIcon} alt="Store" />
        </div>

        <p className="join-instruction">Enter Store Code</p>

        <form onSubmit={submit} className="modal-form" noValidate>
          <div className="code-inputs">
            {code.map((digit, idx) => (
              <input
                key={idx}
                type="text"
                maxLength={1}
                className={`code-input ${invalid ? 'input-error' : ''}`}
                value={digit}
                onChange={(e) => onChangeDigit(idx, e.target.value)}
                onKeyDown={(e) => onKeyDownDigit(idx, e)}
                ref={(el) => (inputsRef.current[idx] = el)}
                aria-invalid={invalid}
              />
            ))}
          </div>
          {invalid && <p className="error-text">Please enter the 6-digit code.</p>}
          {actionStatus?.text && (
            <p className={`status-text ${actionStatus.kind || 'info'}`}>{actionStatus.text}</p>
          )}

          <div className="modal-actions">
            <button type="submit" className="btn primary">Request to Join</button>
          </div>
        </form>
      </div>
    </div>
  );
}