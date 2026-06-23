import React, { useEffect } from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom'
import { supabase } from './config/supabaseClient'
import LoginSignupPage from './pages/LoginSignupPage.jsx'
import LandingPage from './pages/LandingPage.jsx'
import StoresPage from './pages/StoresPage.jsx'
import VerifyEmailPage from './pages/VerifyEmailPage.jsx'
import AuthCallback from './pages/AuthCallback.jsx'
import StorePage from './pages/StorePage.jsx'
import ManageItemsPage from './pages/ManageItemsPage.jsx'
import StoreSettingsPage from './pages/StoreSettingsPage.jsx'
import ContactPage from './pages/ContactPage.jsx'
import ProfilePage from './pages/ProfilePage.jsx'
import AdminGatekeeper from './pages/AdminGatekeeper.jsx'
import AdminDashboard from './pages/AdminDashboard.jsx'
import AdminRouteGuard from './components/auth/AdminRouteGuard.jsx'

// Track activity of logged in users on route transitions
function ActivityTracker({ children }) {
  const location = useLocation();

  useEffect(() => {
    const updateActivity = async () => {
      try {
        const { data: { session } } = await supabase.auth.getSession();
        if (session?.user) {
          await supabase
            .from('app_users')
            .update({ last_activity_at: new Date().toISOString() })
            .eq('id', session.user.id);
        }
      } catch (err) {
        console.error('Error updating user heartbeat:', err);
      }
    };
    updateActivity();
  }, [location.pathname]);

  return children;
}

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <ActivityTracker>
        <Routes>
          <Route path="/" element={<LandingPage />} />
          <Route path="/login" element={<LoginSignupPage />} />
          <Route path="/stores" element={<StoresPage />} />
          {/* Store page */}
          <Route path="/store" element={<StorePage />} />
          <Route path="/manage-store" element={<StoreSettingsPage />} />
          <Route path="/manage-items" element={<ManageItemsPage />} />
          <Route path="/verify-email" element={<VerifyEmailPage />} />
          <Route path="/auth/callback" element={<AuthCallback />} />
          <Route path="/contact" element={<ContactPage />} />
          {/* Protected profile page */}
          <Route path="/profile" element={<ProfilePage />} />
          
          {/* Admin portal routes */}
          <Route path="/ako-ang-admin" element={<AdminGatekeeper />} />
          <Route 
            path="/admin/dashboard" 
            element={
              <AdminRouteGuard>
                <AdminDashboard />
              </AdminRouteGuard>
            } 
          />
        </Routes>
      </ActivityTracker>
    </BrowserRouter>
  </React.StrictMode>
)
