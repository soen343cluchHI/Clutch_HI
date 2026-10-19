import Logo from './Logo';

export default function AuthLayout({ children }) {
  return (
    <div className="auth-layout">
      <header className="auth-brand">
        <Logo size={52} />
        <p className="brand-tagline">
          Every order, from the kitchen <span>to the door.</span>
        </p>
      </header>
      <main className="auth-main">{children}</main>
    </div>
  );
}
