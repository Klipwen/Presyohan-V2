import React, { useEffect, useRef, useState } from 'react';
import '../../styles/StoreModals.css';

export default function EditCategoryModal({ open, onClose, category, onSave }) {
  const [name, setName] = useState('');
  const [touched, setTouched] = useState(false);
  const [error, setError] = useState('');
  const firstFieldRef = useRef(null);

  useEffect(() => {
    if (open && category) {
      setName(category.name || '');
      setTouched(false);
      setError('');
      firstFieldRef.current?.focus();
    }
  }, [open, category]);

  const validate = (v) => {
    const trimmed = (v || '').trim();
    if (!trimmed) return 'Category name is required';
    if (trimmed.length < 2) return 'Category name must be at least 2 characters';
    return '';
  };

  const submit = (ev) => {
    ev.preventDefault();
    setTouched(true);
    const e = validate(name);
    setError(e);
    if (e) return;
    onSave?.(name.trim());
    onClose?.();
  };

  if (!open || !category) return null;

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" onClick={(e) => { if (e.target === e.currentTarget) onClose?.(); }}>
      <div className="modal-card" style={{ maxWidth: 460 }}>
        <div className="modal-header-strip">
          <h2>Edit Category</h2>
          {onClose && (<button className="close-btn" onClick={onClose} aria-label="Close" />)}
        </div>

        <form onSubmit={submit} className="modal-form" noValidate>
          <label className="field-label">Category name</label>
          <input
            ref={firstFieldRef}
            type="text"
            value={name}
            onChange={(e) => { setName(e.target.value); if (touched) setError(validate(e.target.value)); }}
            onBlur={() => { setTouched(true); setError(validate(name)); }}
            className={touched ? (error ? 'input-error' : 'input-valid') : ''}
            aria-invalid={!!(touched && error)}
          />
          {touched && error && <p className="error-text">{error}</p>}

          <div className="modal-actions" style={{ justifyContent: 'space-between' }}>
            <button type="button" className="btn teal" onClick={onClose}>Cancel</button>
            <button type="submit" className="btn orange">Save</button>
          </div>
        </form>
      </div>
    </div>
  );
}