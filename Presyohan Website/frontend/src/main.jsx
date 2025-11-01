﻿import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import LoginSignupPage from './pages/LoginSignupPage.jsx'
import StoresPage from './pages/StoresPage.jsx'
import VerifyEmailPage from './pages/VerifyEmailPage.jsx'
import AuthCallback from './pages/AuthCallback.jsx'
import StorePage from './pages/StorePage.jsx'
import ManageItemsPage from './pages/ManageItemsPage.jsx'

// Removed default Vite App.css to avoid constraining #root width

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/login" replace />} />
        <Route path="/login" element={<LoginSignupPage />} />
        <Route path="/stores" element={<StoresPage />} />
        {/* Store page */}
        <Route path="/store" element={<StorePage />} />
        <Route path="/manage-items" element={<ManageItemsPage />} />
        <Route path="/verify-email" element={<VerifyEmailPage />} />
        <Route path="/auth/callback" element={<AuthCallback />} />
      </Routes>
    </BrowserRouter>
  </React.StrictMode>
)
