import React, { useEffect, useState } from 'react';
import '../../styles/JoinRequestRoleModal.css';
import { supabase } from '../../config/supabaseClient';

export default function JoinRequestRoleModal({ open, onBack, onAccept, storeId, requesterMessage }) {
  const [role, setRole] = useState('employee');
  const [store, setStore] = useState({ name: '', branch: '' });

  useEffect(() => {
    const loadStore = async () => {
      if (!open || !storeId) return;
      try {
        const { data } = await supabase
          .from('stores')
          .select('name, branch')
          .eq('id', storeId)
          .single();
        if (data) setStore({ name: data.name || '', branch: data.branch || '' });
      } catch (e) {
        console.warn('Failed to fetch store info', e);
      }
    };
    loadStore();
  }, [open, storeId]);

  if (!open) return null;

  return (
    <div className="jr-modal-overlay" role="dialog" aria-modal="true">
      <div className="jr-modal" role="document">
        <div className="jr-modal-header">
          <div className="jr-store-name">{store.name}{store.branch ? ` | ${store.branch}` : ''}</div>
          <h3>Join Request</h3>
        </div>
        <div className="jr-modal-body">
          <p className="jr-intro">{requesterMessage || 'A user requested to join your store.'}</p>

          <div className="jr-role-options" role="radiogroup" aria-label="Select role">
            <label
              className={`jr-role-option ${role === 'employee' ? 'selected' : ''}`}
              tabIndex={0}
              onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') setRole('employee'); }}
            >
              <div className="jr-role-header">
                <input
                  type="radio"
                  name="join-role"
                  value="employee"
                  checked={role === 'employee'}
                  onChange={() => setRole('employee')}
                />
                <span className="jr-option-title">View only prices (sales staff)</span>
              </div>
              <div className="jr-role-details">
                <span className="jr-option-sub">Limited access</span>
                <ul className="jr-bullets">
                  <li>Can view pricing only</li>
                </ul>
              </div>
            </label>

            <label
              className={`jr-role-option ${role === 'manager' ? 'selected' : ''}`}
              tabIndex={0}
              onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') setRole('manager'); }}
            >
              <div className="jr-role-header">
                <input
                  type="radio"
                  name="join-role"
                  value="manager"
                  checked={role === 'manager'}
                  onChange={() => setRole('manager')}
                />
                <span className="jr-option-title">Manage prices (manager)</span>
              </div>
              <div className="jr-role-details">
                <span className="jr-option-sub">Elevated access</span>
                <ul className="jr-bullets">
                  <li>Can manage pricing</li>
                </ul>
              </div>
            </label>
          </div>
        </div>
        <div className="jr-modal-footer">
          <button className="jr-btn jr-btn-secondary" onClick={onBack}>Back</button>
          <button className="jr-btn jr-btn-primary" onClick={() => onAccept(role)}>Accept</button>
        </div>
      </div>
    </div>
  );
}