import React, { useState, useEffect } from 'react';
import '../styles/StoresPage.css';
import StoreHeader from '../components/layout/StoreHeader';
import Footer from '../components/layout/Footer';
import AddStoreModal from '../components/store/AddStoreModal';
import StoreCard from '../components/store/StoreCard';
import StoreOptionsModal from '../components/store/StoreOptionsModal';
import ConfirmModal from '../components/store/ConfirmModal';
import { supabase } from '../config/supabaseClient';
import { useNavigate } from 'react-router-dom';

export default function StoresPage() {
  const [sideMenuOpen, setSideMenuOpen] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [stores, setStores] = useState([]);
  const [optionsOpen, setOptionsOpen] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [confirmConfig, setConfirmConfig] = useState({ title: '', message: '', action: null, confirmLabel: 'Confirm' });
  const [selectedStore, setSelectedStore] = useState(null);
  const navigate = useNavigate();

  useEffect(() => {
    // Redirect to login if no session, then load stores via RPC
    const init = async () => {
      const { data: { session } } = await supabase.auth.getSession();
      if (!session) {
        navigate('/login', { replace: true });
        return;
      }
      try {
        const { data, error } = await supabase.rpc('get_user_stores');
        if (error) {
          console.warn('Failed to load stores:', error);
          return;
        }
        // Map RPC rows to UI shape
        const mapped = (data || []).map(r => ({
          id: r.store_id,
          name: r.name,
          branch: r.branch || '',
          type: r.type || 'Type',
          role: r.role || null
        }));
        setStores(mapped);
      } catch (e) {
        console.warn('Unexpected error loading stores:', e);
      }
    };
    init();
  }, [navigate]);

  const toggleSideMenu = () => setSideMenuOpen(v => !v);
  const openModal = () => setModalOpen(true);
  const closeModal = () => setModalOpen(false);

  const joinStore = async (code) => {
    // Placeholder: join flow via invite code RPC could be added
    alert(`Requested to join store with code: ${code}`);
  };

  const createStore = async (payload) => {
    try {
      const { data: { session } } = await supabase.auth.getSession();
      if (!session) {
        navigate('/login', { replace: true });
        return;
      }
      const { data, error } = await supabase.rpc('create_store', {
        p_name: payload.name,
        p_branch: payload.branch,
        p_type: payload.type
      });
      if (error) {
        alert(error.message || 'Failed to create store.');
        return;
      }
      const newStore = { id: data, name: payload.name, branch: payload.branch };
      setStores((prev) => [newStore, ...prev]);
      window.location.reload();
    } catch (e) {
      alert(e.message || 'Unexpected error creating store.');
    }
  };

  const logout = async () => {
    await supabase.auth.signOut();
    setSideMenuOpen(false);
    navigate('/login', { replace: true });
  };

  // Helper to resolve current app_users.id if present, else use auth.uid()
  const getCurrentUserId = async () => {
    const { data: { session } } = await supabase.auth.getSession();
    const authId = session?.user?.id;
    if (!authId) return null;
    const { data: userRow } = await supabase.from('app_users').select('id, auth_uid').eq('auth_uid', authId).maybeSingle();
    return userRow?.id || authId;
  };

  const openStoreOptions = (store) => {
    setSelectedStore(store);
    setOptionsOpen(true);
  };

  const closeOptions = () => setOptionsOpen(false);

  const handleSettings = () => {
    setOptionsOpen(false);
    navigate(`/manage-store?id=${encodeURIComponent(selectedStore?.id || '')}`);
  };

  const handleInvite = () => {
    setOptionsOpen(false);
    navigate(`/invite-staff?id=${encodeURIComponent(selectedStore?.id || '')}`);
  };

  const triggerConfirm = (type) => {
    if (!selectedStore) return;
    if (type === 'delete') {
      setConfirmConfig({
        title: 'Delete Store',
        message: 'Are you sure you want to delete this store? This action cannot be undone.',
        confirmLabel: 'Delete',
        action: async () => {
          const { error } = await supabase.from('stores').delete().eq('id', selectedStore.id);
          if (error) { alert(error.message || 'Failed to delete store'); return; }
          setStores(prev => prev.filter(s => s.id !== selectedStore.id));
          setConfirmOpen(false);
          setSelectedStore(null);
        }
      });
    } else {
      setConfirmConfig({
        title: 'Leave Store',
        message: 'Are you sure you want to leave this store?',
        confirmLabel: 'Leave',
        action: async () => {
          const uid = await getCurrentUserId();
          if (!uid) { alert('Not authenticated'); return; }
          const { error } = await supabase.from('store_members').delete().eq('store_id', selectedStore.id).eq('user_id', uid);
          if (error) { alert(error.message || 'Failed to leave store'); return; }
          setStores(prev => prev.filter(s => s.id !== selectedStore.id));
          setConfirmOpen(false);
          setSelectedStore(null);
        }
      });
    }
    setOptionsOpen(false);
    setConfirmOpen(true);
  };

  return (
    <div className="page-root">
      <StoreHeader stores={stores} onLogout={logout} includeAllStoresLink={false} />


      <div className="main-content">
        <div className="page-header">
          <h1 className="page-title">Stores</h1>
          <button className="add-store-btn" onClick={openModal}>
            <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 4v16m8-8H4"/>
            </svg>
            Add Store
          </button>
        </div>

        <div className="stores-grid" id="storesGrid">
          {stores.map((s) => (
            <StoreCard
              key={s.id}
              name={s.name}
              location={s.branch}
              type={s.type}
              role={s.role}
              href={`/store?id=${encodeURIComponent(s.id)}`}
              onOptionsClick={() => openStoreOptions(s)}
            />
          ))}
        </div>
      </div>

      <button className="floating-add-btn" onClick={openModal}>
        <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 4v16m8-8H4"/>
        </svg>
      </button>

      <AddStoreModal open={modalOpen} onClose={closeModal} onJoin={joinStore} onCreate={createStore} />

      {/* Options modal */}
      {selectedStore && (
        <StoreOptionsModal
          open={optionsOpen}
          onClose={closeOptions}
          storeId={selectedStore.id}
          storeName={selectedStore.name}
          role={selectedStore.role}
          onSettings={handleSettings}
          onInvite={handleInvite}
          onDelete={() => triggerConfirm('delete')}
          onLeave={() => triggerConfirm('leave')}
        />
      )}

      {/* Confirm modal */}
      <ConfirmModal
        open={confirmOpen}
        title={confirmConfig.title}
        message={confirmConfig.message}
        confirmLabel={confirmConfig.confirmLabel}
        onConfirm={confirmConfig.action}
        onCancel={() => setConfirmOpen(false)}
      />

      <Footer />
    </div>
  );
}
