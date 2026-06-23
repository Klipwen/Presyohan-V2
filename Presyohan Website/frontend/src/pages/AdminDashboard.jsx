import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { supabase } from '../config/supabaseClient';
import presyohanLogo from '../assets/presyohan_logo.png';
import AnalyticsOverview from '../components/admin/AnalyticsOverview.jsx';
import UserManagement from '../components/admin/UserManagement.jsx';
import StoreDirectory from '../components/admin/StoreDirectory.jsx';
import StandardPriceStores from '../components/admin/StandardPriceStores.jsx';
import AppReleases from '../components/admin/AppReleases.jsx';
import Announcements from '../components/admin/Announcements.jsx';
import '../styles/AdminDashboard.css';

export default function AdminDashboard() {
  const [activeTab, setActiveTab] = useState('analytics');
  const [adminEmail, setAdminEmail] = useState('');
  const navigate = useNavigate();

  useEffect(() => {
    const fetchUser = async () => {
      const { data: { user } } = await supabase.auth.getUser();
      if (user) {
        setAdminEmail(user.email);
      }
    };
    fetchUser();
  }, []);

  const handleLogout = async () => {
    await supabase.auth.signOut();
    sessionStorage.removeItem('admin_passcode_passed');
    navigate('/', { replace: true });
  };

  const menuItems = [
    {
      id: 'analytics',
      label: 'Analytics Overview',
      icon: (
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
          <line x1="18" y1="20" x2="18" y2="10" />
          <line x1="12" y1="20" x2="12" y2="4" />
          <line x1="6" y1="20" x2="6" y2="14" />
        </svg>
      )
    },
    {
      id: 'users',
      label: 'User Management',
      icon: (
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
          <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
          <circle cx="9" cy="7" r="4" />
          <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
          <path d="M16 3.13a4 4 0 0 1 0 7.75" />
        </svg>
      )
    },
    {
      id: 'stores',
      label: 'Store Directory',
      icon: (
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
          <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
          <polyline points="9 22 9 12 15 12 15 22" />
        </svg>
      )
    },
    {
      id: 'standard-stores',
      label: 'Standard Price Stores',
      icon: (
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
          <circle cx="12" cy="12" r="10" />
          <line x1="12" y1="8" x2="12" y2="16" />
          <line x1="8" y1="12" x2="16" y2="12" />
        </svg>
      )
    },
    {
      id: 'releases',
      label: 'App Releases (APK)',
      icon: (
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
          <polyline points="7 10 12 15 17 10" />
          <line x1="12" y1="15" x2="12" y2="3" />
        </svg>
      )
    },
    {
      id: 'announcements',
      label: 'Announcements',
      icon: (
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
          <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
          <path d="M13.73 21a2 2 0 0 1-3.46 0" />
        </svg>
      )
    }
  ];

  const renderContent = () => {
    switch (activeTab) {
      case 'analytics':
        return <AnalyticsOverview setActiveTab={setActiveTab} />;
      case 'users':
        return (
          <div className="admin-card">
            <h3 style={{ marginBottom: '6px' }}>User Management</h3>
            <p style={{ color: '#64748b', fontSize: '0.9rem', marginBottom: '24px' }}>
              Search and administer registered client accounts, roles, and suspensions.
            </p>
            <UserManagement />
          </div>
        );
      case 'stores':
        return (
          <div className="admin-card">
            <h3 style={{ marginBottom: '6px' }}>Store Directory</h3>
            <p style={{ color: '#64748b', fontSize: '0.9rem', marginBottom: '24px' }}>
              Monitor store creation, verify membership counts, and search owner linkages.
            </p>
            <StoreDirectory />
          </div>
        );
      case 'standard-stores':
        return (
          <div className="admin-card">
            <h3 style={{ marginBottom: '12px' }}>Standard Price Stores</h3>
            <StandardPriceStores />
          </div>
        );
      case 'releases':
        return (
          <div className="admin-card">
            <h3 style={{ marginBottom: '12px' }}>App Releases (APK)</h3>
            <AppReleases />
          </div>
        );
      case 'announcements':
        return (
          <div className="admin-card">
            <h3 style={{ marginBottom: '12px' }}>System Announcements</h3>
            <Announcements />
          </div>
        );
      default:
        return null;
    }
  };

  const getPageTitle = () => {
    const item = menuItems.find(m => m.id === activeTab);
    return item ? item.label : 'Admin Portal';
  };

  return (
    <div className="admin-container">
      {/* Sidebar */}
      <aside className="admin-sidebar">
        <div className="admin-sidebar-header">
          <img src={presyohanLogo} alt="Presyohan Logo" />
          <h2>
            <span className="presyo">presyo</span><span className="han">han?</span>
          </h2>
        </div>

        <nav className="admin-sidebar-menu">
          {menuItems.map((item) => (
            <button
              key={item.id}
              className={`admin-menu-item ${activeTab === item.id ? 'active' : ''}`}
              onClick={() => setActiveTab(item.id)}
            >
              {item.icon}
              {item.label}
            </button>
          ))}
        </nav>

        <div className="admin-sidebar-footer">
          <div className="admin-user-profile">
            <div className="admin-avatar">
              {adminEmail ? adminEmail.slice(0, 2).toUpperCase() : 'AD'}
            </div>
            <div className="admin-user-info">
              <div style={{ fontSize: '0.9rem', fontWeight: 600 }}>Administrator</div>
              <div className="admin-user-email" title={adminEmail}>{adminEmail}</div>
            </div>
          </div>
          <button className="admin-logout-btn" onClick={handleLogout}>
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
              <polyline points="16 17 21 12 16 7" />
              <line x1="21" y1="12" x2="9" y2="12" />
            </svg>
            Sign Out
          </button>
        </div>
      </aside>

      {/* Main Content Area */}
      <main className="admin-main">
        <header className="admin-topbar">
          <h1>{getPageTitle()}</h1>
          <div className="admin-system-status">
            <span className="status-dot"></span>
            System Live
          </div>
        </header>

        <section className="admin-content">
          {renderContent()}
        </section>
      </main>
    </div>
  );
}
