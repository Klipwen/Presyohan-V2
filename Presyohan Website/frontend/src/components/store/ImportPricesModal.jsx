import React, { useEffect, useMemo, useRef, useState } from 'react'
import { supabase } from '../../config/supabaseClient'

export default function ImportPricesModal({ open, onClose, storeId, storeName, role }) {
  const [step, setStep] = useState('upload')
  const [error, setError] = useState('')
  const [isBusy, setIsBusy] = useState(false)

  const [fileName, setFileName] = useState('')
  const [rows, setRows] = useState([])
  const [issues, setIssues] = useState([])

  const [preview, setPreview] = useState({ creates: [], updates: [] })
  const [applySummary, setApplySummary] = useState({ created: 0, updated: 0 })

  const fileInputRef = useRef(null)
  const txtInputRef = useRef(null)
  const [textInput, setTextInput] = useState('')
  const [textCharCount, setTextCharCount] = useState(0)

  useEffect(() => {
    if (!open) {
      setStep('upload')
      setError('')
      setIsBusy(false)
      setFileName('')
      setRows([])
      setIssues([])
      setPreview({ creates: [], updates: [] })
      setApplySummary({ created: 0, updated: 0 })
      setTextInput('')
      setTextCharCount(0)
    }
  }, [open])

  const canImport = useMemo(() => role === 'owner' || role === 'manager', [role])

  const handlePick = () => fileInputRef.current?.click()
  const handlePickTxt = () => txtInputRef.current?.click()

  const handleFile = async (file) => {
    if (!file) return
    setError('')
    setIsBusy(true)
    setFileName(file.name)
    try {
      const [{ default: ExcelJS }] = await Promise.all([
        import('exceljs')
      ])
      const wb = new ExcelJS.Workbook()
      const buf = await file.arrayBuffer()
      await wb.xlsx.load(buf)
      const ws = wb.worksheets[0]
      if (!ws) throw new Error('Worksheet not found')

      const expected = ['Category', 'Name', 'Description', 'Unit', 'Price']
      let headerRowIndex = -1
      for (let r = 1; r <= Math.min(ws.rowCount, 15); r++) {
        const vals = (ws.getRow(r).values || []).slice(1).map(v => String(v || '').trim())
        if (vals.length >= 5) {
          const head = vals.slice(0, 5)
          const match = head.every((v, i) => v === expected[i])
          if (match) { headerRowIndex = r; break }
        }
      }
      if (headerRowIndex === -1) throw new Error('Invalid template: headers not found')

      const parsed = []
      for (let r = headerRowIndex + 1; r <= ws.rowCount; r++) {
        const row = ws.getRow(r)
        const vals = (row.values || []).slice(1)
        const category = String(vals[0] ?? '').trim()
        const name = String(vals[1] ?? '').trim()
        const description = String(vals[2] ?? '').trim()
        const unit = String(vals[3] ?? '').trim()
        const priceRaw = vals[4]
        const price = typeof priceRaw === 'number' ? priceRaw : Number(String(priceRaw ?? '').replace(/[^0-9.\-]/g, ''))
        const emptyRow = !category && !name && !description && !unit && (price === 0 || Number.isNaN(price))
        if (emptyRow) continue
        parsed.push({ rowIndex: r, category, name, description, unit, price })
      }

      const issues = []
      for (const p of parsed) {
        if (!p.name) issues.push({ rowIndex: p.rowIndex, message: 'Name is required' })
        if (!p.category) issues.push({ rowIndex: p.rowIndex, message: 'Category is required' })
        if (!p.unit) issues.push({ rowIndex: p.rowIndex, message: 'Unit is required' })
        if (Number.isNaN(p.price)) issues.push({ rowIndex: p.rowIndex, message: 'Price must be numeric' })
      }

      setRows(parsed)
      setIssues(issues)
      setStep('validate')
    } catch (e) {
      setError(e.message || 'Failed to read file')
    } finally {
      setIsBusy(false)
    }
  }

  const handleTxtFile = async (file) => {
    if (!file) return
    setError('')
    setIsBusy(true)
    setFileName(file.name)
    try {
      const text = await file.text()
      const { parsed, issues } = parseNoteText(text)
      setRows(parsed)
      setIssues(issues)
      setStep('validate')
    } catch (e) {
      setError(e.message || 'Failed to read TXT file')
    } finally {
      setIsBusy(false)
    }
  }

  const handleParseText = () => {
    if (!textInput.trim()) return
    setError('')
    setIsBusy(true)
    try {
      const { parsed, issues } = parseNoteText(textInput)
      setRows(parsed)
      setIssues(issues)
      setStep('validate')
    } catch (e) {
      setError(e.message || 'Failed to parse text')
    } finally {
      setIsBusy(false)
    }
  }

  const computeDryRun = async () => {
    setError('')
    setIsBusy(true)
    try {
      const { data, error: rpcErr } = await supabase.rpc('get_store_products', { p_store_id: storeId })
      if (rpcErr) throw rpcErr
      const existing = Array.isArray(data) ? data : []
      const byName = new Map()
      for (const e of existing) {
        const key = String(e.name || '').trim().toUpperCase()
        if (!byName.has(key)) byName.set(key, e)
      }
      const creates = []
      const updates = []
      for (const r of rows) {
        const key = r.name.trim().toUpperCase()
        const match = byName.get(key)
        if (!match) {
          creates.push({ action: 'create', name: r.name, category: r.category, unit: r.unit, price: r.price, description: r.description })
        } else {
          const willUpdate = (String(match.category || '') !== String(r.category || '')) ||
            (String(match.units || '') !== String(r.unit || '')) ||
            (Number(match.price) !== Number(r.price)) ||
            (String(match.description || '') !== String(r.description || ''))
          if (willUpdate) {
            updates.push({ action: 'update', product_id: match.product_id || match.id, name: r.name, prev: { category: match.category, unit: match.units, price: match.price, description: match.description }, next: { category: r.category, unit: r.unit, price: r.price, description: r.description } })
          }
        }
      }
      setPreview({ creates, updates })
      setStep('review')
    } catch (e) {
      setError(e.message || 'Failed to compute dry-run')
    } finally {
      setIsBusy(false)
    }
  }

  const applyImport = async () => {
    setError('')
    setIsBusy(true)
    try {
      const { data: catsRows } = await supabase
        .from('categories')
        .select('id, name')
        .eq('store_id', storeId)
      const catMap = new Map()
      for (const c of (catsRows || [])) catMap.set(String(c.name || '').toUpperCase(), c.id)

      const ensureCategory = async (name) => {
        const key = String(name || '').trim().toUpperCase()
        if (!key) return null
        if (catMap.has(key)) return catMap.get(key)
        const { data: inserted, error } = await supabase.rpc('add_category', { p_store_id: storeId, p_name: key })
        if (error) throw error
        const newId = inserted?.[0]?.category_id
        const normalized = inserted?.[0]?.name || key
        catMap.set(String(normalized).toUpperCase(), newId)
        return newId
      }

      let created = 0
      let updated = 0

      for (const c of preview.creates) {
        const categoryId = await ensureCategory(c.category)
        const { error } = await supabase.rpc('add_product', {
          p_store_id: storeId,
          p_category_id: categoryId ?? null,
          p_name: c.name,
          p_description: c.description || null,
          p_price: Number(c.price),
          p_unit: c.unit
        })
        if (error) throw error
        created += 1
      }

      for (const u of preview.updates) {
        let categoryId = null
        if (u.next.category && String(u.next.category) !== String(u.prev.category)) {
          categoryId = await ensureCategory(u.next.category)
        } else if (u.next.category) {
          const key = String(u.next.category).trim().toUpperCase()
          categoryId = catMap.get(key) ?? null
        }
        const { error } = await supabase
          .from('products')
          .update({
            name: u.name,
            description: u.next.description || null,
            price: Number(u.next.price),
            units: u.next.unit,
            ...(categoryId ? { category_id: categoryId } : {})
          })
          .eq('store_id', storeId)
          .eq('id', u.product_id)
        if (error) throw error
        updated += 1
      }

      setApplySummary({ created, updated })
      setStep('done')
    } catch (e) {
      setError(e.message || 'Import failed')
    } finally {
      setIsBusy(false)
    }
  }

  if (!open) return null

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.35)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 2000 }}>
      <div style={{ background: 'white', borderRadius: 16, width: 'min(860px, 96vw)', padding: 20, boxShadow: '0 10px 30px rgba(0,0,0,0.15)' }}>
        <h2 style={{ margin: 0, fontSize: '1.25rem', color: '#ff8c00' }}>Import Prices</h2>
        <p style={{ marginTop: 6, color: '#555' }}>Import your pricelist from Excel, TXT, or pasted text.</p>

        {step === 'upload' && (
          <div style={{ marginTop: 16, display: 'grid', gap: 12 }}>
            <div style={{ border: '2px dashed #ffcc80', borderRadius: 14, padding: 22, background: '#fffaf3' }}>
              <div style={{ marginBottom: 10, color: '#7a4a12', fontWeight: 700 }}>Upload from file</div>
              <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                <button onClick={handlePick} disabled={!canImport || isBusy} style={{ padding: '10px 14px', borderRadius: 10, border: '1px solid #ff8c00', background: canImport ? 'white' : '#ffd8ae', color: '#ff8c00', fontWeight: 700, cursor: canImport ? 'pointer' : 'not-allowed' }}>Choose .xlsx</button>
                <button onClick={handlePickTxt} disabled={!canImport || isBusy} style={{ padding: '10px 14px', borderRadius: 10, border: '1px solid #ff8c00', background: canImport ? 'white' : '#ffd8ae', color: '#ff8c00', fontWeight: 700, cursor: canImport ? 'pointer' : 'not-allowed' }}>Choose .txt</button>
              </div>
              {fileName && <div style={{ marginTop: 8, color: '#777' }}>Selected: {fileName}</div>}
              <input ref={fileInputRef} type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" style={{ display: 'none' }} onChange={(e) => handleFile(e.target.files?.[0])} />
              <input ref={txtInputRef} type="file" accept=".txt,text/plain" style={{ display: 'none' }} onChange={(e) => handleTxtFile(e.target.files?.[0])} />
            </div>

            <div style={{ border: '1px solid #eee', borderRadius: 12, padding: 16 }}>
              <div style={{ marginBottom: 10, color: '#7a4a12', fontWeight: 700 }}>Paste raw text</div>
              <textarea
                value={textInput}
                onChange={(e) => { setTextInput(e.target.value); setTextCharCount(e.target.value.length) }}
                placeholder="Paste your note here (supports: PRICELIST header, [CATEGORY], • Name (desc) — ₱Price)"
                rows={8}
                style={{ width: '100%', padding: 10, borderRadius: 10, border: '1px solid #ddd', fontFamily: 'system-ui, -apple-system, sans-serif' }}
              />
              <div style={{ marginTop: 6, color: '#777', fontSize: '0.85rem' }}>{textCharCount} characters</div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 10 }}>
                <button onClick={handleParseText} disabled={!canImport || !textInput.trim() || isBusy} style={{ padding: '10px 16px', borderRadius: 10, border: 'none', background: canImport && textInput.trim() ? 'linear-gradient(135deg, #ffb800 0%, #ff8c00 100%)' : '#ffd8ae', color: 'white', fontWeight: 700, cursor: canImport && textInput.trim() ? 'pointer' : 'not-allowed' }}>Parse Text</button>
              </div>
            </div>

            {error && <div style={{ color: '#d32f2f' }}>{error}</div>}
            {!canImport && <div style={{ color: '#d32f2f' }}>Only owners and managers can import.</div>}
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
              <button onClick={onClose} disabled={isBusy} style={{ padding: '10px 14px', borderRadius: 10, border: '1px solid #ddd', background: 'white', color: '#555' }}>Cancel</button>
              <button onClick={() => setStep('validate')} disabled={!rows.length || !!issues.length || isBusy} style={{ padding: '10px 16px', borderRadius: 10, border: 'none', background: rows.length && !issues.length ? 'linear-gradient(135deg, #ffb800 0%, #ff8c00 100%)' : '#ffd8ae', color: 'white', fontWeight: 700, cursor: rows.length && !issues.length ? 'pointer' : 'not-allowed' }}>Next</button>
            </div>
          </div>
        )}

        {step === 'validate' && (
          <div style={{ marginTop: 12 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 10 }}>
              <div style={{ color: '#777' }}>Total items</div>
              <strong>{rows.length}</strong>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 10 }}>
              <div style={{ color: '#777' }}>Valid rows</div>
              <strong>{rows.length - issues.length}</strong>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 10 }}>
              <div style={{ color: '#777' }}>Rows with issues</div>
              <strong style={{ color: issues.length ? '#b32626' : '#1d7a33' }}>{issues.length}</strong>
            </div>
            {!!issues.length && (
              <div style={{ marginTop: 8, padding: 10, border: '1px solid #f2c7c7', borderRadius: 10, background: '#fff5f5', color: '#b32626' }}>
                {issues.slice(0, 10).map((i, idx) => (
                  <div key={idx}>Row {i.rowIndex}: {i.message}</div>
                ))}
                {issues.length > 10 && <div>…and {issues.length - 10} more</div>}
              </div>
            )}
            <div style={{ maxHeight: 260, overflow: 'auto', border: '1px solid #eee', borderRadius: 10, marginTop: 12 }}>
              <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                <thead>
                  <tr>
                    <th style={{ textAlign: 'left', padding: '8px 10px', borderBottom: '1px solid #f6f6f6' }}>Name</th>
                    <th style={{ textAlign: 'left', padding: '8px 10px', borderBottom: '1px solid #f6f6f6' }}>Description</th>
                    <th style={{ textAlign: 'left', padding: '8px 10px', borderBottom: '1px solid #f6f6f6' }}>Category</th>
                    <th style={{ textAlign: 'left', padding: '8px 10px', borderBottom: '1px solid #f6f6f6' }}>Unit</th>
                    <th style={{ textAlign: 'right', padding: '8px 10px', borderBottom: '1px solid #f6f6f6' }}>Price</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((r, idx) => (
                    <tr key={idx}>
                      <td style={{ padding: '6px 10px' }}>{r.name}</td>
                      <td style={{ padding: '6px 10px', color: '#666' }}>{r.description}</td>
                      <td style={{ padding: '6px 10px' }}>{r.category}</td>
                      <td style={{ padding: '6px 10px' }}>{r.unit}</td>
                      <td style={{ padding: '6px 10px', textAlign: 'right' }}>{Number(r.price).toFixed(2)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {error && <div style={{ marginTop: 10, color: '#d32f2f' }}>{error}</div>}
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, marginTop: 16 }}>
              <button onClick={() => setStep('upload')} disabled={isBusy} style={{ padding: '10px 14px', borderRadius: 10, border: '1px solid #ddd', background: 'white', color: '#555' }}>Back</button>
              <div>
                <button onClick={computeDryRun} disabled={!!issues.length || !rows.length || isBusy} style={{ padding: '10px 16px', borderRadius: 10, border: 'none', background: !issues.length && rows.length ? 'linear-gradient(135deg, #ffb800 0%, #ff8c00 100%)' : '#ffd8ae', color: 'white', fontWeight: 700, cursor: !issues.length && rows.length ? 'pointer' : 'not-allowed' }}>Next</button>
              </div>
            </div>
          </div>
        )}

        {step === 'review' && (
          <div style={{ marginTop: 12 }}>
            <div style={{ display: 'flex', gap: 20, marginBottom: 10 }}>
              <div style={{ padding: 10, border: '1px solid #eee', borderRadius: 10 }}>
                <div style={{ color: '#777' }}>Creates</div>
                <div style={{ fontWeight: 700 }}>{preview.creates.length}</div>
              </div>
              <div style={{ padding: 10, border: '1px solid #eee', borderRadius: 10 }}>
                <div style={{ color: '#777' }}>Updates</div>
                <div style={{ fontWeight: 700 }}>{preview.updates.length}</div>
              </div>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
              <div style={{ border: '1px solid #eee', borderRadius: 10 }}>
                <div style={{ padding: '8px 10px', borderBottom: '1px solid #f6f6f6', fontWeight: 700, color: '#1d7a33' }}>Will Create</div>
                <div style={{ maxHeight: 240, overflow: 'auto' }}>
                  {preview.creates.map((c, idx) => (
                    <div key={idx} style={{ padding: '6px 10px', borderBottom: '1px solid #f9f9f9' }}>
                      <div style={{ fontWeight: 600 }}>{c.name}</div>
                      <div style={{ color: '#777' }}>{c.description}</div>
                      <div style={{ display: 'flex', gap: 10, color: '#555' }}>
                        <span>{c.category}</span>
                        <span>•</span>
                        <span>{c.unit}</span>
                        <span>•</span>
                        <span>₱{Number(c.price).toFixed(2)}</span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
              <div style={{ border: '1px solid #eee', borderRadius: 10 }}>
                <div style={{ padding: '8px 10px', borderBottom: '1px solid #f6f6f6', fontWeight: 700, color: '#7a4a12' }}>Will Update</div>
                <div style={{ maxHeight: 240, overflow: 'auto' }}>
                  {preview.updates.map((u, idx) => (
                    <div key={idx} style={{ padding: '6px 10px', borderBottom: '1px solid #f9f9f9' }}>
                      <div style={{ fontWeight: 600 }}>{u.name}</div>
                      <div style={{ display: 'flex', gap: 6 }}>
                        <span style={{ color: '#777' }}>from</span>
                        <span style={{ color: '#999' }}>{u.prev.category} / {u.prev.unit} / ₱{Number(u.prev.price).toFixed(2)}</span>
                        <span style={{ color: '#777' }}>to</span>
                        <span style={{ color: '#555' }}>{u.next.category} / {u.next.unit} / ₱{Number(u.next.price).toFixed(2)}</span>
                      </div>
                      {String(u.prev.description || '') !== String(u.next.description || '') && (
                        <div style={{ color: '#777' }}>description change</div>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            </div>
            {error && <div style={{ marginTop: 10, color: '#d32f2f' }}>{error}</div>}
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, marginTop: 16 }}>
              <button onClick={() => setStep('validate')} disabled={isBusy} style={{ padding: '10px 14px', borderRadius: 10, border: '1px solid #ddd', background: 'white', color: '#555' }}>Back</button>
              <button onClick={applyImport} disabled={isBusy || (!preview.creates.length && !preview.updates.length)} style={{ padding: '10px 16px', borderRadius: 10, border: 'none', background: (preview.creates.length || preview.updates.length) ? 'linear-gradient(135deg, #ffb800 0%, #ff8c00 100%)' : '#ffd8ae', color: 'white', fontWeight: 700, cursor: (preview.creates.length || preview.updates.length) ? 'pointer' : 'not-allowed' }}>Confirm & Import</button>
            </div>
          </div>
        )}

        {step === 'done' && (
          <div style={{ marginTop: 12 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, color: '#1d7a33' }}>
              <SuccessIcon />
              <strong>Import completed successfully!</strong>
            </div>
            <div style={{ marginTop: 8, color: '#555' }}>Created {applySummary.created} items, updated {applySummary.updated} items.</div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
              <button onClick={onClose} style={{ padding: '10px 14px', borderRadius: 10, border: '1px solid #ddd', background: 'white', color: '#555' }}>Close</button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

function SuccessIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="#1d7a33">
      <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 15l-5-5 1.41-1.41L11 14.17l7.59-7.59L20 8l-9 9z"/>
    </svg>
  )
}

function parseNoteText(text) {
  const lines = String(text || '').split(/\r?\n/)
  let category = ''
  const parsed = []
  const issues = []
  const seen = new Set()

  const isHeader = (s) => /^\s*PRICELIST:?\s*$/i.test(s)
  const isDateLine = (s) => /^\s*\d{2}\/\d{2}\/\d{4}\s*$/.test(s)
  const catMatch = (s) => s.match(/^\s*\[(.+?)\]\s*$/)
  const splitItem = (s) => {
    const norm = s.replace(/^\s*[•\-]\s*/, '')
    const parts = norm.split(/\s+[—-]\s+/)
    if (parts.length < 2) return null
    const left = parts[0].trim()
    const right = parts.slice(1).join(' — ').trim()
    const m = left.match(/^(.+?)(?:\s*\((.*?)\))?$/)
    const name = (m?.[1] || '').trim()
    const desc = (m?.[2] || '').trim()
    const priceStr = right.replace(/₱/g, '').replace(/,/g, '').trim()
    const price = Number(priceStr)
    if (!name || Number.isNaN(price)) return null
    return { name, desc, price }
  }

  for (const raw of lines) {
    const s = String(raw || '').trim()
    if (!s) continue
    if (isHeader(s)) continue
    if (isDateLine(s)) continue
    const cm = catMatch(s)
    if (cm) { category = cm[1].trim(); continue }
    const item = splitItem(s)
    if (item) {
      const cat = category || 'PRICELIST'
      const key = `${cat}||${item.name.toUpperCase()}`
      if (seen.has(key)) {
        issues.push({ rowIndex: parsed.length + 1, message: 'Duplicate item under same category' })
      } else {
        seen.add(key)
      }
      parsed.push({ rowIndex: parsed.length + 1, category: cat, name: item.name, description: item.desc, unit: 'pc', price: item.price })
      continue
    }
    issues.push({ rowIndex: parsed.length + 1, message: 'Unrecognized line format' })
  }

  for (const p of parsed) {
    if (!p.name) issues.push({ rowIndex: p.rowIndex, message: 'Name is required' })
    if (!p.category) issues.push({ rowIndex: p.rowIndex, message: 'Category is required' })
    if (Number.isNaN(p.price)) issues.push({ rowIndex: p.rowIndex, message: 'Price must be numeric' })
  }

  return { parsed, issues }
}
