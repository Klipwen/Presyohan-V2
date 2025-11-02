import React, { useState, useEffect } from 'react';
import '../styles/StoresPage.css';
import StoreHeader from '../components/layout/StoreHeader';
import Footer from '../components/layout/Footer';
import AddStoreModal from '../components/store/AddStoreModal';
import StoreCard from '../components/store/StoreCard';
import { supabase } from '../config/supabaseClient';
import { useNavigate } from 'react-router-dom';

export default function StoresPage() {
  const [sideMenuOpen, setSideMenuOpen] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [stores, setStores] = useState([]);
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

      <Footer />
    </div>
  );
}
