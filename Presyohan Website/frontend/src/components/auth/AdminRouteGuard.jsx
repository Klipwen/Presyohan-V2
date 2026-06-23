import React, { useState, useEffect } from 'react';
import { Navigate } from 'react-router-dom';
import { supabase } from '../../config/supabaseClient';

export default function AdminRouteGuard({ children }) {
  const [loading, setLoading] = useState(true);
  const [authorized, setAuthorized] = useState(false);
  const [redirectPath, setRedirectPath] = useState('/ako-ang-admin');

  useEffect(() => {
    const verifyAdmin = async () => {
      try {
        // 1. Check if they have passed the passcode gatekeeper
        const passcodePassed = sessionStorage.getItem('admin_passcode_passed') === 'true';
        if (!passcodePassed) {
          setRedirectPath('/ako-ang-admin');
          setAuthorized(false);
          setLoading(false);
          return;
        }

        // 2. Check if they have an active Supabase session
        const { data: { session } } = await supabase.auth.getSession();
        if (!session) {
          setRedirectPath('/login');
          setAuthorized(false);
          setLoading(false);
          return;
        }

        // 3. Check if the user's role is 'admin'
        let { data: profile, error } = await supabase
          .from('app_users')
          .select('role')
          .eq('id', session.user.id)
          .maybeSingle();

        if (!profile && !error) {
          try {
            const { data: fallbackProfile } = await supabase
              .from('app_users')
              .select('role')
              .eq('auth_uid', session.user.id)
              .maybeSingle();
            if (fallbackProfile) {
              profile = fallbackProfile;
            }
          } catch (_) {}
        }

        if (error || !profile || profile.role !== 'admin') {
          // If logged in but not admin, redirect to regular stores page
          setRedirectPath('/stores');
          setAuthorized(false);
        } else {
          setAuthorized(true);
        }
      } catch (e) {
        console.error('Error verifying admin authorization:', e);
        setAuthorized(false);
        setRedirectPath('/login');
      } finally {
        setLoading(false);
      }
    };

    verifyAdmin();
  }, []);

  if (loading) {
    return (
      <div style={{
        height: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#0f172a',
        color: '#1FA5C4',
        fontSize: '1.2rem',
        fontWeight: '600',
        fontFamily: 'sans-serif'
      }}>
        Verifying administration credentials...
      </div>
    );
  }

  return authorized ? children : <Navigate to={redirectPath} replace />;
}
