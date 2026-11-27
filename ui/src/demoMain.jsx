import { useState } from 'react';
import { createRoot } from 'react-dom/client';
import AuthLayout from './components/AuthLayout';
import DashboardPage from './pages/DashboardPage';
import '@fontsource-variable/geist';
import '@fontsource-variable/fraunces/full-italic.css';
import './styles/theme.css';
import './styles/auth.css';

const roles = ['customer', 'restaurant', 'courier', 'operator'];

function DemoApp() {
  const [username, setUsername] = useState('customer@demo.local');
  const [password, setPassword] = useState('demo1234');
  const [user, setUser] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function signIn(event) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      const response = await fetch('/api/demo-login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ username, password }),
      });
      const result = await response.json();
      if (!response.ok) throw new Error(result.error || 'Sign in failed');
      setUser({ uid: result.username, email: result.username, getIdToken: async () => result.token });
    } catch (failure) {
      setError(failure.message);
    } finally {
      setBusy(false);
    }
  }

  if (user) return <DashboardPage firebaseUser={user} onLogout={() => setUser(null)} busy={false} error="" />;
  return <AuthLayout>
    <section className="panel">
      <header className="panel-header">
        <h1>Demo sign in</h1>
        <p>Choose a role to try the ordering workflow. These accounts contain no real user data.</p>
      </header>
      <form className="stack" onSubmit={signIn}>
        <label>Account
          <select value={username} onChange={(event) => setUsername(event.target.value)}>
            {roles.map((role) => <option key={role} value={`${role}@demo.local`}>{role[0].toUpperCase() + role.slice(1)}</option>)}
          </select>
        </label>
        <label>Password
          <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} required />
        </label>
        {error && <p role="alert">{error}</p>}
        <button className="btn btn-primary" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
      </form>
      <p>All demo accounts use <code>demo1234</code>.</p>
    </section>
  </AuthLayout>;
}

createRoot(document.getElementById('root')).render(<DemoApp />);
