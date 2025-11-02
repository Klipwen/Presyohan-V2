import React, { useEffect, useRef, useState } from 'react';
import '../../styles/StoreModals.css';
import AddCategoryModal from './AddCategoryModal';

export default function EditItemModal({
  open,
  onClose,
  item,
  categories = [],
  onSave,
  onAddCategory
}) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState('');
  const [price, setPrice] = useState('');
  const [unit, setUnit] = useState('');
  const [touched, setTouched] = useState({});
  const [errors, setErrors] = useState({});
  const firstFieldRef = useRef(null);
  const [showAddCategory, setShowAddCategory] = useState(false);

  useEffect(() => {
    if (open && item) {
      setName(item.name || '');
      setDescription(item.description || '');
      setCategory(item.category || '');
      setPrice(String(item.price ?? ''));
      setUnit(item.unit || '');
      setTouched({});
      setErrors({});
      firstFieldRef.current?.focus();
    }
  }, [open, item]);

  const computeErrors = () => {
    const e = {};
    if (!name.trim()) e.name = 'Item name is required.';
    const priceNum = Number(price);
    if (!price || Number.isNaN(priceNum) || priceNum <= 0) e.price = 'Enter a valid price.';
    if (!unit.trim()) e.unit = 'Unit is required.';
    return e;
  };

  const submit = (ev) => {
    ev.preventDefault();
    const allTouched = { name: true, price: true, unit: true };
    setTouched(allTouched);
    const e = computeErrors();
    setErrors(e);
    if (Object.keys(e).length > 0) return;
    const payload = {
      id: item.id,
      name: name.trim(),
      description: description.trim(),
      category: category.trim(),
      price: Number(price),
      unit: unit.trim()
    };
    onSave?.(payload);
    onClose?.();
  };

  if (!open) return null;

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" onClick={(e) => { if (e.target === e.currentTarget) onClose?.(); }}>
      <div className="modal-card">
        <div className="modal-header-strip">
          <h2>Edit Item</h2>
          {onClose && (<button className="close-btn" onClick={onClose} aria-label="Close" />)}
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
              }}
            >
              <option value="">--Select category--</option>
              {Array.isArray(categories) && categories.map((c) => (
                <option key={c.id ?? c.name} value={c.name}>{c.name}</option>
              ))}
              <option value="__ADD__">+ Add Category…</option>
            </select>
            <span className="chevron" aria-hidden="true">▾</span>
          </div>

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

          <div className="modal-actions" style={{ justifyContent: 'space-between' }}>
            <button type="button" className="btn teal" onClick={onClose}>Cancel</button>
            <button type="submit" className="btn orange">Save</button>
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