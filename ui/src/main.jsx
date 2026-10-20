import { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { createUserWithEmailAndPassword, deleteUser, EmailAuthProvider, onAuthStateChanged, reauthenticateWithCredential, sendEmailVerification, sendPasswordResetEmail, signInWithEmailAndPassword, signOut } from 'firebase/auth';
import { get, onValue, ref, remove, serverTimestamp, set } from 'firebase/database';
import { auth, database, configurationError } from './firebase';
import AuthLayout from './components/AuthLayout';
import AuthPage from './pages/AuthPage';
import DashboardPage from './pages/DashboardPage';
import StatusPage from './pages/StatusPage';
import VerifyEmailPage from './pages/VerifyEmailPage';
import { registerAccount } from './services/dashboardApi';
import '@fontsource-variable/geist';
import '@fontsource-variable/fraunces/full-italic.css';
import './styles/theme.css';
import './styles/auth.css';

function readSignupDraft(uid) {
  try {
    const stored = localStorage.getItem(`signup:${uid}`) || sessionStorage.getItem(`signup:${uid}`);
    return stored ? JSON.parse(stored) : null;
  } catch { return null; }
}

function signupError(failure) {
  if (/permission.?denied/i.test(`${failure.code || ''} ${failure.message || ''}`)) {
    return 'Your account details could not be saved because access was denied. Please retry or contact the project administrator.';
  }
  return failure.message;
}

function App() {
  const [user, setUser] = useState(null);
  const [verified, setVerified] = useState(false);
  const [verificationSent, setVerificationSent] = useState(false);
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [registering, setRegistering] = useState(false);
  const [signupRole, setSignupRole] = useState('Customer');
  const [restaurantName, setRestaurantName] = useState('');
  const [accountReady, setAccountReady] = useState(false);
  const [provisionError, setProvisionError] = useState('');
  const [provisionAttempt, setProvisionAttempt] = useState(0);
  const [resettingPassword, setResettingPassword] = useState(false);
  const [resetSent, setResetSent] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [profileError, setProfileError] = useState('');
  const [savingSignup, setSavingSignup] = useState(false);
  const [signupFailure, setSignupFailure] = useState('');

  useEffect(() => {
    let stopProfile = () => {};
    const stopAuth = onAuthStateChanged(auth, (current) => {
      stopProfile();
      if (current) {
        setEmail(current.email || '');
        try {
          const draft = readSignupDraft(current.uid);
          setSignupRole(draft?.requestedRole || 'Customer');
          setRestaurantName(draft?.restaurantName || '');
        } catch { setSignupRole('Customer'); setRestaurantName(''); }
      }
      setUser(current);
      setAccountReady(false);
      setProvisionError('');
      setVerified(Boolean(current?.emailVerified));
      setVerificationSent(false);
      setProfile(null);
      setProfileError('');
      setLoading(Boolean(current && database));
      if (current && database) {
        stopProfile = onValue(
          ref(database, `users/${current.uid}`),
          (snapshot) => {
            setProfile(snapshot.val());
            setLoading(false);
          },
          (failure) => {
            setProfileError(failure.message);
            setLoading(false);
          },
        );
      }
    });
    return () => {
      stopAuth();
      stopProfile();
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    setAccountReady(false);
    setProvisionError('');
    if (!user || !verified || !profile) return;
    // Older/demo accounts keep the existing server-approved UID mappings.
    if (!profile.requestedRole) { setAccountReady(true); return; }
    registerAccount(user, profile.requestedRole, profile.restaurantName || '')
      .then(() => {
        if (!cancelled) {
          sessionStorage.removeItem(`signup:${user.uid}`);
          localStorage.removeItem(`signup:${user.uid}`);
          setAccountReady(true);
        }
      })
      .catch(failure => { if (!cancelled) setProvisionError(failure.message); });
    return () => { cancelled = true; };
  }, [user, verified, profile, provisionAttempt]);

  useEffect(() => {
    if (!user || !profile || verified || savingSignup) return;
    let checking = false;
    let cancelled = false;
    async function refreshVerification() {
      if (checking || document.visibilityState === 'hidden') return;
      checking = true;
      try {
        await user.reload();
        if (user.emailVerified) {
          await user.getIdToken(true);
          if (!cancelled) setVerified(true);
        }
      } catch { /* The verification screen still offers a manual check/retry. */ }
      finally { checking = false; }
    }
    window.addEventListener('focus', refreshVerification);
    document.addEventListener('visibilitychange', refreshVerification);
    return () => {
      cancelled = true;
      window.removeEventListener('focus', refreshVerification);
      document.removeEventListener('visibilitychange', refreshVerification);
    };
  }, [user, profile, verified, savingSignup]);

  async function saveProfile(current, draft = null) {
    // Enrollment (including restaurant creation) must finish before verification mail.
    if (draft) await registerAccount(current, draft.requestedRole, draft.restaurantName || '');
    const destination = ref(database, `users/${current.uid}`);
    if (!(await get(destination)).exists()) {
      // Keep metadata compatible with existing profile rules; the backend registry owns role/name.
      await set(destination, {
        email: current.email,
        role: 'Customer',
        createdAt: serverTimestamp(),
      });
    }
    const saved = await get(destination);
    setProfile(saved.val());
    setProfileError('');
    sessionStorage.removeItem(`signup:${current.uid}`);
    localStorage.removeItem(`signup:${current.uid}`);
  }

  useEffect(() => {
    if (!user || loading || profile || profileError || savingSignup || signupFailure) return;
    // Recover interrupted signup automatically from its original details, without a second form.
    setSavingSignup(true);
    const draft = readSignupDraft(user.uid);
    saveProfile(user, draft)
      .then(async () => {
        if (!user.emailVerified) { await sendEmailVerification(user); setVerificationSent(true); }
      })
      .catch(failure => { const message = signupError(failure); setSignupFailure(message); setError(message); })
      .finally(() => setSavingSignup(false));
  }, [user, loading, profile, profileError, savingSignup, signupFailure]);

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setSavingSignup(registering || Boolean(user && !profile));
    setSignupFailure('');
    setError('');
    try {
      if (user) {
        const draft = { requestedRole: signupRole, restaurantName: signupRole === 'Restaurant' ? restaurantName.trim() : '' };
        localStorage.setItem(`signup:${user.uid}`, JSON.stringify(draft));
        await saveProfile(user, draft);
        if (!user.emailVerified) { await sendEmailVerification(user); setVerificationSent(true); }
      } else if (registering) {
        const draft = { requestedRole: signupRole, restaurantName: signupRole === 'Restaurant' ? restaurantName.trim() : '' };
        const credential = await createUserWithEmailAndPassword(auth, email.trim(), password);
        localStorage.setItem(`signup:${credential.user.uid}`, JSON.stringify(draft));
        setSignupRole(draft.requestedRole);
        setRestaurantName(draft.restaurantName);
        await saveProfile(credential.user, draft);
        await sendEmailVerification(credential.user);
        setVerificationSent(true);
      } else {
        await signInWithEmailAndPassword(auth, email.trim(), password);
      }
    } catch (failure) {
      const message = signupError(failure);
      setError(message);
      if (registering || user) setSignupFailure(message);
    } finally {
      setPassword('');
      setBusy(false);
      setSavingSignup(false);
    }
  }

  async function logout() {
    setBusy(true);
    setError('');
    setSignupFailure('');
    try {
      await signOut(auth);
    } catch (failure) {
      setError(failure.message);
    } finally {
      setBusy(false);
    }
  }

  async function resetPassword(event) {
    event.preventDefault();
    setBusy(true);
    setError('');
    setResetSent(false);
    try {
      await sendPasswordResetEmail(auth, email.trim());
      setResetSent(true);
    } catch (failure) {
      setError(failure.message);
    } finally {
      setBusy(false);
    }
  }

  async function checkVerification() {
    setBusy(true);
    setError('');
    setVerificationSent(false);
    try {
      await user.reload();
      if (!auth.currentUser.emailVerified) throw new Error('Your email is not verified yet. Open the link we sent you, then try again.');
      await auth.currentUser.getIdToken(true);
      setVerified(true);
    } catch (failure) {
      setError(failure.message);
    } finally {
      setBusy(false);
    }
  }

  async function resendVerification() {
    setBusy(true);
    setError('');
    setVerificationSent(false);
    try {
      await sendEmailVerification(user);
      setVerificationSent(true);
    } catch (failure) {
      setError(failure.message);
    } finally {
      setBusy(false);
    }
  }

  async function deleteAccount(confirmationPassword) {
    setBusy(true);
    setError('');
    try {
      await reauthenticateWithCredential(user, EmailAuthProvider.credential(user.email, confirmationPassword));
      await remove(ref(database, `users/${user.uid}`));
      await deleteUser(user);
      setProfile(null);
    } catch (failure) {
      setError(failure.code === 'auth/requires-recent-login'
        ? 'For security, sign out and sign in again before deleting your account.'
        : failure.message);
    } finally {
      setBusy(false);
    }
  }

  function screen() {
    if (configurationError) return <StatusPage title="Setup required" message={configurationError} />;
    if (loading || savingSignup) return <StatusPage loading />;
    if (!user || (!profile && signupFailure))
      return (
        <AuthPage
          registering={Boolean(user) || registering}
          authenticatedSignup={Boolean(user)}
          restaurantName={restaurantName}
          setRestaurantName={setRestaurantName}
          signupRole={signupRole}
          onSignupRoleChange={value => { setSignupRole(value); setError(''); }}
          resettingPassword={resettingPassword}
          resetSent={resetSent}
          email={email}
          setEmail={setEmail}
          password={password}
          setPassword={setPassword}
          busy={busy}
          error={error}
          onSubmit={submit}
          onResetPassword={resetPassword}
          onForgotPassword={() => {
            setResettingPassword(true);
            setResetSent(false);
            setError('');
            setPassword('');
          }}
          onBackToSignIn={() => {
            setResettingPassword(false);
            setResetSent(false);
            setError('');
          }}
          onToggleMode={async () => {
            if (user) { await logout(); setRegistering(false); }
            else setRegistering(!registering);
            setSignupRole('Customer');
            setRestaurantName('');
            setError('');
            setResetSent(false);
          }}
        />
      );
    if (profileError)
      return (
        <StatusPage
          title="Profile unavailable"
          message={signupError({ message: profileError })}
          busy={busy}
          error={error}
          onLogout={logout}
        />
      );
    if (!profile) return <StatusPage loading />;
    if (!verified)
      return (
        <VerifyEmailPage
          email={user.email}
          busy={busy}
          error={error}
          sent={verificationSent}
          onCheck={checkVerification}
          onResend={resendVerification}
          onLogout={logout}
        />
      );
    if (provisionError) return <section className="panel">
      <header className="panel-header"><h1>Account setup incomplete</h1><p>{provisionError}</p></header>
      <button className="btn btn-primary" onClick={() => setProvisionAttempt(value => value + 1)}>Retry account setup</button>
      <button className="btn btn-secondary" onClick={logout} disabled={busy}>Sign out</button>
    </section>;
    if (!accountReady) return <StatusPage loading />;
    return (
      <DashboardPage
        key={user.uid}
        firebaseUser={user}
        busy={busy}
        error={error}
        onLogout={logout}
        onDeleteAccount={deleteAccount}
      />
    );
  }

  const isDashboard = !configurationError && !loading && user && verified && profile && !profileError && accountReady && !savingSignup;

  return isDashboard ? screen() : <AuthLayout>{screen()}</AuthLayout>;
}

createRoot(document.getElementById('root')).render(<App />);
