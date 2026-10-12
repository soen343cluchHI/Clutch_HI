import { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { createUserWithEmailAndPassword, onAuthStateChanged, signInWithEmailAndPassword, signOut } from 'firebase/auth';
import { onValue, ref, serverTimestamp, set } from 'firebase/database';
import { auth, database, configurationError } from './firebase';
import AuthLayout from './components/AuthLayout';
import AuthPage from './pages/AuthPage';
import ProfileSetupPage from './pages/ProfileSetupPage';
import DashboardPage from './pages/DashboardPage';
import StatusPage from './pages/StatusPage';
import '@fontsource-variable/geist';
import './styles/theme.css';
import './styles/auth.css';

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

  function screen() {
    if (configurationError) return <StatusPage title="Setup required" message={configurationError} />;
    if (loading) return <StatusPage loading />;
    if (!user) return <AuthPage
      registering={registering} email={email} setEmail={setEmail} password={password} setPassword={setPassword}
      roles={roles} role={role} setRole={setRole} busy={busy} error={error} onSubmit={submit}
      onToggleMode={() => { setRegistering(!registering); setError(''); }} />;
    if (profileError) return <StatusPage title="Profile unavailable" message={`Could not load your profile: ${profileError}`} busy={busy} error={error} onLogout={logout} />;
    if (!profile) return <ProfileSetupPage roles={roles} role={role} setRole={setRole} busy={busy} error={error} onSubmit={submit} onLogout={logout} />;
    if (!roles.includes(profile.role)) return <StatusPage title="Profile unavailable" message="Your profile has an invalid role. Contact the project team." busy={busy} error={error} onLogout={logout} />;
    return <DashboardPage key={user.uid} firebaseUser={user} profileRole={profile.role} email={user.email} busy={busy} error={error} onLogout={logout} />;
  }

  const isDashboard =
  !configurationError &&
  !loading &&
  user &&
  profile &&
  !profileError &&
  roles.includes(profile.role);

return isDashboard ? screen() : <AuthLayout>{screen()}</AuthLayout>;
}

createRoot(document.getElementById('root')).render(<App />);
