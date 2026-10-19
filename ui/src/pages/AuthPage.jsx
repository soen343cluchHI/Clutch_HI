import { useState } from 'react';
import Field from '../components/Field';
import FormAlert from '../components/FormAlert';

export default function AuthPage({ registering, resettingPassword, resetSent, email, setEmail, password,
  setPassword, busy, error, onSubmit, onResetPassword, onForgotPassword, onBackToSignIn,
  onToggleMode, signupRole, onSignupRoleChange }) {
  const [details, setDetails] = useState({ restaurantName: '', operatorCode: '' });
  const specialSignup = registering && !resettingPassword && signupRole !== 'Customer';
  const updateDetail = name => event => setDetails(current => ({ ...current, [name]: event.target.value }));
  return (
    <section className="panel" key={resettingPassword ? 'reset' : registering ? 'register' : 'login'}>
      <header className="panel-header">
        <h1>{resettingPassword ? 'Reset your password' : registering ? 'Create your account' : 'Sign in'}</h1>
        <p>{resettingPassword ? 'Enter your email and we’ll send you a link to reset your password.'
          : registering ? 'Choose your account type and enter your details.'
            : 'Welcome back. Sign in with your email and password.'}</p>
      </header>
      <form className="stack" onSubmit={resettingPassword ? onResetPassword : onSubmit}>
        {registering && !resettingPassword && <fieldset className="role-picker" disabled={busy}>
          <legend>Account type</legend>
          <div className="role-grid signup-role-grid">
            {['Customer', 'Restaurant', 'Courier'].map(option => <label className="role-option" key={option}>
              <input type="radio" name="signupRole" value={option} checked={signupRole === option}
                onChange={() => { onSignupRoleChange(option); setDetails(current => ({ ...current, operatorCode: '' })); }} />
              <span className="role-name">{option}</span>
            </label>)}
          </div>
        </fieldset>}
        <Field label="Email" type="email" name="email" autoComplete="email" spellCheck={false} required value={email} onChange={(e) => setEmail(e.target.value)} />
        {!resettingPassword && <Field label="Password" type="password" name="password" required
          autoComplete={registering ? 'new-password' : 'current-password'} minLength={registering ? 6 : undefined}
          hint={registering ? 'At least 6 characters.' : undefined}
          value={password} onChange={(e) => setPassword(e.target.value)} />}
        {specialSignup && <>
          {signupRole === 'Restaurant' && <Field label="Restaurant name" name="restaurantName" autoComplete="organization" maxLength={80} required
            value={details.restaurantName} onChange={updateDetail('restaurantName')} />}
          <div className="operator-code-disabled" title="Operator approval not implemented yet" tabIndex={0} aria-label="Operator approval not implemented yet">
            <fieldset disabled>
              <Field label="Operator signup code" type="password" name="operatorCode" autoComplete="off" required
                hint="Use the code provided by your operator." value={details.operatorCode} onChange={updateDetail('operatorCode')} />
            </fieldset>
          </div>
        </>}
        {!registering && !resettingPassword && <button type="button" className="btn-link forgot-link" disabled={busy} onClick={onForgotPassword}>Forgot password?</button>}
        <FormAlert message={error} />
        {resetSent && <p className="form-success" role="status">If this email has an account, check your inbox for a reset link.</p>}
        <button className="btn btn-primary" disabled={busy || specialSignup}>
          {busy ? 'Please wait…' : resettingPassword ? 'Send reset link' : registering ? 'Create Account' : 'Sign In'}
        </button>
      </form>
      <p className="panel-footer">
        {resettingPassword ? <button type="button" className="btn-link" disabled={busy} onClick={onBackToSignIn}>Back to sign in</button>
          : <>{registering ? 'Already have an account?' : 'New to SugarRush?'}{' '}
            <button type="button" className="btn-link" disabled={busy} onClick={onToggleMode}>
              {registering ? 'Sign in' : 'Create an account'}
            </button></>}
      </p>
    </section>
  );
}
