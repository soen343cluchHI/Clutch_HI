const friendlyMessages = {
  'invalid-credential': 'That email and password don’t match. Check them and try again.',
  'email-already-in-use': 'An account with this email already exists. Sign in instead.',
  'invalid-email': 'Enter a valid email address, like name@example.com.',
  'weak-password': 'Use a password with at least 6 characters.',
  'too-many-requests': 'Too many attempts. Wait a minute, then try again.',
  'network-request-failed': 'Can’t reach the server. Check your connection and try again.',
};

export default function FormAlert({ message }) {
  if (!message) return null;
  const code = message.match(/\(auth\/([\w-]+)\)/)?.[1];
  return <p className="form-alert" role="alert">{friendlyMessages[code] ?? message}</p>;
}
