import Field from '../components/Field';
import FormAlert from '../components/FormAlert';
import RolePicker from '../components/RolePicker';

export default function AuthPage({ registering, email, setEmail, password, setPassword, roles, role, setRole, busy, error, onSubmit, onToggleMode }) {
  return (
    <section className="panel" key={registering ? 'register' : 'login'}>
      <header className="panel-header">
        <h1>{registering ? 'Create your account' : 'Sign in'}</h1>
        <p>{registering ? 'Set up your login and choose how you’ll use SugarRush.' : 'Welcome back. Sign in with your email and password.'}</p>
      </header>
      <form className="stack" onSubmit={onSubmit}>
        <Field label="Email" type="email" name="email" autoComplete="email" spellCheck={false} required value={email} onChange={e => setEmail(e.target.value)} />
        <Field
          label="Password" type="password" name="password" required
          autoComplete={registering ? 'new-password' : 'current-password'}
          minLength={registering ? 6 : undefined}
          hint={registering ? 'At least 6 characters.' : undefined}
          value={password} onChange={e => setPassword(e.target.value)}
        />
        {registering && <RolePicker roles={roles} value={role} onChange={setRole} />}
        <FormAlert message={error} />
        <button className="btn btn-primary" disabled={busy}>
          {busy ? 'Please wait…' : registering ? 'Create Account' : 'Sign In'}
        </button>
      </form>
      <p className="panel-footer">
        {registering ? 'Already have an account?' : 'New to SugarRush?'}{' '}
        <button type="button" className="btn-link" disabled={busy} onClick={onToggleMode}>
          {registering ? 'Sign in' : 'Create an account'}
        </button>
      </p>
    </section>
  );
}
