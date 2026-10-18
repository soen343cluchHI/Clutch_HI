export default function AuthLayout({ children }) {
  return (
    <div className="auth-layout">
      <aside className="auth-brand">
        <p className="wordmark" translate="no">
          SugarRush
        </p>
        <p className="brand-tagline">Every order, from the kitchen to the door.</p>
      </aside>
      <main className="auth-main">{children}</main>
    </div>
  );
}
