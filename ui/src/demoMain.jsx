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
  const [registering, setRegistering] = useState(false);
  const [username, setUsername] = useState('customer@demo.local');
  const [password, setPassword] = useState('demo1234');
  const [role, setRole] = useState('CUSTOMER');
  const [restaurantName, setRestaurantName] = useState('');
  const [user, setUser] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      const response = await fetch(registering ? '/api/demo-register' : '/api/demo-login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams(registering ? { username, password, role, restaurantName } : { username, password }),
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

  if (user) return <DashboardPage firebaseUser={user} onLogout={() => { setUser(null); setRegistering(false); }} busy={false} error="" />;
  return <AuthLayout>
    <section className="panel">
      <header className="panel-header">
        <h1>{registering ? 'Create demo account' : 'Demo sign in'}</h1>
        <p>{registering ? 'Use a fictional email address. Operator accounts cannot self-register.' : 'Sign in with a built-in account or one you created here.'}</p>
      </header>
      <form className="stack" onSubmit={submit}>
        <label>Email
          <input type="email" value={username} onChange={(event) => setUsername(event.target.value)} list={registering ? undefined : 'demo-accounts'} required maxLength={254} autoComplete="email" />
          {!registering && <datalist id="demo-accounts">
            {roles.map((account) => <option key={account} value={`${account}@demo.local`} />)}
          </datalist>}
        </label>
        <label>Password
          <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} required minLength={registering ? 6 : undefined} autoComplete={registering ? 'new-password' : 'current-password'} />
        </label>
        {registering && <label>Account type
          <select value={role} onChange={(event) => setRole(event.target.value)}>
            <option value="CUSTOMER">Customer</option>
            <option value="RESTAURANT">Restaurant</option>
            <option value="COURIER">Courier</option>
          </select>
        </label>}
        {registering && role === 'RESTAURANT' && <label>Restaurant name
          <input value={restaurantName} onChange={(event) => setRestaurantName(event.target.value)} required maxLength={80} />
        </label>}
        {error && <p role="alert">{error}</p>}
        <button className="btn btn-primary" disabled={busy}>{busy ? 'Please wait…' : registering ? 'Create account' : 'Sign in'}</button>
      </form>
      <button type="button" className="btn-link" disabled={busy} onClick={() => {
        setRegistering(!registering);
        setUsername(registering ? 'customer@demo.local' : '');
        setPassword(registering ? 'demo1234' : '');
        setError('');
      }}>{registering ? 'Back to sign in' : 'Create an account'}</button>
      <p>Built-in demo accounts use <code>demo1234</code>.</p>
    </section>
  </AuthLayout>;
}

createRoot(document.getElementById('root')).render(<DemoApp />);
