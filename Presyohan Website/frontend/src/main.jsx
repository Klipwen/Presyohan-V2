﻿import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import LoginSignupPage from './pages/LoginSignupPage.jsx'
import StoresPage from './pages/StoresPage.jsx'
import VerifyEmailPage from './pages/VerifyEmailPage.jsx'
import AuthCallback from './pages/AuthCallback.jsx'

// Removed default Vite App.css to avoid constraining #root width

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/login" replace />} />
        <Route path="/login" element={<LoginSignupPage />} />
        <Route path="/stores" element={<StoresPage />} />
        <Route path="/store" element={<Navigate to="/stores" replace />} />
        <Route path="/verify-email" element={<VerifyEmailPage />} />
        <Route path="/auth/callback" element={<AuthCallback />} />
      </Routes>
    </BrowserRouter>
  </React.StrictMode>
)
