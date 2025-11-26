import React, { useEffect, useMemo, useRef, useState } from 'react'
import { supabase } from '../../config/supabaseClient'

export default function ImportPricesModal({ open, onClose, storeId, storeName, role }) {
  const [step, setStep] = useState('upload')
  const [error, setError] = useState('')
  const [isBusy, setIsBusy] = useState(false)

  const [fileName, setFileName] = useState('')
  const [rows, setRows] = useState([])
  const [issues, setIssues] = useState([]) // errors only
  const [warnings, setWarnings] = useState([]) // non-blocking issues (e.g., duplicates)

  const [preview, setPreview] = useState({ creates: [], updates: [] })
  const [applySummary, setApplySummary] = useState({ created: 0, updated: 0 })

  const fileInputRef = useRef(null)
  const [rawText, setRawText] = useState('')
  const [textCharCount, setTextCharCount] = useState(0)

  useEffect(() => {
    if (!open) {
      setStep('upload')
      setError('')
      setIsBusy(false)
      setFileName('')
      setRows([])
      setIssues([])
      setWarnings([])
      setPreview({ creates: [], updates: [] })
      setApplySummary({ created: 0, updated: 0 })
      setRawText('')
      setTextCharCount(0)
    }
  }, [open])

  const canImport = useMemo(() => role === 'owner' || role === 'manager', [role])

  const handlePick = () => fileInputRef.current?.click()

  const handleFile = async (file) => {
    if (!file) return
    setError('')
    setIsBusy(true)
    setFileName(file.name)
    try {
      // Primary parser: exceljs
      const [{ default: ExcelJS }] = await Promise.all([
        import('exceljs')
      ])
      const wb = new ExcelJS.Workbook()
      const buf = await file.arrayBuffer()
      let parsed = []
      try {
        await wb.xlsx.load(buf)
        const ws = wb.worksheets[0]
        if (!ws) throw new Error('Worksheet not found')

        const expected = ['Category', 'Name', 'Description', 'Unit', 'Price']
        let headerRowIndex = -1
        for (let r = 1; r <= Math.min(ws.rowCount, 20); r++) {
          const vals = (ws.getRow(r).values || []).slice(1).map(v => String(v || '').trim())
          const head = vals.slice(0, 5)
          const match = head.length >= 5 && head.every((v, i) => v.toLowerCase() === expected[i].toLowerCase())
          if (match) { headerRowIndex = r; break }
        }
        if (headerRowIndex === -1) throw new Error('headers_not_found')

        for (let r = headerRowIndex + 1; r <= ws.rowCount; r++) {
          const row = ws.getRow(r)
          const vals = (row.values || []).slice(1)
          const category = String(vals[0] ?? '').trim()
          const name = String(vals[1] ?? '').trim()
          const description = String(vals[2] ?? '').trim()
          const unit = String(vals[3] ?? '').trim()
          const priceRaw = vals[4]
          const price = typeof priceRaw === 'number' ? priceRaw : Number(String(priceRaw ?? '').replace(/[^0-9.,\-]/g, '').replace(/,/g, ''))
          const emptyRow = !category && !name && !description && !unit && (price === 0 || Number.isNaN(price))
          if (emptyRow) continue
          parsed.push({ rowIndex: r, category, name, description, unit, price })
        }
      } catch (excelErr) {
        // Fallback parser: sheetjs/xlsx — more tolerant to XML differences
        const XLSXMod = await import('xlsx')
        const XLSX = XLSXMod.read ? XLSXMod : (XLSXMod.default || XLSXMod)
        if (!XLSX || typeof XLSX.read !== 'function') {
          throw new Error('xlsx_read_unavailable')
        }
        const wb2 = XLSX.read(buf, { type: 'array' })
        const sheetName = wb2.SheetNames[0]
        const sheet = wb2.Sheets[sheetName]
        const rowsArr = XLSX.utils.sheet_to_json(sheet, { header: 1, blankrows: false })
        const aliases = {
          category: ['category', 'cat'],
          name: ['name', 'item', 'item name', 'product'],
          description: ['description', 'desc', 'details'],
          unit: ['unit', 'units', 'uom'],
          price: ['price', 'amount', 'cost']
        }
        const norm = (v) => String(v || '').trim().toLowerCase()
        let headerRowIndex = -1
        let col = { category: -1, name: -1, description: -1, unit: -1, price: -1 }
        for (let r = 0; r < Math.min(rowsArr.length, 25); r++) {
          const row = rowsArr[r] || []
          const idx = { category: -1, name: -1, description: -1, unit: -1, price: -1 }
          for (let c = 0; c < row.length; c++) {
            const cell = norm(row[c])
            for (const key of Object.keys(aliases)) {
              if (aliases[key].some(a => cell === a)) idx[key] = c
            }
          }
          const gotAll = Object.values(idx).every(v => v >= 0)
          if (gotAll) { headerRowIndex = r; col = idx; break }
        }
        if (headerRowIndex === -1) throw new Error('headers_not_found')

        parsed = []
        for (let r = headerRowIndex + 1; r < rowsArr.length; r++) {
          const row = rowsArr[r] || []
          const category = String(row[col.category] ?? '').trim()
          const name = String(row[col.name] ?? '').trim()
          const description = String(row[col.description] ?? '').trim()
          const unit = String(row[col.unit] ?? '').trim()
          const priceCell = row[col.price]
          const price = typeof priceCell === 'number' ? priceCell : Number(String(priceCell ?? '').replace(/[^0-9.,\-]/g, '').replace(/,/g, ''))
          const emptyRow = !category && !name && !description && !unit && (price === 0 || Number.isNaN(price))
          if (emptyRow) continue
          parsed.push({ rowIndex: r + 1, category, name, description, unit, price })
        }
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
      // Friendly error messaging
      if (String(e.message || '').includes('unexpected close tag')) {
        setError('This Excel file has a format mobile Excel sometimes produces. I added support, but if you still see this, please re-export or paste the text instead.')
      } else if (String(e.message || '') === 'headers_not_found') {
        setError('Invalid template: could not find headers. Expected columns: Category, Name, Description, Unit, Price.')
      } else if (String(e.message || '') === 'xlsx_read_unavailable') {
        setError('Excel import module failed to load. Please refresh the page and try again, or paste the text format as a fallback.')
      } else {
        setError(e.message || 'Failed to read file')
      }
    } finally {
      setIsBusy(false)
    }
  }

  // ===== RAW TEXT PARSER =====
  const handleParseText = () => {
    try {
      setError('')
      const text = String(rawText || '')
      const lines = text.split(/\r?\n/)
      const cleaned = []
      for (let i = 0; i < lines.length; i++) {
        let ln = lines[i]
        if (!ln) { cleaned.push(''); continue }
        // Normalize spacing
        ln = ln.replace(/\u00A0/g, ' ')
        // Ignore headers and metadata
        const ignorePatterns = [/^\s*PRICELIST:?\s*$/i, /Shared via Presyohan/i, /^\s*\d{1,2}\s*\/\s*\d{1,2}\s*\/\s*\d{2,4}\s*$/]
        if (ignorePatterns.some((re) => re.test(ln))) continue
        // Ignore lines that look like a store header (Store — Branch), but keep item lines with prices
        const looksLikeHeader = /^\s*[^-•*].*—.*$/.test(ln) && !/[₱0-9]/.test(ln) && !/[\[\]]/.test(ln)
        if (looksLikeHeader) continue
        cleaned.push(ln)
      }

      const categoryRegex = /^\s*\[([^\]]+)\]\s*$/
      const simpleCategoryRegex = /^\s*([^\-•*].*)$/ // line without bullet/dash; will validate that it also doesn't contain price
      const itemBulletRegex = /^\s*[-•*]\s*/
      // Updated regex to allow optional pipe or space for unit
      const priceLineRegex = /[:=\-—>]?:\s*₱?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:(?:\|)?\s*(.+))?\s*$/
      const inlineItemRegex = new RegExp(
        String.raw`^\s*[-•*]\s*` + // bullet
        String.raw`([^\(\|\n]+?)` + // name (lazy, until '(' or '|' or end)
        String.raw`(?:\s*\(([^\)]*)\))?` + // optional (desc)
        String.raw`\s*(?:[—\-=>:]?\s*)?` + // optional separator
        String.raw`₱?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)` + // price numeric (commas supported)
        String.raw`(?:\s*(?:\|)?\s*(.+))?\s*$`, // unit (optional pipe or space)
        'i'
      )

      const parsed = []
      const errs = []
      const warns = []
      let currentCategory = ''

      for (let i = 0; i < cleaned.length; i++) {
        const line = (cleaned[i] || '').trim()
        if (!line) continue

        // Category detection: [Category]
        let mCat = line.match(categoryRegex)
        if (mCat) {
          currentCategory = String(mCat[1] || '').trim().toUpperCase()
          continue
        }
        // Category detection: plain line without bullet or price; must be alone
        if (!itemBulletRegex.test(line)) {
          const hasPriceNumber = /[0-9]+(?:\.[0-9]{1,2})?/.test(line)
          const looksLikeCategory = !hasPriceNumber && !/[:=\-—>|]/.test(line)
          if (looksLikeCategory) {
            currentCategory = String(line).trim().toUpperCase()
            continue
          }
        }

        // Item: inline single-line
        let mItem = line.match(inlineItemRegex)
        if (mItem) {
          if (!currentCategory) {
            warns.push({ rowIndex: i + 1, message: 'Line skipped — item has no category context.' })
            continue
          }
          const name = String(mItem[1] || '').trim()
          const desc = String(mItem[2] || '').trim()
          const priceStr = String(mItem[3] || '').trim()
          const unitRaw = (mItem[4] || '').trim()
          const priceClean = priceStr.replace(/,/g, '')
          const priceNum = Number(priceClean)
          if (!/^[0-9]+(\.[0-9]{1,2})?$/.test(priceClean)) {
            warns.push({ rowIndex: i + 1, message: 'Line skipped — price is not a valid number.' })
            continue
          }
          parsed.push({ rowIndex: i + 1, category: currentCategory, name, description: desc, unit: unitRaw || '1pc', price: priceNum })
          continue
        }

        // Item: multi-line (name on line i, price on line i+1)
        if (itemBulletRegex.test(line)) {
          // Extract name and optional (desc) from first line
          const stripped = line.replace(itemBulletRegex, '').trim()
          const nameMatch = /^([^\(\|]+?)(?:\s*\(([^\)]*)\))?\s*$/.exec(stripped)
          const name = nameMatch ? String(nameMatch[1] || '').trim() : stripped
          const desc = nameMatch ? String(nameMatch[2] || '').trim() : ''

          // Look ahead for price line
          const nextLine = String(cleaned[i + 1] || '').trim()
          const priceMatch = nextLine.match(priceLineRegex)
          if (priceMatch) {
            if (!currentCategory) {
              warns.push({ rowIndex: i + 1, message: 'Line skipped — item has no category context.' })
              continue
            }
            const priceNum = Number(String(priceMatch[1]).replace(/,/g, ''))
            const unitRaw = String(priceMatch[2] || '').trim()
            parsed.push({ rowIndex: i + 1, category: currentCategory, name, description: desc, unit: unitRaw || '1pc', price: priceNum })
            i += 1 // consume next line
            continue
          }
        }

        // If line looks like a price-only continuation, attempt to merge with previous item
        const priceOnly = line.match(priceLineRegex)
        if (priceOnly && parsed.length > 0) {
          // Ambiguous continuation; skip with friendly message
          warns.push({ rowIndex: i + 1, message: 'Line skipped — ambiguous price without item name.' })
          continue
        }

        // Unrecognized structured line: mark skipped for clarity
        warns.push({ rowIndex: i + 1, message: 'Line skipped — unrecognized format.' })
      }

      // Deduplicate by category+name+description+unit (case-insensitive). Keep last occurrence of exact duplicates.
      const seen = new Map()
      const deduped = []
      for (let idx = 0; idx < parsed.length; idx++) {
        const p = parsed[idx]
        const key = `${String(p.category).toUpperCase()}::${String(p.name).toUpperCase()}::${String(p.description||'').toUpperCase()}::${String(p.unit||'').toUpperCase()}`
        if (seen.has(key)) {
          const labelDesc = p.description ? ` (${p.description})` : ''
          const labelUnit = p.unit ? ` / ${p.unit}` : ''
          warns.push({ rowIndex: p.rowIndex, message: `Duplicate item — kept latest for “${p.name}${labelDesc}${labelUnit}”.` })
          // Replace previous with this one (latest wins)
          const prevIndex = seen.get(key)
          deduped[prevIndex] = p
          seen.set(key, prevIndex)
        } else {
          seen.set(key, deduped.length)
          deduped.push(p)
        }
      }

      setRows(deduped)
      setIssues(errs)
      setWarnings(warns)
      setStep('validate')
    } catch (e) {
      setError(e.message || 'Failed to parse text')
    }
  }

  const computeDryRun = async () => {
    setError('')
    setIsBusy(true)
    try {
      const { data, error: rpcErr } = await supabase.rpc('get_store_products', { p_store_id: storeId })
      if (rpcErr) throw rpcErr
      const existing = Array.isArray(data) ? data : []
      const bySignature = new Map()
      for (const e of existing) {
        const sig = `${String(e.name||'').trim().toUpperCase()}::${String(e.description||'').trim().toUpperCase()}::${String(e.units||'').trim().toUpperCase()}`
        // Keep the first occurrence for a given signature; multiple matches mean data already has duplicates
        if (!bySignature.has(sig)) bySignature.set(sig, e)
      }
      const creates = []
      const updates = []
      for (const r of rows) {
        const sig = `${String(r.name).trim().toUpperCase()}::${String(r.description||'').trim().toUpperCase()}::${String(r.unit||'').trim().toUpperCase()}`
        const match = bySignature.get(sig)
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
            unit: u.next.unit,
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
        <p style={{ marginTop: 6, color: '#555' }}>Follow the steps to import your Excel pricelist. Only .xlsx exported by Convert are accepted.</p>

        {step === 'upload' && (
          <div style={{ marginTop: 16 }}>
            <div style={{ border: '2px dashed #ffcc80', borderRadius: 14, padding: 22, textAlign: 'center', background: '#fffaf3' }}>
              <div style={{ marginBottom: 8, color: '#7a4a12' }}>Drop your file here or choose a file</div>
              <div style={{ display: 'flex', gap: 10, justifyContent: 'center' }}>
                <button onClick={handlePick} disabled={!canImport || isBusy} style={{ padding: '10px 14px', borderRadius: 10, border: '1px solid #ff8c00', background: canImport ? 'white' : '#ffd8ae', color: '#ff8c00', fontWeight: 700, cursor: canImport ? 'pointer' : 'not-allowed' }}>Choose .xlsx</button>
              </div>
              {fileName && <div style={{ marginTop: 8, color: '#777' }}>Selected: {fileName}</div>}
              <input ref={fileInputRef} type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" style={{ display: 'none' }} onChange={(e) => handleFile(e.target.files?.[0])} />
            </div>

            <div style={{ border: '1px solid #eee', borderRadius: 12, padding: 16 }}>
              <div style={{ marginBottom: 10, color: '#7a4a12', fontWeight: 700 }}>Paste raw text</div>
              <textarea
                value={rawText}
                onChange={(e) => { setRawText(e.target.value); setTextCharCount(e.target.value.length) }}
                placeholder="type or paste your note here...

valid format:

[Category name]
- Item name (Item Description) —  ₱999.99 | unit
  or
- Item name 22 can"
                rows={8}
                style={{  width: '100%', padding: 10, borderRadius: 10, border: '1px solid #ddd', fontFamily: 'system-ui, -apple-system, sans-serif' }}
              />
              <div style={{ marginTop: 6, color: '#777', fontSize: '0.85rem' }}>{textCharCount} characters</div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 10 }}>
                <button onClick={handleParseText} disabled={!canImport || !rawText.trim() || isBusy} style={{ padding: '10px 16px', borderRadius: 10, border: 'none', background: canImport && rawText.trim() ? 'linear-gradient(135deg, #ffb800 0%, #ff8c00 100%)' : '#ffd8ae', color: 'white', fontWeight: 700, cursor: canImport && rawText.trim() ? 'pointer' : 'not-allowed' }}>Parse Text</button>
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
              <strong>{rows.length}</strong>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 10 }}>
              <div style={{ color: '#777' }}>Skipped lines</div>
              <strong style={{ color: warnings.length ? '#7a4a12' : '#1d7a33' }}>{warnings.length}</strong>
            </div>
            {!!warnings.length && (
              <div style={{ marginTop: 8, padding: 10, border: '1px solid #ffe6b3', borderRadius: 10, background: '#fff9e6', color: '#7a4a12' }}>
                <div style={{ fontWeight: 700, marginBottom: 6 }}>Some lines were skipped</div>
                {warnings.slice(0, 10).map((w, idx) => (
                  <div key={idx}>Row {w.rowIndex}: {w.message}</div>
                ))}
                {warnings.length > 10 && <div>…and {warnings.length - 10} more</div>}
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
            {!!warnings.length && (
              <div style={{ marginTop: 8, padding: 10, border: '1px solid #ffe6b3', borderRadius: 10, background: '#fff9e6', color: '#7a4a12' }}>
                <div style={{ fontWeight: 700, marginBottom: 6 }}>Warnings (not blocking)</div>
                {warnings.slice(0, 10).map((w, idx) => (
                  <div key={idx}>Row {w.rowIndex}: {w.message}</div>
                ))}
                {warnings.length > 10 && <div>…and {warnings.length - 10} more</div>}
              </div>
            )}
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, marginTop: 16 }}>
              <button onClick={() => setStep('upload')} disabled={isBusy} style={{ padding: '10px 14px', borderRadius: 10, border: '1px solid #ddd', background: 'white', color: '#555' }}>Back</button>
              <div>
                <button onClick={computeDryRun} disabled={!rows.length || isBusy} style={{ padding: '10px 16px', borderRadius: 10, border: 'none', background: rows.length ? 'linear-gradient(135deg, #ffb800 0%, #ff8c00 100%)' : '#ffd8ae', color: 'white', fontWeight: 700, cursor: rows.length ? 'pointer' : 'not-allowed' }}>Next</button>
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