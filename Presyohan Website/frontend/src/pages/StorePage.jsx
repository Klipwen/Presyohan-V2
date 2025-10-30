import React, { useState, useEffect } from 'react';
import '../styles/StorePage.css';
import Header from '../components/layout/Header';
import Footer from '../components/layout/Footer';
import AddStoreModal from '../components/store/AddStoreModal';
import StoreCard from '../components/store/StoreCard';
import '../styles/StorePage.css';
import { supabase } from '../config/supabaseClient';
import { useNavigate } from 'react-router-dom';

export default function StorePage() {
  const [sideMenuOpen, setSideMenuOpen] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [stores, setStores] = useState([
    { id: 'demo-1', name: 'Gaisano Grand Mall', branch: 'Colon Street, Cebu City' },
    { id: 'demo-2', name: 'SM City Cebu', branch: 'North Reclamation Area' }
  ]);
  const navigate = useNavigate();

  useEffect(() => {
    // Redirect to login if no session
    const check = async () => {
      const { data: { session } } = await supabase.auth.getSession();
      if (!session) navigate('/login', { replace: true });
    };
    check();
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
      <Header variant="store" onToggleSideMenu={toggleSideMenu} />

      <div className={`side-menu-overlay ${sideMenuOpen ? 'active' : ''}`} onClick={toggleSideMenu}></div>
      <div className={`side-menu ${sideMenuOpen ? 'active' : ''}`}>
        <div className="side-menu-header">
          <h3>Menu</h3>
          <p>Welcome back!</p>
        </div>
        <div className="side-menu-items">
          <a href="#" className="menu-item active">
            <svg fill="currentColor" viewBox="0 0 24 24">
              <path d="M3 21h18v-2H3v2zm0-4h18v-2H3v2zm0-4h18v-2H3v2zm0-4h18V7H3v2zm0-6v2h18V3H3z"/>
            </svg>
            <span>My Stores</span>
          </a>
          <a href="#" className="menu-item">
            <svg fill="currentColor" viewBox="0 0 24 24">
              <path d="M3 13h8V3H3v10zm0 8h8v-6H3v6zm10 0h8V11h-8v10zm0-18v6h8V3h-8z"/>
            </svg>
            <span>Dashboard</span>
          </a>
          <a href="#" className="menu-item">
            <svg fill="currentColor" viewBox="0 0 24 24">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"/>
            </svg>
            <span>Help & Support</span>
          </a>
          <div className="menu-divider"></div>
          <a href="#" className="menu-item">
            <svg fill="currentColor" viewBox="0 0 24 24">
              <path d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.07.62-.07.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17 .47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22 .08 .47 0 .59-.22l1.92-3.32c.12-.22 .07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"/>
            </svg>
            <span>Settings</span>
          </a>
          <a href="#" className="menu-item" onClick={(e) => { e.preventDefault(); logout(); }}>
            <svg fill="currentColor" viewBox="0 0 24 24">
              <path d="M17 7l-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5zM4 5h8V3H4c-1.1 0-2 .9-2 2v14c0 1.1 .9 2 2 2h8v-2H4V5z"/>
            </svg>
            <span>Logout</span>
          </a>
        </div>
      </div>

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
            <StoreCard key={s.id} name={s.name} location={s.branch} />
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
