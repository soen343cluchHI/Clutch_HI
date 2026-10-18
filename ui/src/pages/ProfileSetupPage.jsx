import FormAlert from '../components/FormAlert';

export default function ProfileSetupPage({ busy, error, onSubmit, onLogout, role, setRole }) {
  return (
    <section className="panel">
      <header className="panel-header">
        <h1>Finish setting up</h1>
        <p>You’re signed in, but your profile wasn’t saved. Save your profile to continue.</p>
      </header>
      <form className="stack" onSubmit={onSubmit}>
        <fieldset className="role-picker"><legend>Account type</legend><div className="role-grid">
          {['Customer', 'Restaurant', 'Courier', 'Operator'].map((option) => <label className="role-option" key={option}>
            <input type="radio" name="role" value={option} checked={role === option} onChange={() => setRole(option)} disabled={busy} />
            <span className="role-name">{option}</span>
          </label>)}
        </div></fieldset>
        <FormAlert message={error} />
        <button className="btn btn-primary" disabled={busy}>
          {busy ? 'Saving…' : 'Save Profile'}
        </button>
      </form>
      <button className="btn btn-secondary" disabled={busy} onClick={onLogout}>
        Sign Out
      </button>
    </section>
  );
}
