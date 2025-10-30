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
        navigate('/store', { replace: true });
        return;
      }
      const { data: { subscription } } = supabase.auth.onAuthStateChange((_event, session) => {
        if (session) navigate('/store', { replace: true });
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
