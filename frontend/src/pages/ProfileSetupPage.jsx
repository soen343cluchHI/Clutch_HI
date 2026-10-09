import FormAlert from '../components/FormAlert';
import RolePicker from '../components/RolePicker';

export default function ProfileSetupPage({ roles, role, setRole, busy, error, onSubmit, onLogout }) {
  return (
    <section className="panel">
      <header className="panel-header">
        <h1>Finish setting up</h1>
        <p>You’re signed in, but your profile wasn’t saved. Choose a role and try again.</p>
      </header>
      <form className="stack" onSubmit={onSubmit}>
        <RolePicker roles={roles} value={role} onChange={setRole} />
        <FormAlert message={error} />
        <button className="btn btn-primary" disabled={busy}>{busy ? 'Saving…' : 'Save Profile'}</button>
      </form>
      <button className="btn btn-secondary" disabled={busy} onClick={onLogout}>Sign Out</button>
    </section>
  );
}
