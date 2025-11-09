import React, { useEffect, useRef, useState } from 'react';
import '../../styles/StoreModals.css';
import storeIcon from '../../assets/icon_store.png';
import AddCategoryModal from './AddCategoryModal';

export default function AddItemModal({
  open,
  onClose,
  storeName,
  storeBranch,
  categories = [],
  onCreateItem,
  onManageItems,
  onAddCategory,
  defaultCategory
}) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState('');
  const [price, setPrice] = useState('');
  const [unit, setUnit] = useState('');
  const [touched, setTouched] = useState({});
  const [errors, setErrors] = useState({});
  const [showAddCategory, setShowAddCategory] = useState(false);
  const firstFieldRef = useRef(null);

  useEffect(() => {
    if (open) {
      // Reset when opened to keep UX predictable
      setTouched({});
      setErrors({});
      firstFieldRef.current?.focus();
      if (defaultCategory) {
        setCategory(defaultCategory);
      }
    }
  }, [open, defaultCategory]);

  const computeErrors = () => {
    const e = {};
    if (!name.trim()) e.name = 'Item name is required.';
    if (!category.trim()) e.category = 'Category is required.';
    const priceNum = Number(price);
    if (!price || Number.isNaN(priceNum) || priceNum <= 0) e.price = 'Enter a valid price.';
    if (!unit.trim()) e.unit = 'Unit is required.';
    return e;
  };

  const submit = (ev) => {
    ev.preventDefault();
    const allTouched = { name: true, category: true, price: true, unit: true };
    setTouched(allTouched);
    const e = computeErrors();
    setErrors(e);
    if (Object.keys(e).length > 0) return;
    const payload = {
      name: name.trim(),
      description: description.trim(),
      category: category.trim(),
      price: Number(price),
      unit: unit.trim()
    };
    onCreateItem?.(payload);
    onClose?.();
  };

  const goManage = () => {
    if (onManageItems) onManageItems();
    onClose?.();
  };

  if (!open) return null;

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" onClick={(e) => { if (e.target === e.currentTarget) onClose?.(); }}>
      <div className="modal-card">
        <div className="modal-header-strip">
          <h2>Add Item</h2>
          {onClose && (
            <button className="close-btn" onClick={onClose} aria-label="Close" />
          )}
        </div>

        {/* Store row and Manage Items button (only if onManageItems is provided) */}
        <div style={{ padding: '14px 20px 0', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '12px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <img src={storeIcon} alt={storeName} style={{ width: 28, height: 28 }} />
            <div>
              <div style={{ fontWeight: 700, color: '#ff8c00' }}>{storeName}</div>
              <div style={{ fontSize: '0.85rem', color: '#999' }}>| {storeBranch}</div>
            </div>
          </div>
          {onManageItems && (
            <button
              onClick={goManage}
              style={{
                background: '#00bcd4',
                color: 'white',
                border: 'none',
                padding: '10px 16px',
                borderRadius: '22px',
                fontSize: '0.85rem',
                fontWeight: 600,
                cursor: 'pointer',
                whiteSpace: 'nowrap'
              }}
            >
              + Multiple Items
            </button>
          )}
        </div>

        <form onSubmit={submit} className="modal-form" noValidate>
          <label className="field-label">Item Name</label>
          <input
            ref={firstFieldRef}
            type="text"
            value={name}
            onChange={(e) => { setName(e.target.value); if (touched.name) setErrors(computeErrors()); }}
            onBlur={() => setTouched((t) => ({ ...t, name: true }))}
            className={touched.name ? (errors.name ? 'input-error' : 'input-valid') : ''}
            aria-invalid={!!(touched.name && errors.name)}
          />
          {touched.name && errors.name && <p className="error-text">{errors.name}</p>}

          <label className="field-label">Description</label>
          <input
            type="text"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Optional description"
          />

          <label className="field-label">Category</label>
          <div className="select-row">
            <select
              value={category}
              onChange={(e) => {
                const v = e.target.value;
                if (v === '__ADD__') {
                  setShowAddCategory(true);
                  return;
                }
                setCategory(v);
                if (touched.category) setErrors(computeErrors());
              }}
              onBlur={() => setTouched((t) => ({ ...t, category: true }))}
              className={touched.category ? (errors.category ? 'input-error' : 'input-valid') : ''}
              aria-invalid={!!(touched.category && errors.category)}
            >
              <option value="">--Select category--</option>
              {Array.isArray(categories) && categories.map((c) => (
                <option key={c.id ?? c.name} value={c.name}>{c.name}</option>
              ))}
              <option value="__ADD__">+ Add Category…</option>
            </select>
            <span className="chevron" aria-hidden="true">▾</span>
          </div>
          {touched.category && errors.category && <p className="error-text">{errors.category}</p>}

          <div className="two-col-row">
            <div className="col">
              <label className="field-label">Price</label>
              <input
                type="number"
                step="0.01"
                inputMode="decimal"
                value={price}
                onChange={(e) => { setPrice(e.target.value); if (touched.price) setErrors(computeErrors()); }}
                onBlur={() => setTouched((t) => ({ ...t, price: true }))}
                className={touched.price ? (errors.price ? 'input-error' : 'input-valid') : ''}
                aria-invalid={!!(touched.price && errors.price)}
              />
              {touched.price && errors.price && <p className="error-text">{errors.price}</p>}
            </div>
            <div className="col">
              <label className="field-label">Unit</label>
              <input
                type="text"
                value={unit}
                onChange={(e) => { setUnit(e.target.value); if (touched.unit) setErrors(computeErrors()); }}
                onBlur={() => setTouched((t) => ({ ...t, unit: true }))}
                className={touched.unit ? (errors.unit ? 'input-error' : 'input-valid') : ''}
                aria-invalid={!!(touched.unit && errors.unit)}
                placeholder="e.g., pc, kg, L"
              />
              {touched.unit && errors.unit && <p className="error-text">{errors.unit}</p>}
            </div>
          </div>

          <div className="modal-actions">
            <button type="submit" className="btn primary">Done</button>
          </div>
        </form>

        <AddCategoryModal
          open={showAddCategory}
          onClose={() => setShowAddCategory(false)}
          onAdd={(newName) => {
            onAddCategory?.(newName);
            setCategory(newName);
          }}
        />
      </div>
    </div>
  );
}