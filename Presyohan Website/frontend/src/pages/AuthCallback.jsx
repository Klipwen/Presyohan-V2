import React, { useEffect, useState } from 'react';
import { supabase } from '../config/supabaseClient';
import { useNavigate } from 'react-router-dom';

export default function AuthCallback() {
  const [status, setStatus] = useState('Signing you in');
  const [needEmail, setNeedEmail] = useState(false);
  const [emailInput, setEmailInput] = useState('');
  const [savingEmail, setSavingEmail] = useState(false);
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
          const userEmail = user.email || null;
          if (!userEmail) {
            // Some Facebook accounts may not expose email. Collect it to proceed.
            setNeedEmail(true);
            setStatus('Facebook signed in. Please provide your email to continue.');
            return;
          }
          await supabase
            .from('app_users')
            .upsert(
              {
                id: user.id,
                email: userEmail,
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
        const userEmail = session.user.email || null;
        if (!userEmail) {
          setNeedEmail(true);
          setStatus('Facebook signed in. Please provide your email to continue.');
          return;
        }
        await supabase
          .from('app_users')
          .upsert(
            {
              id: session.user.id,
              email: userEmail,
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

  const saveEmailAndProceed = async () => {
    if (!emailInput || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailInput)) {
      setStatus('Please enter a valid email address.');
      return;
    }
    setSavingEmail(true);
    try {
      const { data: { session } } = await supabase.auth.getSession();
      const userId = session?.user?.id;
      if (!userId) {
        setStatus('No session found. Please try logging in again.');
        setSavingEmail(false);
        return;
      }
      await supabase
        .from('app_users')
        .upsert(
          {
            id: userId,
            email: emailInput,
            name: session.user.user_metadata?.name || null,
            avatar_url: session.user.user_metadata?.avatar_url || null
          },
          { onConflict: 'id' }
        );
      navigate('/stores', { replace: true });
    } catch (e) {
      setStatus(e.message || 'Failed to save email. Please try again.');
    } finally {
      setSavingEmail(false);
    }
  };

  return (
    <div style={{display:'flex',alignItems:'center',justifyContent:'center',minHeight:'100vh', padding:'16px'}}>
      {!needEmail ? (
        <p>{status}</p>
      ) : (
        <div style={{maxWidth: 420, width:'100%', background:'#fff', border:'1px solid #e5e7eb', borderRadius:8, padding:16, boxShadow:'0 4px 12px rgba(0,0,0,0.08)'}}>
          <h3 style={{marginTop:0}}>Complete Sign In</h3>
          <p style={{color:'#374151'}}>Facebook did not share your email. Please enter it to continue.</p>
          <label htmlFor="emailInput" style={{display:'block', fontSize:14, color:'#374151', marginBottom:6}}>Email address</label>
          <input
            id="emailInput"
            type="email"
            value={emailInput}
            onChange={(e) => setEmailInput(e.target.value)}
            placeholder="you@example.com"
            style={{width:'100%', padding:'10px 12px', border:'1px solid #d1d5db', borderRadius:6, marginBottom:12}}
          />
          <button
            onClick={saveEmailAndProceed}
            disabled={savingEmail}
            style={{width:'100%', padding:'10px 12px', background:'#2563eb', color:'#fff', border:'none', borderRadius:6, cursor:'pointer'}}
          >
            {savingEmail ? 'Saving…' : 'Continue'}
          </button>
          <p style={{marginTop:10, fontSize:12, color:'#6b7280'}}>We use your email for account recovery and store invitations.</p>
          <p style={{marginTop:8, fontSize:12}}>{status}</p>
        </div>
      )}
    </div>
  );
}
