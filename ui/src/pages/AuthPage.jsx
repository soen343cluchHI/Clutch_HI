import Field from '../components/Field';
import FormAlert from '../components/FormAlert';

export default function AuthPage({ registering, resettingPassword, resetSent, email, setEmail, password,
  setPassword, busy, error, onSubmit, onResetPassword, onForgotPassword, onBackToSignIn,
  onToggleMode, signupRole, onSignupRoleChange, restaurantName, setRestaurantName, authenticatedSignup = false }) {
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
            {['Customer', 'Restaurant', 'Courier', 'Operator'].map(option => <label className="role-option" key={option}>
              <input type="radio" name="signupRole" value={option} checked={signupRole === option}
                onChange={() => onSignupRoleChange(option)} />
              <span className="role-name">{option}</span>
              {option === 'Operator' && <span className="role-desc">Operator sign-up is available for this iteration only. Normally, operator accounts cannot be created through sign-up.</span>}
            </label>)}
          </div>
        </fieldset>}
        <Field label="Email" type="email" name="email" autoComplete="email" spellCheck={false} readOnly={authenticatedSignup} required value={email} onChange={(e) => setEmail(e.target.value)} />
        {!resettingPassword && !authenticatedSignup && <Field label="Password" type="password" name="password" required
          autoComplete={registering ? 'new-password' : 'current-password'} minLength={registering ? 6 : undefined}
          hint={registering ? 'At least 6 characters.' : undefined}
          value={password} onChange={(e) => setPassword(e.target.value)} />}
        {registering && signupRole === 'Restaurant' && !resettingPassword &&
          <Field label="Restaurant name (optional)" name="restaurantName" autoComplete="organization" maxLength={80}
            value={restaurantName} onChange={event => setRestaurantName(event.target.value)} />}
        {!registering && !resettingPassword && <button type="button" className="btn-link forgot-link" disabled={busy} onClick={onForgotPassword}>Forgot password?</button>}
        <FormAlert message={error} />
        {resetSent && <p className="form-success" role="status">If this email has an account, check your inbox for a reset link.</p>}
        <button className="btn btn-primary" disabled={busy}>
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
