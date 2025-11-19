import React, { useEffect, useState } from 'react';
import { supabase } from '../config/supabaseClient';
import { useNavigate } from 'react-router-dom';

const extractOAuthError = () => {
  if (typeof window === 'undefined') return null;
  const buckets = [];
  if (window.location.search) buckets.push(window.location.search.substring(1));
  if (window.location.hash) buckets.push(window.location.hash.substring(1));
  for (const raw of buckets) {
    if (!raw) continue;
    const params = new URLSearchParams(raw);
    const message = params.get('error_description') || params.get('error');
    if (message) {
      return message.replace(/\+/g, ' ');
    }
  }
  return null;
};

export default function AuthCallback() {
  const [status, setStatus] = useState('Signing you in');
  const [needEmail, setNeedEmail] = useState(false);
  const [emailInput, setEmailInput] = useState('');
  const [savingEmail, setSavingEmail] = useState(false);
  const navigate = useNavigate();

  const fetchFacebookEmail = async (session) => {
    const accessToken = session?.provider_token;
    if (!accessToken) return null;
    try {
      const resp = await fetch(`https://graph.facebook.com/me?fields=email&access_token=${accessToken}`);
      if (!resp.ok) return null;
      const data = await resp.json();
      const email = data?.email;
      if (!email) return null;
      await supabase.auth.updateUser({ email });
      await supabase
        .from('app_users')
        .upsert({
          id: session.user.id,
          auth_uid: session.user.id,
          email,
          name: session.user.user_metadata?.name || null
        }, { onConflict: 'id' });
      return email;
    } catch {
      return null;
    }
  };

  useEffect(() => {
    const oauthError = extractOAuthError();
    if (oauthError) {
      setStatus(oauthError);
      const timer = setTimeout(() => {
        navigate('/login', { replace: true, state: { oauthError } });
      }, 1500);
      return () => clearTimeout(timer);
    }

    let subscription;
    let timer;
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
          let finalEmail = userEmail;
          if (!finalEmail && session.provider === 'facebook') {
            finalEmail = await fetchFacebookEmail(session);
          }
          if (!finalEmail) {
            setNeedEmail(true);
            setStatus('Facebook signed in. Please provide your email to continue.');
            return;
          }
          await supabase
            .from('app_users')
            .upsert(
              {
                id: user.id,
                email: finalEmail,
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
      const listener = supabase.auth.onAuthStateChange((_event, session) => {
      if (session) {
        // Upsert profile as soon as session appears
        (async () => {
          try {
            let userEmail = session.user.email || null;
            if (!userEmail && session.provider === 'facebook') {
              userEmail = await fetchFacebookEmail(session);
            }
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
      subscription = listener.data.subscription;
      timer = setTimeout(() => setStatus('No session found. Please try logging in again.'), 4000);
    };
    run();
    return () => {
      subscription?.unsubscribe();
      if (timer) clearTimeout(timer);
    };
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
