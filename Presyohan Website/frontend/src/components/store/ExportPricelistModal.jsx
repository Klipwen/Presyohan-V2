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

  const canExport = useMemo(() => role === 'owner' || role === 'manager', [role])

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
      } catch (e) {
        setErrorText(e.message || 'Failed to load preview.')
      } finally {
        setIsLoadingPreview(false)
      }
    }
    fetchPreview()
  }, [open, storeId])

  const formatFilename = () => {
    const slug = `${(storeName || 'store').trim()}_${(branch || 'main').trim()}`
      .toLowerCase()
      .replace(/\s+/g, '-')
      .replace(/[^a-z0-9\-]/g, '')
    const pad = (n) => String(n).padStart(2, '0')
    const d = new Date()
    const ts = `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}_${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`
    return `presyohan_${slug}_pricelist_${ts}.xlsx`
  }

  const handleExport = async () => {
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
        <h2 style={{ margin: 0, fontSize: '1.25rem', color: '#ff8c00' }}>Export Pricelist</h2>
        <p style={{ marginTop: 8, color: '#555' }}>Generate a professionally formatted Excel file of your store products.</p>

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

        {errorText && (
          <div style={{ marginTop: 10, color: '#d32f2f' }}>{errorText}</div>
        )}

        {!canExport && (
          <div style={{ marginTop: 10, color: '#d32f2f' }}>Only owners and managers can export.</div>
        )}

        <div style={{ display: 'flex', gap: 10, marginTop: 20, justifyContent: 'flex-end' }}>
          <button
            onClick={onClose}
            disabled={isExporting}
            style={{
              padding: '10px 14px', borderRadius: 12, border: '1px solid #ddd', background: 'white', color: '#555',
              cursor: 'pointer'
            }}
          >Cancel</button>
          <button
            onClick={handleExport}
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
            {isExporting ? 'Preparing…' : 'Confirm Export'}
          </button>
        </div>
      </div>
    </div>
  )
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