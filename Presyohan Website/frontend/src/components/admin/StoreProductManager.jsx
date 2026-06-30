import React, { useState, useEffect } from 'react';
import { supabase } from '../../config/supabaseClient';

export default function StoreProductManager({ store, onBack }) {
  const [categories, setCategories] = useState([]);
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedCategory, setSelectedCategory] = useState('ALL');
  
  // Modals / forms
  const [showAddProduct, setShowAddProduct] = useState(false);
  const [showAddCategory, setShowAddCategory] = useState(false);
  const [newCategoryName, setNewCategoryName] = useState('');
  const [editingProduct, setEditingProduct] = useState(null);

  // Form states
  const [prodName, setProdName] = useState('');
  const [prodPrice, setProdPrice] = useState('');
  const [prodUnit, setProdUnit] = useState('pc');
  const [prodDesc, setProdDesc] = useState('');
  const [prodCatId, setProdCatId] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

  // Import Excel states
  const [showImportModal, setShowImportModal] = useState(false);
  const [importFileName, setImportFileName] = useState('');
  const [importLoading, setImportLoading] = useState(false);
  const [importError, setImportError] = useState('');
  const [parsedItems, setParsedItems] = useState([]);
  const [parseIssues, setParseIssues] = useState([]);
  const [isImporting, setIsImporting] = useState(false);
  const importFileInputRef = React.useRef(null);

  const loadData = async () => {
    try {
      setLoading(true);

      // 1. Fetch categories
      const { data: cats, error: catsErr } = await supabase
        .from('categories')
        .select('*')
        .eq('store_id', store.id)
        .order('name', { ascending: true });

      if (catsErr) throw catsErr;
      setCategories(cats || []);

      // 2. Fetch products
      const { data: prods, error: prodsErr } = await supabase
        .from('products')
        .select('*')
        .eq('store_id', store.id)
        .order('name', { ascending: true });

      if (prodsErr) throw prodsErr;
      setProducts(prods || []);
    } catch (err) {
      console.error('Failed to load products/categories:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [store.id]);

  const handleAddCategory = async (e) => {
    e.preventDefault();
    if (!newCategoryName.trim()) return;

    try {
      setActionLoading(true);
      const uppercaseName = newCategoryName.trim().toUpperCase();

      // Check duplicate
      if (categories.some(c => c.name === uppercaseName)) {
        alert('A category with this name already exists.');
        return;
      }

      const { error } = await supabase
        .from('categories')
        .insert({
          store_id: store.id,
          name: uppercaseName
        });

      if (error) throw error;
      setNewCategoryName('');
      setShowAddCategory(false);
      await loadData();
    } catch (err) {
      console.error(err);
      alert('Error: ' + err.message);
    } finally {
      setActionLoading(false);
    }
  };

  const handleDeleteCategory = async (catId, catName) => {
    if (!window.confirm(`Are you sure you want to delete the category "${catName}"? This will also delete all items cataloged under this category.`)) {
      return;
    }

    try {
      const { error } = await supabase
        .from('categories')
        .delete()
        .eq('id', catId);

      if (error) throw error;
      setSelectedCategory('ALL');
      await loadData();
    } catch (err) {
      console.error(err);
      alert('Error: ' + err.message);
    }
  };

  const handleProductSubmit = async (e) => {
    e.preventDefault();
    if (!prodName.trim() || !prodPrice) return;
    if (!prodCatId) {
      alert('Please select a category.');
      return;
    }

    try {
      setActionLoading(true);
      const payload = {
        store_id: store.id,
        name: prodName.trim(),
        price: parseFloat(prodPrice),
        unit: prodUnit,
        description: prodDesc.trim() || null,
        category_id: prodCatId || null,
        is_public: true // Standard store items are always public
      };

      let query;
      if (editingProduct) {
        query = supabase
          .from('products')
          .update(payload)
          .eq('id', editingProduct.id);
      } else {
        query = supabase
          .from('products')
          .insert(payload);
      }

      const { error } = await query;
      if (error) throw error;

      setShowAddProduct(false);
      setEditingProduct(null);
      setProdName('');
      setProdPrice('');
      setProdUnit('pc');
      setProdDesc('');
      setProdCatId('');
      await loadData();
    } catch (err) {
      console.error(err);
      alert('Error adding/updating product: ' + err.message);
    } finally {
      setActionLoading(false);
    }
  };

  const handleEditProduct = (prod) => {
    setEditingProduct(prod);
    setProdName(prod.name);
    setProdPrice(prod.price.toString());
    setProdUnit(prod.unit || 'pc');
    setProdDesc(prod.description || '');
    setProdCatId(prod.category_id || '');
    setShowAddProduct(true);
  };

  const handleDeleteProduct = async (prodId, prodName) => {
    if (!window.confirm(`Are you sure you want to delete "${prodName}"?`)) return;

    try {
      const { error } = await supabase
        .from('products')
        .delete()
        .eq('id', prodId);

      if (error) throw error;
      await loadData();
    } catch (err) {
      console.error(err);
      alert('Error: ' + err.message);
    }
  };

  const handleImportFile = async (file) => {
    if (!file) return;
    setImportFileName(file.name);
    setImportLoading(true);
    setImportError('');
    setParsedItems([]);
    setParseIssues([]);

    try {
      // 1. Fetch existing categories and products for comparison
      const { data: dbCats, error: dbCatsErr } = await supabase
        .from('categories')
        .select('id, name')
        .eq('store_id', store.id);
      if (dbCatsErr) throw dbCatsErr;

      const { data: dbProds, error: dbProdsErr } = await supabase
        .from('products')
        .select('id, category_id, name, description, price, unit')
        .eq('store_id', store.id);
      if (dbProdsErr) throw dbProdsErr;

      // XLSX reader
      const XLSXMod = await import('xlsx');
      const XLSX = XLSXMod.read ? XLSXMod : (XLSXMod.default || XLSXMod);
      if (!XLSX || typeof XLSX.read !== 'function') {
        throw new Error('XLSX module is unavailable');
      }

      const buf = await file.arrayBuffer();
      const wb = XLSX.read(buf, { type: 'array' });
      const sheetName = wb.SheetNames[0];
      const sheet = wb.Sheets[sheetName];
      if (!sheet) {
        throw new Error('Spreadsheet has no worksheets.');
      }

      const rowsArr = XLSX.utils.sheet_to_json(sheet, { header: 1, blankrows: false });
      if (rowsArr.length === 0) {
        throw new Error('Spreadsheet is empty.');
      }

      const COLUMN_ALIASES = {
        category:    ['category', 'cat'],
        name:        ['name', 'product', 'item', 'item name'],
        description: ['description', 'desc', 'details'],
        unit:        ['unit', 'units', 'uom', 'size'],
        price:       ['price', 'amount', 'cost']
      };

      const findHeaderRow = (rowsArr, maxRows = 25) => {
        for (let r = 0; r < Math.min(rowsArr.length, maxRows); r++) {
          const row = rowsArr[r] || [];
          const idx = { category: -1, name: -1, description: -1, unit: -1, price: -1 };
          for (let c = 0; c < row.length; c++) {
            const cell = String(row[c] || '').trim().toLowerCase();
            for (const key of Object.keys(COLUMN_ALIASES)) {
              if (idx[key] === -1 && COLUMN_ALIASES[key].includes(cell)) {
                idx[key] = c;
              }
            }
          }
          if (idx.name >= 0 && idx.price >= 0) {
            return { headerRowIndex: r, col: idx };
          }
        }
        return { headerRowIndex: -1, col: null };
      };

      const parsePrice = (raw) => {
        if (typeof raw === 'number') return raw;
        if (raw === undefined || raw === null || raw === '') return NaN;
        const clean = String(raw)
          .replace(/₱/g, '')
          .replace(/PHP/gi, '')
          .replace(/,/g, '')
          .trim();
        const val = parseFloat(clean);
        return isNaN(val) ? NaN : val;
      };

      const headerInfo = findHeaderRow(rowsArr);
      let headerRowIndex = -1;
      let col = null;

      if (headerInfo.headerRowIndex !== -1) {
        headerRowIndex = headerInfo.headerRowIndex;
        col = headerInfo.col;
      } else {
        let firstNonEmptyRow = 0;
        for (let r = 0; r < rowsArr.length; r++) {
          if ((rowsArr[r] || []).some(cell => String(cell || '').trim() !== '')) {
            firstNonEmptyRow = r;
            break;
          }
        }
        headerRowIndex = firstNonEmptyRow - 1;
        col = { category: 0, name: 1, description: 2, unit: 3, price: 4 };
      }

      // Normalization helpers
      const normalizeText = (val) => {
        return (val || '').trim().replace(/\s+/g, ' ').toLowerCase();
      };

      const productKey = (name, desc, unit) => {
        return `${normalizeText(name)}|${normalizeText(desc)}|${normalizeText(unit)}`;
      };

      // Map existing products by key
      const dbProductMap = {};
      (dbProds || []).forEach(p => {
        const key = productKey(p.name, p.description, p.unit);
        if (!dbProductMap[key]) {
          dbProductMap[key] = [];
        }
        dbProductMap[key].push(p);
      });

      // Map category name to ID
      const dbCatMapByName = {};
      (dbCats || []).forEach(c => {
        dbCatMapByName[normalizeText(c.name)] = c.id;
      });

      const seenImportKeys = new Set();
      const itemsList = [];
      const issuesList = [];

      for (let r = headerRowIndex + 1; r < rowsArr.length; r++) {
        const row = rowsArr[r] || [];
        const getCol = (key) => {
          const idx = col[key];
          return idx >= 0 && idx < row.length ? String(row[idx] ?? '').trim() : '';
        };

        const category    = getCol('category');
        const name        = getCol('name');
        const description = getCol('description');
        const unit        = getCol('unit');
        const priceRaw    = col.price >= 0 && col.price < row.length ? row[col.price] : '';
        const price       = parsePrice(priceRaw);

        // Skip completely empty row
        if (!name && !category && !description && !unit && isNaN(price)) {
          continue;
        }

        // Deduplicate within the imported file list
        const importListKey = `${normalizeText(name)}|${normalizeText(description)}|${normalizeText(category)}|${normalizeText(unit)}|${price}`;
        if (seenImportKeys.has(importListKey)) {
          continue; // skip duplicate row in spreadsheet
        }
        seenImportKeys.add(importListKey);

        const validationErrors = [];
        let status = 'NEW';
        let matchedProductId = null;
        let resolvedCategoryId = dbCatMapByName[normalizeText(category)] || null;

        if (!name) {
          validationErrors.push('Product name is required');
          status = 'INVALID';
        }
        if (!category) {
          validationErrors.push('Category is required');
          status = 'INVALID';
        }
        if (isNaN(price)) {
          validationErrors.push(`Price must be a valid number (found: '${priceRaw}')`);
          status = 'INVALID';
        } else if (price < 0) {
          validationErrors.push(`Price cannot be negative (found: ${price})`);
          status = 'INVALID';
        }

        if (status !== 'INVALID') {
          const key = productKey(name, description, unit);
          const dbMatches = dbProductMap[key];

          const isExactDuplicateInDb = dbMatches ? dbMatches.some(dbProd => {
            const samePrice = Number(dbProd.price) === price;
            const sameUnit = normalizeText(dbProd.unit) === normalizeText(unit);
            return samePrice && sameUnit;
          }) : false;

          if (isExactDuplicateInDb) {
            status = 'DUPLICATE';
            const matchedDbProd = dbMatches.find(dbProd => {
              const samePrice = Number(dbProd.price) === price;
              const sameUnit = normalizeText(dbProd.unit) === normalizeText(unit);
              return samePrice && sameUnit;
            });
            matchedProductId = matchedDbProd.id;
            resolvedCategoryId = matchedDbProd.category_id;
          } else if (dbMatches && dbMatches.length > 0) {
            if (dbMatches.length === 1) {
              const matchedDbProd = dbMatches[0];
              status = 'UPDATE';
              matchedProductId = matchedDbProd.id;
              resolvedCategoryId = matchedDbProd.category_id;
            } else {
              // Ambiguous match across categories -> find the one in the same parsed category
              const catDbMatches = dbMatches.filter(m => resolvedCategoryId && m.category_id === resolvedCategoryId);
              if (catDbMatches.length === 1) {
                status = 'UPDATE';
                matchedProductId = catDbMatches[0].id;
              } else {
                status = 'INVALID';
                validationErrors.push('Ambiguous duplicate product name found in database across different categories.');
              }
            }
          }
        }

        if (status === 'INVALID') {
          issuesList.push({
            rowIndex: r + 1,
            message: validationErrors.join(', '),
            name: name || ''
          });
        }

        itemsList.push({
          rowIndex: r + 1,
          category: (category || '').toUpperCase().trim(),
          categoryId: resolvedCategoryId,
          name: name.trim(),
          description: description.trim() || null,
          unit: unit.trim() || 'pc',
          price,
          status,
          validationErrors,
          productId: matchedProductId
        });
      }

      setParsedItems(itemsList);
      setParseIssues(issuesList);
    } catch (err) {
      console.error('Failed to parse Excel file:', err);
      setImportError(err.message || 'Failed to read Excel file. Make sure it is a valid spreadsheet.');
    } finally {
      setImportLoading(false);
    }
  };

  const handleConfirmImport = async () => {
    const importableItems = parsedItems.filter(p => p.status === 'NEW' || p.status === 'UPDATE');
    if (importableItems.length === 0) return;

    setIsImporting(true);
    setImportError('');

    try {
      const newItems = importableItems.filter(item => item.status === 'NEW');
      const updateItems = importableItems.filter(item => item.status === 'UPDATE');

      // 1. Resolve Categories
      const importCatNames = Array.from(new Set(newItems.map(item => item.category.toUpperCase().trim())));

      const { data: existingCats, error: existingCatsErr } = await supabase
        .from('categories')
        .select('*')
        .eq('store_id', store.id);

      if (existingCatsErr) throw existingCatsErr;

      const existingCatNames = (existingCats || []).map(c => c.name.toUpperCase().trim());
      const newCatNames = importCatNames.filter(name => !existingCatNames.includes(name));

      if (newCatNames.length > 0) {
        const { error: insertCatsErr } = await supabase
          .from('categories')
          .insert(newCatNames.map(name => ({
            store_id: store.id,
            name: name
          })));

        if (insertCatsErr) throw insertCatsErr;
      }

      const { data: allCats, error: allCatsErr } = await supabase
        .from('categories')
        .select('*')
        .eq('store_id', store.id);

      if (allCatsErr) throw allCatsErr;

      const catNameToIdMap = {};
      allCats.forEach(c => {
        catNameToIdMap[c.name.toUpperCase().trim()] = c.id;
      });

      // 2. Insert NEW products
      if (newItems.length > 0) {
        const productsPayload = newItems.map(item => ({
          store_id: store.id,
          category_id: catNameToIdMap[item.category] || null,
          name: item.name,
          price: item.price,
          unit: item.unit,
          description: item.description,
          is_public: true
        }));

        const { error: insertProdsErr } = await supabase
          .from('products')
          .insert(productsPayload);

        if (insertProdsErr) throw insertProdsErr;
      }

      // 3. Update UPDATE products
      if (updateItems.length > 0) {
        const updatePromises = updateItems.map(item => {
          return supabase
            .from('products')
            .update({
              name: item.name,
              price: item.price,
              unit: item.unit,
              description: item.description,
              category_id: item.categoryId || catNameToIdMap[item.category] || null
            })
            .eq('id', item.productId)
            .eq('store_id', store.id);
        });

        const results = await Promise.all(updatePromises);
        const firstError = results.find(r => r.error);
        if (firstError) throw firstError.error;
      }

      // 4. Success! Refresh UI and close modal
      setShowImportModal(false);
      setParsedItems([]);
      setParseIssues([]);
      setImportFileName('');
      await loadData();
      alert(`Import complete! Saved ${newItems.length} new items and updated ${updateItems.length} prices.`);
    } catch (err) {
      console.error('Failed to import products:', err);
      setImportError(err.message || 'An error occurred during import. Please try again.');
    } finally {
      setIsImporting(false);
    }
  };

  // Filter products by selected category
  const filteredProducts = products.filter(p => {
    if (selectedCategory === 'ALL') return true;
    return p.category_id === selectedCategory;
  });

  if (loading) {
    return (
      <div style={{ padding: '40px', textAlign: 'center', color: '#64748b' }}>
        Loading catalog inventory...
      </div>
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '24px' }}>
        <button className="admin-btn-action" onClick={onBack}>
          ← Back to Stores
        </button>
        <h2 style={{ fontSize: '1.25rem', fontWeight: 700, margin: 0 }}>
          {store.name} Catalog
        </h2>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '220px 1fr', gap: '28px', alignItems: 'start' }}>
        {/* Category List Panel */}
        <div style={{ background: '#ffffff', borderRadius: '16px', border: '1px solid rgba(0,0,0,0.04)', padding: '16px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px' }}>
            <h4 style={{ margin: 0, fontSize: '0.85rem', color: '#475569', textTransform: 'uppercase', letterSpacing: '0.5px' }}>Categories</h4>
            <button 
              className="admin-btn-action"
              style={{ padding: '4px 8px', fontSize: '0.75rem', color: '#ff8c00', borderColor: 'rgba(255,140,0,0.2)' }}
              onClick={() => setShowAddCategory(true)}
            >
              + Add
            </button>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
            <button
              className={`admin-menu-item ${selectedCategory === 'ALL' ? 'active' : ''}`}
              style={{ padding: '10px 12px', fontSize: '0.85rem' }}
              onClick={() => setSelectedCategory('ALL')}
            >
              All Items ({products.length})
            </button>
            
            {categories.map(c => {
              const count = products.filter(p => p.category_id === c.id).length;
              return (
                <div key={c.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', group: 'true' }}>
                  <button
                    className={`admin-menu-item ${selectedCategory === c.id ? 'active' : ''}`}
                    style={{ padding: '10px 12px', fontSize: '0.85rem', flex: 1, textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}
                    onClick={() => setSelectedCategory(c.id)}
                  >
                    {c.name} ({count})
                  </button>
                  <button 
                    style={{ background: 'none', border: 'none', color: '#ef4444', cursor: 'pointer', padding: '0 8px', fontSize: '0.9rem' }}
                    onClick={() => handleDeleteCategory(c.id, c.name)}
                    title="Delete Category"
                  >
                    ×
                  </button>
                </div>
              );
            })}
          </div>
        </div>

        {/* Products Table Panel */}
        <div className="admin-card" style={{ padding: '24px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
            <h4 style={{ margin: 0, fontSize: '1rem', fontWeight: 700 }}>
              Products List ({filteredProducts.length})
            </h4>
            <div style={{ display: 'flex', gap: '8px' }}>
              <button className="admin-btn-action" style={{ display: 'flex', alignItems: 'center', gap: '6px', color: '#ff8c00', borderColor: 'rgba(255, 140, 0, 0.2)' }} onClick={() => setShowImportModal(true)}>
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth="2.2" stroke="currentColor" style={{ width: '14px', height: '14px' }}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M3 16.5v2.25A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75V16.5m-13.5-9L12 3m0 0l4.5 4.5M12 3v13.5" />
                </svg>
                Import Excel
              </button>
              <button className="admin-btn-primary" onClick={() => {
                setEditingProduct(null);
                setProdName('');
                setProdPrice('');
                setProdUnit('pc');
                setProdDesc('');
                setProdCatId(selectedCategory !== 'ALL' ? selectedCategory : '');
                setShowAddProduct(true);
              }}>
                Add Product
              </button>
            </div>
          </div>

          <div className="admin-table-container">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Product Name</th>
                  <th>Price</th>
                  <th>Unit</th>
                  <th>Description</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredProducts.map(p => (
                  <tr key={p.id}>
                    <td style={{ fontWeight: 600 }}>{p.name}</td>
                    <td style={{ color: '#ff8c00', fontWeight: 700 }}>₱{Number(p.price).toFixed(2)}</td>
                    <td>{p.unit || 'pc'}</td>
                    <td style={{ color: '#64748b', fontSize: '0.8rem' }}>{p.description || '—'}</td>
                    <td>
                      <div className="admin-actions-cell">
                        <button className="admin-btn-action" onClick={() => handleEditProduct(p)}>
                          Edit
                        </button>
                        <button className="admin-btn-action suspend" onClick={() => handleDeleteProduct(p.id, p.name)}>
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
                {filteredProducts.length === 0 && (
                  <tr>
                    <td colSpan="5" style={{ textAlign: 'center', color: '#94a3b8', padding: '32px' }}>
                      No products found in this category. Click 'Add Product' to insert one.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* Add/Edit Product Modal */}
      {showAddProduct && (
        <div style={{
          position: 'fixed',
          inset: 0,
          backgroundColor: 'rgba(0,0,0,0.4)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 100,
          fontFamily: 'Outfit'
        }}>
          <div className="admin-card" style={{ width: '450px', padding: '28px' }}>
            <h3 style={{ marginBottom: '16px' }}>{editingProduct ? 'Edit Product' : 'Add New Product'}</h3>
            <form onSubmit={handleProductSubmit}>
              <div style={{ marginBottom: '12px' }}>
                <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#475569', marginBottom: '6px' }}>Product Name</label>
                <input
                  type="text"
                  required
                  className="admin-search-input"
                  style={{ paddingLeft: '16px' }}
                  placeholder="e.g. Premium White Rice"
                  value={prodName}
                  onChange={(e) => setProdName(e.target.value)}
                />
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginBottom: '12px' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#475569', marginBottom: '6px' }}>Price (₱)</label>
                  <input
                    type="number"
                    step="0.01"
                    required
                    className="admin-search-input"
                    style={{ paddingLeft: '16px' }}
                    placeholder="50.00"
                    value={prodPrice}
                    onChange={(e) => setProdPrice(e.target.value)}
                  />
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#475569', marginBottom: '6px' }}>Unit</label>
                  <input
                    type="text"
                    required
                    className="admin-search-input"
                    style={{ paddingLeft: '16px' }}
                    placeholder="kg, pc, pack"
                    value={prodUnit}
                    onChange={(e) => setProdUnit(e.target.value)}
                  />
                </div>
              </div>

              <div style={{ marginBottom: '12px' }}>
                <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#475569', marginBottom: '6px' }}>Category</label>
                <select
                  required
                  className="admin-select"
                  style={{ width: '100%' }}
                  value={prodCatId}
                  onChange={(e) => setProdCatId(e.target.value)}
                >
                  <option value="" disabled>Select Category</option>
                  {categories.map(c => (
                    <option key={c.id} value={c.id}>{c.name}</option>
                  ))}
                </select>
              </div>

              <div style={{ marginBottom: '24px' }}>
                <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#475569', marginBottom: '6px' }}>Description</label>
                <textarea
                  className="admin-search-input"
                  style={{ paddingLeft: '16px', height: '80px', resize: 'none', paddingTop: '10px' }}
                  placeholder="e.g. Local well-milled rice"
                  value={prodDesc}
                  onChange={(e) => setProdDesc(e.target.value)}
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
                <button
                  type="button"
                  className="admin-btn-action"
                  onClick={() => {
                    setShowAddProduct(false);
                    setEditingProduct(null);
                  }}
                >
                  Cancel
                </button>
                <button type="submit" className="admin-btn-primary" disabled={actionLoading}>
                  {actionLoading ? 'Saving...' : (editingProduct ? 'Save Changes' : 'Add Product')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Add Category Modal */}
      {showAddCategory && (
        <div style={{
          position: 'fixed',
          inset: 0,
          backgroundColor: 'rgba(0,0,0,0.4)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 100,
          fontFamily: 'Outfit'
        }}>
          <div className="admin-card" style={{ width: '380px', padding: '28px' }}>
            <h3 style={{ marginBottom: '16px' }}>Add Category</h3>
            <form onSubmit={handleAddCategory}>
              <div style={{ marginBottom: '24px' }}>
                <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#475569', marginBottom: '6px' }}>Category Name</label>
                <input
                  type="text"
                  required
                  className="admin-search-input"
                  style={{ paddingLeft: '16px' }}
                  placeholder="e.g. BEVERAGES"
                  value={newCategoryName}
                  onChange={(e) => setNewCategoryName(e.target.value)}
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
                <button
                  type="button"
                  className="admin-btn-action"
                  onClick={() => {
                    setShowAddCategory(false);
                    setNewCategoryName('');
                  }}
                >
                  Cancel
                </button>
                <button type="submit" className="admin-btn-primary" disabled={actionLoading}>
                  {actionLoading ? 'Adding...' : 'Add'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Import Excel Modal */}
      {showImportModal && (
        <div style={{
          position: 'fixed',
          inset: 0,
          backgroundColor: 'rgba(0,0,0,0.4)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 100,
          fontFamily: 'Outfit'
        }}>
          <div className="admin-card" style={{ width: '600px', maxWidth: '95%', padding: '28px', maxHeight: '90vh', display: 'flex', flexDirection: 'column' }}>
            <h3 style={{ marginBottom: '8px' }}>Import Products from Excel</h3>
            <p style={{ fontSize: '0.85rem', color: '#64748b', marginBottom: '20px' }}>
              Upload an Excel (.xlsx) file to bulk import products into <strong>{store.name}</strong>.
            </p>

            <div style={{ 
              border: '2px dashed #e2e8f0', 
              borderRadius: '12px', 
              padding: '24px', 
              textAlign: 'center', 
              backgroundColor: '#f8fafc',
              cursor: 'pointer',
              marginBottom: '20px',
              transition: 'border-color 0.2s ease',
              position: 'relative'
            }}
            onClick={() => importFileInputRef.current?.click()}
            >
              <input
                ref={importFileInputRef}
                type="file"
                accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                style={{ display: 'none' }}
                onChange={(e) => handleImportFile(e.target.files?.[0])}
              />
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth="1.5" stroke="#94a3b8" style={{ width: '48px', height: '48px', margin: '0 auto 12px' }}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M3 16.5v2.25A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75V16.5m-13.5-9L12 3m0 0l4.5 4.5M12 3v13.5" />
              </svg>
              {importFileName ? (
                <div>
                  <span style={{ fontWeight: 600, color: '#ff8c00', fontSize: '0.9rem' }}>{importFileName}</span>
                  <p style={{ fontSize: '0.75rem', color: '#64748b', marginTop: '4px' }}>Click to select another file</p>
                </div>
              ) : (
                <div>
                  <span style={{ fontWeight: 600, color: '#475569', fontSize: '0.9rem' }}>Choose Excel spreadsheet</span>
                  <p style={{ fontSize: '0.75rem', color: '#64748b', marginTop: '4px' }}>Only .xlsx files are supported</p>
                </div>
              )}
            </div>

            {importError && (
              <div style={{ backgroundColor: '#fef2f2', border: '1px solid #fee2e2', color: '#ef4444', padding: '12px', borderRadius: '8px', fontSize: '0.8rem', marginBottom: '20px' }}>
                {importError}
              </div>
            )}

            {importLoading && (
              <div style={{ padding: '20px 0', textAlign: 'center', color: '#64748b', fontSize: '0.85rem' }}>
                Parsing spreadsheet...
              </div>
            )}

            {!importLoading && parsedItems.length > 0 && (
              <div style={{ flex: 1, overflowY: 'auto', marginBottom: '20px', paddingRight: '4px' }}>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '12px', marginBottom: '16px' }}>
                  {/* New Items */}
                  <div style={{ backgroundColor: '#f0fdf4', border: '1px solid #dcfce7', padding: '12px', borderRadius: '8px' }}>
                    <div style={{ fontSize: '0.7rem', color: '#166534', fontWeight: 600, textTransform: 'uppercase' }}>New Items</div>
                    <div style={{ fontSize: '1.3rem', fontWeight: 700, color: '#15803d' }}>
                      {parsedItems.filter(p => p.status === 'NEW').length}
                    </div>
                  </div>
                  {/* Price Updates */}
                  <div style={{ backgroundColor: '#eff6ff', border: '1px solid #dbeafe', padding: '12px', borderRadius: '8px' }}>
                    <div style={{ fontSize: '0.7rem', color: '#1e40af', fontWeight: 600, textTransform: 'uppercase' }}>Price Updates</div>
                    <div style={{ fontSize: '1.3rem', fontWeight: 700, color: '#1d4ed8' }}>
                      {parsedItems.filter(p => p.status === 'UPDATE').length}
                    </div>
                  </div>
                  {/* Duplicates */}
                  <div style={{ backgroundColor: '#f8fafc', border: '1px solid #e2e8f0', padding: '12px', borderRadius: '8px' }}>
                    <div style={{ fontSize: '0.7rem', color: '#475569', fontWeight: 600, textTransform: 'uppercase' }}>Duplicates</div>
                    <div style={{ fontSize: '1.3rem', fontWeight: 700, color: '#334155' }}>
                      {parsedItems.filter(p => p.status === 'DUPLICATE').length}
                    </div>
                  </div>
                  {/* Issues */}
                  <div style={{ backgroundColor: '#fef2f2', border: '1px solid #fee2e2', padding: '12px', borderRadius: '8px' }}>
                    <div style={{ fontSize: '0.7rem', color: '#991b1b', fontWeight: 600, textTransform: 'uppercase' }}>Errors</div>
                    <div style={{ fontSize: '1.3rem', fontWeight: 700, color: '#b91c1c' }}>
                      {parsedItems.filter(p => p.status === 'INVALID').length}
                    </div>
                  </div>
                </div>

                {parseIssues.length > 0 && (
                  <div style={{ marginBottom: '16px' }}>
                    <h5 style={{ margin: '0 0 6px', fontSize: '0.8rem', fontWeight: 600, color: '#991b1b' }}>Validation Warnings (will be skipped):</h5>
                    <div style={{ backgroundColor: '#fff5f5', border: '1px solid #fed7d7', borderRadius: '8px', padding: '10px', maxHeight: '100px', overflowY: 'auto', fontSize: '0.75rem', color: '#c53030' }}>
                      {parseIssues.map((issue, idx) => (
                        <div key={idx} style={{ marginBottom: '4px' }}>
                          Row {issue.rowIndex}: {issue.message} {issue.name ? `(Name: "${issue.name}")` : ''}
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                <h5 style={{ margin: '0 0 8px', fontSize: '0.8rem', fontWeight: 600, color: '#475569' }}>Items Preview (First 50 items shown):</h5>
                <div style={{ border: '1px solid #e2e8f0', borderRadius: '8px', overflow: 'hidden' }}>
                  <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.75rem' }}>
                    <thead>
                      <tr style={{ backgroundColor: '#f1f5f9', borderBottom: '1px solid #e2e8f0', textAlign: 'left' }}>
                        <th style={{ padding: '6px 8px' }}>Category</th>
                        <th style={{ padding: '6px 8px' }}>Name</th>
                        <th style={{ padding: '6px 8px' }}>Unit</th>
                        <th style={{ padding: '6px 8px' }}>Price</th>
                        <th style={{ padding: '6px 8px' }}>Import Action</th>
                      </tr>
                    </thead>
                    <tbody>
                      {parsedItems.slice(0, 50).map((item, idx) => {
                        let statusColor = '#334155';
                        let statusBg = 'transparent';
                        let badgeText = '';

                        if (item.status === 'NEW') {
                          statusBg = '#f0fdf4';
                          statusColor = '#15803d';
                          badgeText = 'NEW';
                        } else if (item.status === 'UPDATE') {
                          statusBg = '#eff6ff';
                          statusColor = '#1d4ed8';
                          badgeText = 'PRICE UPDATE';
                        } else if (item.status === 'DUPLICATE') {
                          statusBg = '#f1f5f9';
                          statusColor = '#64748b';
                          badgeText = 'SKIP (DUPLICATE)';
                        } else if (item.status === 'INVALID') {
                          statusBg = '#fff5f5';
                          statusColor = '#e53e3e';
                          badgeText = 'SKIP (ERROR)';
                        }

                        return (
                          <tr key={idx} style={{ borderBottom: '1px solid #f1f5f9', backgroundColor: item.status === 'DUPLICATE' || item.status === 'INVALID' ? '#fafafa' : 'transparent' }}>
                            <td style={{ padding: '6px 8px', color: '#64748b' }}>{item.category}</td>
                            <td style={{ padding: '6px 8px', fontWeight: 600, color: item.status === 'DUPLICATE' ? '#94a3b8' : '#1e293b' }}>
                              {item.name}
                            </td>
                            <td style={{ padding: '6px 8px', color: '#64748b' }}>{item.unit}</td>
                            <td style={{ padding: '6px 8px', color: item.status === 'DUPLICATE' ? '#94a3b8' : '#ff8c00', fontWeight: 600 }}>
                              ₱{isNaN(item.price) ? '—' : item.price.toFixed(2)}
                            </td>
                            <td style={{ padding: '6px 8px' }}>
                              <span style={{
                                display: 'inline-block',
                                padding: '2px 6px',
                                borderRadius: '4px',
                                fontSize: '0.65rem',
                                fontWeight: 700,
                                backgroundColor: statusBg,
                                color: statusColor
                              }}>
                                {badgeText}
                              </span>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                  {parsedItems.length > 50 && (
                    <div style={{ padding: '6px 8px', textAlign: 'center', backgroundColor: '#f8fafc', color: '#64748b', fontSize: '0.7rem', borderTop: '1px solid #e2e8f0' }}>
                      And {parsedItems.length - 50} more items...
                    </div>
                  )}
                </div>
              </div>
            )}

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: 'auto', paddingTop: '16px', borderTop: '1px solid #f1f5f9' }}>
              <button
                type="button"
                className="admin-btn-action"
                disabled={isImporting}
                onClick={() => {
                  setShowImportModal(false);
                  setImportFileName('');
                  setParsedItems([]);
                  setParseIssues([]);
                  setImportError('');
                }}
              >
                Cancel
              </button>
              <button
                type="button"
                className="admin-btn-primary"
                disabled={isImporting || parsedItems.filter(p => p.status === 'NEW' || p.status === 'UPDATE').length === 0}
                onClick={handleConfirmImport}
              >
                {isImporting ? 'Importing...' : `Import ${parsedItems.filter(p => p.status === 'NEW' || p.status === 'UPDATE').length} Items`}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
