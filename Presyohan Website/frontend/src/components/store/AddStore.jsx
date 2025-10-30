import React, { useEffect, useRef, useState } from 'react';
import '../../styles/StoreModals.css';
import storeIcon from '../../assets/icon_store.png';

export default function AddStore({ onCreate, onDismiss }) {
  const [name, setName] = useState('');
  const [branch, setBranch] = useState('');
  const [type, setType] = useState('');
  const [otherType, setOtherType] = useState('');
  const [touched, setTouched] = useState({});
  const [errors, setErrors] = useState({});
  const nameRef = useRef(null);

  useEffect(() => {
    nameRef.current?.focus();
  }, []);

  const computeErrors = () => {
    const e = {};
    if (!name.trim()) e.name = 'Store name is required.';
    if (!branch.trim()) e.branch = 'Store branch is required.';
    if (!type) e.type = 'Select a store type.';
    if (type === 'other' && !otherType.trim()) e.otherType = 'Please specify the store type.';
    return e;
  };

  // Recompute errors using prospective values to avoid stale errors on select->other flow
  const computeErrorsWith = ({ nameV = name, branchV = branch, typeV = type, otherTypeV = otherType } = {}) => {
    const e = {};
    if (!nameV.trim()) e.name = 'Store name is required.';
    if (!branchV.trim()) e.branch = 'Store branch is required.';
    if (!typeV) e.type = 'Select a store type.';
    if (typeV === 'other' && !otherTypeV.trim()) e.otherType = 'Please specify the store type.';
    return e;
  };

  const submit = (ev) => {
    ev.preventDefault();
    const allTouched = { name: true, branch: true, type: true, otherType: true };
    setTouched(allTouched);
    const e = computeErrors();
    setErrors(e);
    if (Object.keys(e).length > 0) return;
    const payload = {
      name: name.trim(),
      branch: branch.trim(),
      type: type === 'other' ? otherType.trim() : type
    };
    onCreate?.(payload);
  };

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true">
      <div className="modal-card">
        <div className="modal-header-strip">
          <h2>Create Store</h2>
          {onDismiss && (
            <button className="close-btn" onClick={onDismiss} aria-label="Close"/>
          )}
        </div>

        <div className="modal-icon">
          <img src={storeIcon} alt="Store" />
        </div>

        <form onSubmit={submit} className="modal-form" noValidate>
          <label className="field-label">Store name</label>
          <input
            ref={nameRef}
            type="text"
            placeholder=""
            value={name}
            onChange={(e) => { setName(e.target.value); if (touched.name) setErrors(computeErrors()); }}
            onBlur={() => setTouched((t) => ({ ...t, name: true }))}
            className={touched.name ? (errors.name ? 'input-error' : 'input-valid') : ''}
            aria-invalid={!!(touched.name && errors.name)}
          />
          {touched.name && errors.name && <p className="error-text">{errors.name}</p>}

          <label className="field-label">Store Branch</label>
          <input
            type="text"
            placeholder=""
            value={branch}
            onChange={(e) => { setBranch(e.target.value); if (touched.branch) setErrors(computeErrors()); }}
            onBlur={() => setTouched((t) => ({ ...t, branch: true }))}
            className={touched.branch ? (errors.branch ? 'input-error' : 'input-valid') : ''}
            aria-invalid={!!(touched.branch && errors.branch)}
          />
          {touched.branch && errors.branch && <p className="error-text">{errors.branch}</p>}

          <label className="field-label">Store Type</label>
          <div className="select-row">
            <select
              value={type}
              onChange={(e) => {
                const next = e.target.value;
                setType(next);
                // Recompute immediately with the next value to clear any stale select error
                setErrors(computeErrorsWith({ typeV: next }));
              }}
              onBlur={() => setTouched((t) => ({ ...t, type: true }))}
              className={touched.type ? (errors.type ? 'input-error' : 'input-valid') : ''}
              aria-invalid={!!(touched.type && errors.type)}
            >
              <option value="">--Select store type--</option>
              <option value="Grocery">Grocery</option>
              <option value="Pharmacy">Pharmacy</option>
              <option value="Electronics">Electronics</option>
              <option value="other">Other (specify)</option>
            </select>
            <span className="chevron" aria-hidden="true">▾</span>
          </div>
          {touched.type && errors.type && type !== 'other' && (
            <p className="error-text">{errors.type}</p>
          )}

          {type === 'other' && (
            <>
              <label className="field-label sr-only">Enter Store type</label>
              <input
                type="text"
                placeholder="Enter Store type"
                value={otherType}
                onChange={(e) => {
                  const next = e.target.value;
                  setOtherType(next);
                  // Always recompute so the select error disappears while typing custom type
                  setErrors(computeErrorsWith({ otherTypeV: next }));
                }}
                onBlur={() => setTouched((t) => ({ ...t, otherType: true }))}
                className={touched.otherType ? (errors.otherType ? 'input-error' : 'input-valid') : ''}
                aria-invalid={!!(touched.otherType && errors.otherType)}
              />
              {touched.otherType && errors.otherType && <p className="error-text">{errors.otherType}</p>}
            </>
          )}

          <div className="modal-actions">
            {/* No Back button per request */}
            <button type="submit" className="btn primary">Create</button>
          </div>
        </form>
      </div>
    </div>
  );
}