import React, { useEffect, useMemo, useState } from 'react';
import '../../styles/StoreModals.css';
import { supabase } from '../../config/supabaseClient';

// Simple SVG icons to match the provided design cues
const GearIcon = ({ color = '#0d3b66' }) => (
  <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="3"></circle>
    <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9c.68 0 1.24.49 1.51 1.15.27.66.13 1.41-.33 1.92-.46.51-1.21.74-1.91.68z"></path>
  </svg>
);

const InviteIcon = ({ color = '#ff8c00' }) => (
  <svg width="30" height="30" viewBox="0 0 24 24" fill={color}>
    <path d="M15 14c2.761 0 5 2.239 5 5v1H4v-1c0-2.761 2.239-5 5-5h6zm-3-2a4 4 0 1 1 0-8 4 4 0 0 1 0 8zm7-7h2v2h-2v2h-2V7h-2V5h2V3h2v2z"/>
  </svg>
);

const TrashIcon = ({ color = '#8b0000' }) => (
  <svg width="30" height="30" viewBox="0 0 24 24" fill={color}>
    <path d="M3 6h18v2H3V6zm2 3h14l-1.5 12.5a2 2 0 0 1-2 1.5H8.5a2 2 0 0 1-2-1.5L5 9zm6-7h2a2 2 0 0 1 2 2h-6a2 2 0 0 1 2-2z"/>
  </svg>
);

const MembersIcon = ({ color = '#444' }) => (
  <svg width="30" height="30" viewBox="0 0 24 24" fill={color}>
    <path d="M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5s-3 1.34-3 3 1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5s-3 1.34-3 3 1.34 3 3 3zm0 2c-2.67 0-8 1.34-8 4v2h10v-2c0-1.1.9-2 2-2h2c.73 0 1.41.19 2 .52-.6-2.47-4.4-4.52-8-4.52z"/>
  </svg>
);

export default function StoreOptionsModal({
  open,
  onClose,
  storeId,
  storeName,
  role,
  onSettings,
  onInvite,
  onLeave,
  onDelete
}) {
  const [ownerCount, setOwnerCount] = useState(1);
  const [memberSummary, setMemberSummary] = useState({ owners: 1, managers: 0, employees: 0 });
  const isOwner = role === 'owner';

  // Load owner count and member summary when opened
  useEffect(() => {
    const load = async () => {
      if (!open || !storeId) return;
      try {
        const { data: owners, error: ownersErr, count } = await supabase
          .from('store_members')
          .select('role', { count: 'exact' })
          .eq('store_id', storeId)
          .eq('role', 'owner');
        if (!ownersErr) setOwnerCount(typeof count === 'number' ? count : (owners || []).length);

        const { data: members } = await supabase
          .from('store_members')
          .select('role')
          .eq('store_id', storeId);
        const summary = { owners: 0, managers: 0, employees: 0 };
        (members || []).forEach(m => {
          if (m.role === 'owner') summary.owners += 1;
          else if (m.role === 'manager') summary.managers += 1;
          else summary.employees += 1;
        });
        setMemberSummary(summary);
      } catch (e) {
        // Fail silently; keep defaults
        console.warn('Failed to load member summary', e);
      }
    };
    load();
  }, [open, storeId]);

  const actions = useMemo(() => {
    if (isOwner) {
      // If there are other owners, show Leave instead of Delete
      const canDelete = ownerCount <= 1;
      return [
        { key: 'settings', label: 'Settings', icon: <GearIcon />, onClick: onSettings },
        { key: 'invite', label: 'Invite Staff', icon: <InviteIcon />, onClick: onInvite },
        canDelete
          ? { key: 'delete', label: 'Delete Store', icon: <TrashIcon />, onClick: onDelete }
          : { key: 'leave', label: 'Leave Store', icon: <TrashIcon />, onClick: onLeave }
      ].filter(Boolean);
    }
    // Manager/Staff view
    return [
      { key: 'members', label: `Members (${memberSummary.owners + memberSummary.managers + memberSummary.employees})`, icon: <MembersIcon />, onClick: null },
      { key: 'leave', label: 'Leave Store', icon: <TrashIcon />, onClick: onLeave }
    ];
  }, [isOwner, ownerCount, memberSummary, onSettings, onInvite, onLeave, onDelete]);

  if (!open) return null;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-card" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header-strip">
          <h2>{storeName}</h2>
          <button className="close-btn" onClick={onClose} aria-label="Close" />
        </div>
        {/* Optional note for staff */}
        {!isOwner && (
          <div style={{ padding: '10px 20px', color: '#666', textAlign: 'center' }}>
            You can only view price on this store
          </div>
        )}
        {/* Actions grid */}
        <div className="option-grid">
          {actions.map(a => (
            <button key={a.key} className="option-btn" onClick={a.onClick || (() => {})}>
              <div className="option-icon">{a.icon}</div>
              <div className="option-label">{a.label}</div>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}