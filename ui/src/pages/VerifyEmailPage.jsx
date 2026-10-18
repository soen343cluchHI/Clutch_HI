import FormAlert from '../components/FormAlert';

export default function VerifyEmailPage({ email, busy, error, sent, onCheck, onResend, onLogout }) {
  return (
    <section className="panel">
      <header className="panel-header">
        <h1>Verify your email</h1>
        <p>We sent a verification link to <strong>{email}</strong>. Open it, then come back and continue. Check your spam folder if you don’t see it.</p>
      </header>
      <FormAlert message={error} />
      {sent && <p className="form-success" role="status">A new verification email is on its way.</p>}
      <div className="stack">
        <button className="btn btn-primary" disabled={busy} onClick={onCheck}>
          {busy ? 'Please wait…' : 'I’ve verified my email'}
        </button>
        <button className="btn btn-secondary" disabled={busy} onClick={onResend}>
          Resend email
        </button>
      </div>
      <p className="panel-footer">
        Wrong account?{' '}
        <button type="button" className="btn-link" disabled={busy} onClick={onLogout}>Sign out</button>
      </p>
    </section>
  );
}
