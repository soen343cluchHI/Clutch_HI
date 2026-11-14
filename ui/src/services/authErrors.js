// Plain-language messages for Firebase errors instead of codes such as "Firebase: Error (auth/email-already-in-use)".
const messages = {
  'auth/email-already-in-use': 'An account already exists for this email. Sign in instead, or use “Forgot password?”.',
  'auth/invalid-email': 'Enter a valid email address.',
  'auth/missing-email': 'Enter your email address.',
  'auth/weak-password': 'Choose a password with at least 6 characters.',
  'auth/missing-password': 'Enter your password.',
  'auth/invalid-credential': 'Incorrect email or password.',
  'auth/invalid-login-credentials': 'Incorrect email or password.',
  'auth/wrong-password': 'Incorrect email or password.',
  'auth/user-not-found': 'Incorrect email or password.',
  'auth/user-disabled': 'This account has been disabled.',
  'auth/too-many-requests': 'Too many attempts. Wait a few minutes, then try again.',
  'auth/network-request-failed': 'Cannot reach Firebase. Check your internet connection and retry.',
  'auth/requires-recent-login': 'For security, sign out and sign in again first.',
  'auth/operation-not-allowed': 'Email sign-in is turned off for this Firebase project.',
};

export function friendlyError(failure) {
  const code = failure?.code || '';
  if (messages[code]) return messages[code];
  if (/permission.?denied/i.test(`${code} ${failure?.message || ''}`))
    return 'Your account details could not be saved because access was denied. Please retry or contact the project administrator.';
  return failure?.message || 'Something went wrong. Please retry.';
}
