import { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { createUserWithEmailAndPassword, deleteUser, EmailAuthProvider, onAuthStateChanged, reauthenticateWithCredential, sendEmailVerification, sendPasswordResetEmail, signInWithEmailAndPassword, signOut } from 'firebase/auth';
import { onValue, ref, remove, serverTimestamp, set } from 'firebase/database';
import { auth, database, configurationError } from './firebase';
import AuthLayout from './components/AuthLayout';
import AuthPage from './pages/AuthPage';
import ProfileSetupPage from './pages/ProfileSetupPage';
import DashboardPage from './pages/DashboardPage';
import StatusPage from './pages/StatusPage';
import VerifyEmailPage from './pages/VerifyEmailPage';
import '@fontsource-variable/geist';
import '@fontsource-variable/fraunces/full-italic.css';
import './styles/theme.css';
import './styles/auth.css';

function App() {
  const [user, setUser] = useState(null);
  const [verified, setVerified] = useState(false);
  const [verificationSent, setVerificationSent] = useState(false);
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [registering, setRegistering] = useState(false);
  const [resettingPassword, setResettingPassword] = useState(false);
  const [resetSent, setResetSent] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [profileError, setProfileError] = useState('');

  useEffect(() => {
    let stopProfile = () => {};
    const stopAuth = onAuthStateChanged(auth, (current) => {
      stopProfile();
      setUser(current);
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

  async function saveProfile(current) {
    await set(ref(database, `users/${current.uid}`), {
      email: current.email,
      role: 'Customer',
      createdAt: serverTimestamp(),
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
        await sendEmailVerification(credential.user);
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
    if (loading) return <StatusPage loading />;
    if (!user)
      return (
        <AuthPage
          registering={registering}
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
          onToggleMode={() => {
            setRegistering(!registering);
            setError('');
            setResetSent(false);
          }}
        />
      );
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
    if (profileError)
      return (
        <StatusPage
          title="Profile unavailable"
          message={`Could not load your profile: ${profileError}`}
          busy={busy}
          error={error}
          onLogout={logout}
        />
      );
    if (!profile) return <ProfileSetupPage busy={busy} error={error} onSubmit={submit} onLogout={logout} />;
    return (
      <DashboardPage
        key={user.uid}
        firebaseUser={user}
        email={user.email}
        busy={busy}
        error={error}
        onLogout={logout}
        onDeleteAccount={deleteAccount}
      />
    );
  }

  const isDashboard = !configurationError && !loading && user && verified && profile && !profileError;

  return isDashboard ? screen() : <AuthLayout>{screen()}</AuthLayout>;
}

createRoot(document.getElementById('root')).render(<App />);
