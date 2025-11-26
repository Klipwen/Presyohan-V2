import React, { useEffect, useMemo, useState } from 'react'
import { supabase } from '../../config/supabaseClient'

// ExportPricelistModal
// Provides a user-friendly, professional modal to export store products to Excel (.xlsx)
// - Shows row count preview
// - Confirms export
// - Generates a formatted Excel file client-side using exceljs
// Props:
//   open: boolean
//   onClose: function
//   storeId: string (uuid)
//   storeName: string
//   branch: string
//   role: 'owner' | 'manager' | 'employee' | null
export default function ExportPricelistModal({ open, onClose, storeId, storeName, branch, role }) {
  const [isLoadingPreview, setIsLoadingPreview] = useState(false)
  const [rowCount, setRowCount] = useState(0)
  const [isExporting, setIsExporting] = useState(false)
  const [errorText, setErrorText] = useState('')
  const [products, setProducts] = useState([])
  const [selectedExport, setSelectedExport] = useState('excel')
  const [noteText, setNoteText] = useState('')
  const [noteCharCount, setNoteCharCount] = useState(0)
  const [noteFeedback, setNoteFeedback] = useState(null)
  const [isCopyingNote, setIsCopyingNote] = useState(false)
  const [isSharingNote, setIsSharingNote] = useState(false)

  const canExport = useMemo(() => role === 'owner' || role === 'manager', [role])
  const shareSupported = typeof window !== 'undefined' && typeof navigator !== 'undefined' && typeof navigator.share === 'function'

  useEffect(() => {
    const fetchPreview = async () => {
      if (!open || !storeId) return
      setErrorText('')
      setIsLoadingPreview(true)
      try {
        const { data, error } = await supabase.rpc('get_store_products', {
          p_store_id: storeId,
          p_category_filter: 'PRICELIST',
          p_search_query: null
        })
        if (error) throw error
        const rows = Array.isArray(data) ? data : []
        setRowCount(rows.length)
        setProducts(rows)
      } catch (e) {
        setErrorText(e.message || 'Failed to load preview.')
      } finally {
        setIsLoadingPreview(false)
      }
    }
    fetchPreview()
  }, [open, storeId])

  useEffect(() => {
    if (!open) {
      setSelectedExport('excel')
      setNoteFeedback(null)
      setProducts([])
      setNoteText('')
      setNoteCharCount(0)
    }
  }, [open])

  useEffect(() => {
    const text = buildNoteText(products, storeName, branch)
    setNoteText(text)
    setNoteCharCount(text.length)
  }, [products, storeName, branch])

  const formatFilename = () => {
    const slug = `${(storeName || 'store').trim()}_${(branch || 'main').trim()}`
      .toLowerCase()
      .replace(/\s+/g, '-')
      .replace(/[^a-z0-9-]/g, '')
    const pad = (n) => String(n).padStart(2, '0')
    const d = new Date()
    const ts = `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}_${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`
    return `presyohan_${slug}_pricelist_${ts}.xlsx`
  }

  const handleExcelExport = async () => {
    if (!storeId || !canExport) return
    setErrorText('')
    setIsExporting(true)
    try {
      const { data, error } = await supabase.rpc('get_store_products', {
        p_store_id: storeId,
        p_category_filter: 'PRICELIST',
        p_search_query: null
      })
      if (error) throw error
      const rows = (Array.isArray(data) ? data : [])
      if (!rows.length) {
        setErrorText('No products to export.')
        setIsExporting(false)
        return
      }

      // Sort by category then name for professional ordering
      rows.sort((a, b) => {
        const ca = (a.category || '').toLowerCase()
        const cb = (b.category || '').toLowerCase()
        if (ca < cb) return -1
        if (ca > cb) return 1
        const na = (a.name || '').toLowerCase()
        const nb = (b.name || '').toLowerCase()
        if (na < nb) return -1
        if (na > nb) return 1
        return 0
      })

      // Lazy-load exceljs and file-saver to keep initial bundle light
      const [{ default: ExcelJS }, { saveAs }] = await Promise.all([
        import('exceljs'),
        import('file-saver')
      ])

      const wb = new ExcelJS.Workbook()
      const ws = wb.addWorksheet('Pricelist')

      // Title & store info
      ws.addRow(['Presyohan'])
      ws.getRow(1).font = { bold: true, size: 16 }
      ws.addRow([`Store: ${storeName || ''} — Branch: ${branch || ''}`])

      // Exported by / at
      let exporterName = ''
      try {
        const { data: sessionRes } = await supabase.auth.getSession()
        exporterName = sessionRes?.session?.user?.email || 'User'
      } catch {
        exporterName = 'User'
      }
      const exportedAt = new Date().toLocaleString()
      ws.addRow([`Exported by: ${exporterName}    |    Exported at: ${exportedAt}`])

      ws.addRow(['']) // blank line

      // Headers
      const header = ['Category', 'Name', 'Description', 'Unit', 'Price']
      ws.addRow(header)
      const headerRow = ws.getRow(ws.rowCount)
      headerRow.font = { bold: true }
      ws.views = [{ state: 'frozen', ySplit: 5 }]

      // Column widths
      ws.columns = [
        { key: 'category', width: 20 },
        { key: 'name', width: 28 },
        { key: 'description', width: 40 },
        { key: 'units', width: 12 },
        { key: 'price', width: 14 }
      ]

      // Data rows
      rows.forEach(r => {
        const priceNum = typeof r.price === 'number' ? r.price : Number(r.price || 0)
        const row = ws.addRow([
          r.category || '',
          r.name || '',
          r.description || '',
          r.units || '',
          priceNum
        ])
        row.getCell(5).numFmt = '"₱"#,##0.00'
        row.getCell(3).alignment = { wrapText: true }
      })

      // Generate and download
      const buffer = await wb.xlsx.writeBuffer()
      const blob = new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
      saveAs(blob, formatFilename())

      setIsExporting(false)
      onClose()
      // Optional: You can integrate a toast system here if available
    } catch (e) {
      setErrorText(e.message || 'Failed to export.')
      setIsExporting(false)
    }
  }

  const handleCopyNote = async () => {
    if (!noteText) return
    setNoteFeedback(null)
    setIsCopyingNote(true)
    try {
      if (navigator?.clipboard?.writeText) {
        await navigator.clipboard.writeText(noteText)
      } else {
        const textarea = document.createElement('textarea')
        textarea.value = noteText
        textarea.style.position = 'fixed'
        textarea.style.opacity = '0'
        document.body.appendChild(textarea)
        textarea.focus()
        textarea.select()
        document.execCommand('copy')
        document.body.removeChild(textarea)
      }
      setNoteFeedback({ type: 'success', text: 'Note copied to clipboard!' })
    } catch {
      setNoteFeedback({ type: 'error', text: 'Copy failed. Please try again.' })
    } finally {
      setIsCopyingNote(false)
    }
  }

  const handleShareNote = async () => {
    if (!shareSupported || !noteText) {
      setNoteFeedback({ type: 'error', text: 'Sharing is not supported on this browser. Please copy instead.' })
      return
    }
    setNoteFeedback(null)
    setIsSharingNote(true)
    try {
      await navigator.share({
        title: 'Presyohan Pricelist',
        text: noteText
      })
      setNoteFeedback({ type: 'success', text: 'Shared successfully! Check Google Keep to finalize.' })
    } catch (e) {
      if (e?.name !== 'AbortError') {
        setNoteFeedback({ type: 'error', text: 'Share failed. Please copy and paste manually.' })
      }
    } finally {
      setIsSharingNote(false)
    }
  }

  if (!open) return null

  return (
    <div style={{
      position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.35)', display: 'flex',
      alignItems: 'center', justifyContent: 'center', zIndex: 2000
    }}>
      <div style={{
        background: 'white', borderRadius: 16, width: 'min(520px, 94vw)', padding: 24,
        boxShadow: '0 10px 30px rgba(0,0,0,0.15)'
      }}>
        <h2 style={{ margin: 0, fontSize: '1.25rem', color: '#ff8c00' }}>Convert Pricelist</h2>
        <p style={{ marginTop: 8, color: '#555' }}>Choose whether you want an Excel file or a Google Keep-ready note.</p>

        <div style={{ marginTop: 16 }}>
          <div style={{ fontWeight: 600, color: '#7a4a12', marginBottom: 10 }}>What would you like to convert?</div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
            <OptionCard
              label="Export as Excel"
              description="Download a formatted .xlsx pricelist."
              checked={selectedExport === 'excel'}
              onSelect={() => setSelectedExport('excel')}
            />
            <OptionCard
              label="Export as Notes"
              description="Preview a clean note for Google Keep."
              checked={selectedExport === 'note'}
              onSelect={() => setSelectedExport('note')}
            />
          </div>
        </div>

        {selectedExport === 'excel' && (
          <div style={{
            marginTop: 16, padding: 12, border: '1px solid #eee', borderRadius: 12,
            background: '#fafafa'
          }}>
            {isLoadingPreview ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <Spinner />
                <span style={{ color: '#777' }}>Loading preview…</span>
              </div>
            ) : (
              <>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
                  <span style={{ color: '#777' }}>Rows to export</span>
                  <strong>{rowCount}</strong>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
                  <span style={{ color: '#777' }}>Scope</span>
                  <strong>All products</strong>
                </div>
                <div style={{ color: '#777' }}>Columns: Category, Name, Description, Unit, Price</div>
              </>
            )}
          </div>
        )}

        {selectedExport === 'note' && (
          <div style={{ marginTop: 18 }}>
            <div style={{ fontWeight: 700, color: '#ff8c00', marginBottom: 8 }}>Step 1 — Preview</div>
            <div style={{
              maxHeight: 240,
              padding: 14,
              borderRadius: 12,
              border: '1px solid #f5d3a7',
              background: '#fffdf7',
              fontFamily: "'Inter', 'Segoe UI', sans-serif",
              fontSize: '0.9rem',
              color: '#5b4631',
              overflowY: 'auto',
              whiteSpace: 'pre-wrap'
            }}>
              {isLoadingPreview ? (
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <Spinner />
                  <span style={{ color: '#7a7a7a' }}>Preparing note…</span>
                </div>
              ) : (
                noteText || 'No products available. Add items to generate a note.'
              )}
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 8, color: '#7a4a12', fontSize: '0.85rem' }}>
              <span>{rowCount} items formatted</span>
              <span>{noteCharCount} characters</span>
            </div>
            <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginTop: 14 }}>
              <button
                onClick={handleCopyNote}
                disabled={!noteText || isCopyingNote}
                style={{
                  padding: '10px 16px',
                  borderRadius: 12,
                  border: 'none',
                  background: 'linear-gradient(135deg, #ffb800 0%, #ff8c00 100%)',
                  color: 'white',
                  fontWeight: 700,
                  cursor: noteText && !isCopyingNote ? 'pointer' : 'not-allowed',
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 8
                }}
              >
                {isCopyingNote && <Spinner small />}
                {isCopyingNote ? 'Copying…' : 'Copy'}
              </button>
              <button
                onClick={handleShareNote}
                disabled={!shareSupported || !noteText || isSharingNote}
                style={{
                  padding: '10px 16px',
                  borderRadius: 12,
                  border: shareSupported ? '1px solid #ff8c00' : '1px solid #ddd',
                  background: shareSupported ? 'white' : '#f7f7f7',
                  color: shareSupported ? '#ff8c00' : '#999',
                  fontWeight: 700,
                  cursor: shareSupported && noteText && !isSharingNote ? 'pointer' : 'not-allowed',
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 8
                }}
              >
                {isSharingNote && <Spinner small />}
                {shareSupported ? (isSharingNote ? 'Sharing…' : 'Share') : 'Share not supported'}
              </button>
            </div>
            {noteFeedback?.text && (
              <div style={{
                marginTop: 10,
                color: noteFeedback.type === 'success' ? '#2e7d32' : '#c62828',
                fontSize: '0.85rem'
              }}>
                {noteFeedback.text}
              </div>
            )}
            {!shareSupported && (
              <div style={{
                marginTop: 18,
                padding: 14,
                borderRadius: 12,
                border: '1px dashed #f0ba73',
                background: '#fff9f0',
                color: '#7a4a12'
              }}>
                <div style={{ fontWeight: 700, marginBottom: 6 }}>Step 2 — Sharing Guide</div>
                <ol style={{ margin: 0, paddingLeft: 18, display: 'flex', flexDirection: 'column', gap: 4 }}>
                  <li>Tap <strong>Copy Note</strong>.</li>
                  <li>Open Google Keep.</li>
                  <li>Paste the note, give it a title, then save.</li>
                </ol>
                <div style={{ marginTop: 8, fontSize: '0.85rem' }}>Tip: You can also paste the note into chat apps, email, or printouts.</div>
              </div>
            )}
          </div>
        )}

        {errorText && (
          <div style={{ marginTop: 10, color: '#d32f2f' }}>{errorText}</div>
        )}

        {selectedExport === 'excel' && !canExport && (
          <div style={{ marginTop: 10, color: '#d32f2f' }}>Only owners and managers can export.</div>
        )}

        <div style={{ display: 'flex', gap: 10, marginTop: 20, justifyContent: selectedExport === 'note' ? 'flex-start' : 'flex-end', flexWrap: 'wrap' }}>
          <button
            onClick={onClose}
            disabled={isExporting}
            style={{
              padding: '10px 14px', borderRadius: 12, border: '1px solid #ddd', background: 'white', color: '#555',
              cursor: 'pointer'
            }}
          >Close</button>
          {selectedExport === 'excel' && (
            <button
              onClick={handleExcelExport}
              disabled={!canExport || isLoadingPreview || isExporting || rowCount === 0}
              style={{
                padding: '10px 16px', borderRadius: 12, border: 'none',
                background: 'linear-gradient(135deg, #ffb800 0%, #ff8c00 100%)', color: 'white', fontWeight: 700,
                cursor: canExport && !isLoadingPreview && !isExporting && rowCount > 0 ? 'pointer' : 'not-allowed',
                display: 'inline-flex', alignItems: 'center', gap: 8
              }}
              title={rowCount === 0 ? 'No products to export' : 'Confirm Export'}
            >
              {isExporting && <Spinner small />}
              {isExporting ? 'Preparing…' : 'Export Excel'}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}

function OptionCard({ label, description, checked, onSelect }) {
  return (
    <label
      onClick={onSelect}
      style={{
        border: checked ? '2px solid #ff8c00' : '1px solid #eee',
        borderRadius: 14,
        padding: 14,
        background: checked ? '#fff7ec' : 'white',
        cursor: 'pointer',
        display: 'flex',
        gap: 12,
        transition: 'all 0.2s ease',
        alignItems: 'flex-start',
        boxShadow: checked ? '0 4px 12px rgba(255,140,0,0.12)' : '0 1px 3px rgba(0,0,0,0.04)'
      }}
    >
      <input
        type="checkbox"
        checked={checked}
        readOnly
        style={{ marginTop: 4 }}
      />
      <div>
        <div style={{ fontWeight: 700, color: '#462400' }}>{label}</div>
        <div style={{ fontSize: '0.85rem', color: '#7a7a7a' }}>{description}</div>
      </div>
    </label>
  )
}

function buildNoteText(items = [], storeName = '', branch = '') {
  if (!items.length) return ''
  const priceFormatter = new Intl.NumberFormat('en-PH', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })

  const formatDateMMDDYYYY = (d) => {
    const mm = String(d.getMonth() + 1).padStart(2, '0')
    const dd = String(d.getDate()).padStart(2, '0')
    const yyyy = d.getFullYear()
    return `${mm}/${dd}/${yyyy}`
  }

  const grouped = items.reduce((acc, item) => {
    const category = (item.category || 'General').trim() || 'General'
    if (!acc[category]) acc[category] = []
    acc[category].push(item)
    return acc
  }, {})

  const sortedCategories = Object.keys(grouped).sort((a, b) => a.localeCompare(b, undefined, { sensitivity: 'base' }))

  const storeLine = `${storeName || ''}${branch ? ` — ${branch}` : ''}`.trim()
  const lines = ['PRICELIST:', storeLine, formatDateMMDDYYYY(new Date()), '']

  sortedCategories.forEach((category, index) => {
    lines.push(`[${category}]`)
    const itemsInCategory = grouped[category].sort((a, b) => (a.name || '').localeCompare(b.name || '', undefined, { sensitivity: 'base' }))
    itemsInCategory.forEach((item) => {
      const priceValue = typeof item.price === 'number' ? item.price : Number(item.price || 0)
      const name = item.name || 'Unnamed Item'
      const desc = (item.description || '').trim()
      const descPart = desc ? ` (${desc})` : ''
      lines.push(`• ${name}${descPart} — ₱${priceFormatter.format(priceValue)}`)
    })
    if (index !== sortedCategories.length - 1) lines.push('')
  })

  lines.push('', 'Shared via Presyohan')

  return lines.join('\n').trim()
}

function Spinner({ small = false }) {
  const size = small ? 16 : 20
  return (
    <span style={{
      width: size, height: size, border: '2px solid #eee', borderTopColor: '#ff8c00',
      borderRadius: '50%', display: 'inline-block', animation: 'spin 0.8s linear infinite'
    }} />
  )
}
