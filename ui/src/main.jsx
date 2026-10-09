import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { createUserWithEmailAndPassword, onAuthStateChanged, signInWithEmailAndPassword, signOut } from 'firebase/auth';
import { onValue, ref, serverTimestamp, set } from 'firebase/database';
import { auth, database, configurationError } from './firebase';
import './style.css';

const roles = ['Customer', 'Restaurant', 'Courier', 'Operator'];

function App() {
  const [user, setUser] = useState(null);
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [registering, setRegistering] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState('Customer');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [profileError, setProfileError] = useState('');

  useEffect(() => {
    let stopProfile = () => {};
    const stopAuth = onAuthStateChanged(auth, current => {
      stopProfile();
      setUser(current);
      setProfile(null);
      setProfileError('');
      setLoading(Boolean(current && database));
      if (current && database) {
        stopProfile = onValue(ref(database, `users/${current.uid}`), snapshot => {
          setProfile(snapshot.val());
          setLoading(false);
        }, failure => {
          setProfileError(failure.message);
          setLoading(false);
        });
      }
    });
    return () => { stopAuth(); stopProfile(); };
  }, []);

  async function saveProfile(current) {
    await set(ref(database, `users/${current.uid}`), {
      email: current.email, role, createdAt: serverTimestamp(),
    });
  }

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      if (user) {
        await saveProfile(user);
      } else if (registering) {
        const credential = await createUserWithEmailAndPassword(auth, email.trim(), password);
        await saveProfile(credential.user);
      } else {
        await signInWithEmailAndPassword(auth, email.trim(), password);
      }
    } catch (failure) {
      setError(failure.message);
    } finally {
      setPassword('');
      setBusy(false);
    }
  }

  async function logout() {
    setBusy(true);
    setError('');
    try { await signOut(auth); } catch (failure) { setError(failure.message); }
    finally { setBusy(false); }
  }

  if (configurationError) return <main><h1>SugarRush setup</h1><p role="alert">{configurationError}</p></main>;
  if (loading) return <main><p>Loading…</p></main>;

  return <main>
    <h1>SugarRush</h1>
    {error && <p role="alert">{error}</p>}
    {user ? <>
      {profileError ? <p role="alert">Could not load your profile: {profileError}</p> :
        profile ? (roles.includes(profile.role) ? <>
          <h2>{profile.role} Dashboard</h2>
          <p>Email: {user.email}</p><p>Role: {profile.role}</p>
        </> : <p role="alert">Your profile has an invalid role. Contact the project team.</p>) : <>
          <h2>Complete registration</h2>
          <p>Your account is signed in, but your profile has not been saved. Choose a role and retry.</p>
          <form onSubmit={submit}>
            <RoleSelect role={role} onChange={setRole} />
            <button disabled={busy}>Save profile</button>
          </form>
        </>}
      <button onClick={logout} disabled={busy}>Logout</button>
    </> : <>
      <h2>{registering ? 'Register' : 'Login'}</h2>
      <form onSubmit={submit}>
        <label>Email<input type="email" autoComplete="email" required value={email} onChange={e => setEmail(e.target.value)} /></label>
        <label>Password<input type="password" autoComplete={registering ? 'new-password' : 'current-password'} minLength={registering ? 6 : undefined} required value={password} onChange={e => setPassword(e.target.value)} /></label>
        {registering && <RoleSelect role={role} onChange={setRole} />}
        <button disabled={busy}>{busy ? 'Please wait…' : registering ? 'Register' : 'Login'}</button>
      </form>
      <button disabled={busy} onClick={() => { setRegistering(!registering); setError(''); }}>{registering ? 'Back to login' : 'Create an account'}</button>
    </>}
  </main>;
}

function RoleSelect({ role, onChange }) {
  return <label>Role<select value={role} onChange={e => onChange(e.target.value)}>
    {roles.map(value => <option key={value}>{value}</option>)}
  </select></label>;
}

createRoot(document.getElementById('root')).render(<App />);
