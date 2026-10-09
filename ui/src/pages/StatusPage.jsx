import FormAlert from '../components/FormAlert';

export default function StatusPage({ loading, title, message, busy, error, onLogout }) {
  if (loading) {
    return (
      <section className="panel" aria-busy="true">
        <span className="visually-hidden" role="status">Loading…</span>
        <div className="skeleton skeleton-title" />
        <div className="skeleton skeleton-line" />
        <div className="skeleton skeleton-input" />
        <div className="skeleton skeleton-input" />
      </section>
    );
  }

  return (
    <section className="panel">
      <header className="panel-header">
        <h1>{title}</h1>
        <p role="alert">{message}</p>
      </header>
      <FormAlert message={error} />
      {onLogout && <button className="btn btn-secondary" disabled={busy} onClick={onLogout}>Sign Out</button>}
    </section>
  );
}
