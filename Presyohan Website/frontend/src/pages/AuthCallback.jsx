import React, { useEffect, useState } from 'react';
import { supabase } from '../config/supabaseClient';
import { useNavigate } from 'react-router-dom';

export default function AuthCallback() {
  const [status, setStatus] = useState('Signing you in');
  const navigate = useNavigate();

  useEffect(() => {
    const run = async () => {
      const { data: { session }, error } = await supabase.auth.getSession();
      if (error) {
        setStatus(`Error: ${error.message}`);
        return;
      }
      if (session) {
        // Persist or update public.app_users profile for OAuth users
        try {
          const user = session.user;
          await supabase
            .from('app_users')
            .upsert(
              {
                id: user.id,
                email: user.email,
                name: user.user_metadata?.name || null,
                avatar_url: user.user_metadata?.avatar_url || null
              },
              { onConflict: 'id' }
            );
        } catch (err) {
          // Non-fatal: proceed even if profile upsert fails
          console.warn('Profile upsert failed in OAuth callback:', err);
        }
  navigate('/stores', { replace: true });
        return;
      }
      const { data: { subscription } } = supabase.auth.onAuthStateChange((_event, session) => {
  if (session) {
    // Upsert profile as soon as session appears
    (async () => {
      try {
        await supabase
          .from('app_users')
          .upsert(
            {
              id: session.user.id,
              email: session.user.email,
              name: session.user.user_metadata?.name || null,
              avatar_url: session.user.user_metadata?.avatar_url || null
            },
            { onConflict: 'id' }
          );
      } catch (e) {
        console.warn('Profile upsert failed in auth state change:', e);
      }
      navigate('/stores', { replace: true });
    })();
  }
      });
      const timer = setTimeout(() => setStatus('No session found. Please try logging in again.'), 4000);
      return () => {
        subscription?.unsubscribe();
        clearTimeout(timer);
      };
    };
    run();
  }, [navigate]);

  return (
    <div style={{display:'flex',alignItems:'center',justifyContent:'center',minHeight:'100vh'}}>
      <p>{status}</p>
    </div>
  );
}
