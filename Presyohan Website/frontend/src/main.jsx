import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
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

// Removed default Vite App.css to avoid constraining #root width

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
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
      </Routes>
    </BrowserRouter>
  </React.StrictMode>
)
