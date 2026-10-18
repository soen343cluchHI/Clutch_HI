import FormAlert from '../components/FormAlert';

export default function AccountPage({ email, role, busy, error, onLogout }) {
  return (
    <section className="panel">
      <header className="panel-header">
        <h1>{role} dashboard</h1>
        <p>You’re signed in. Your workspace will appear here.</p>
      </header>
      <dl className="details">
        <div>
          <dt>Email</dt>
          <dd>{email}</dd>
        </div>
        <div>
          <dt>Role</dt>
          <dd>{role}</dd>
        </div>
      </dl>
      <FormAlert message={error} />
      <button className="btn btn-secondary" disabled={busy} onClick={onLogout}>
        Sign Out
      </button>
    </section>
  );
}
