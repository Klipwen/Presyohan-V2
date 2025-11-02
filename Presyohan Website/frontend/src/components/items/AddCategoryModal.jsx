import React, { useEffect, useRef, useState } from 'react';
import '../../styles/StoreModals.css';

export default function AddCategoryModal({ open, onClose, onAdd }) {
  const [name, setName] = useState('');
  const [touched, setTouched] = useState(false);
  const [error, setError] = useState('');
  const inputRef = useRef(null);

  useEffect(() => {
    if (open) {
      setName('');
      setTouched(false);
      setError('');
      inputRef.current?.focus();
    }
  }, [open]);

  const validate = (v) => {
    if (!v.trim()) return 'Category name is required.';
    if (v.trim().length < 2) return 'Category name is too short.';
    return '';
  };

  const submit = (ev) => {
    ev.preventDefault();
    const err = validate(name);
    setTouched(true);
    setError(err);
    if (err) return;
    onAdd?.(name.trim());
    onClose?.();
  };

  if (!open) return null;

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" onClick={(e) => { if (e.target === e.currentTarget) onClose?.(); }}>
      <div className="modal-card" style={{ maxWidth: 420 }}>
        <div className="modal-header-strip">
          <h2>New Category</h2>
          {onClose && (<button className="close-btn" onClick={onClose} aria-label="Close" />)}
        </div>
        <form onSubmit={submit} className="modal-form" noValidate>
          <label className="field-label">Category name</label>
          <input
            ref={inputRef}
            type="text"
            value={name}
            onChange={(e) => { setName(e.target.value); if (touched) setError(validate(e.target.value)); }}
            onBlur={() => { setTouched(true); setError(validate(name)); }}
            className={touched ? (error ? 'input-error' : 'input-valid') : ''}
            aria-invalid={!!(touched && error)}
          />
          {touched && error && <p className="error-text">{error}</p>}

          <div className="modal-actions" style={{ justifyContent: 'space-between' }}>
            <button type="button" className="btn teal" onClick={onClose}>Back</button>
            <button type="submit" className="btn orange">Add</button>
          </div>
        </form>
      </div>
    </div>
  );
}