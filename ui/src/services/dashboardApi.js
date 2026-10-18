async function post(firebaseUser, path, fields = {}, forceRefresh = false) {
  if (!firebaseUser) throw new Error('Sign in to use the dashboard.');
  const token = await firebaseUser.getIdToken(forceRefresh);
  let response;
  try {
    response = await fetch(`${import.meta.env.VITE_API_URL?.replace(/\/$/, '') || ''}${path}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        Authorization: `Bearer ${token}`,
      },
      body: new URLSearchParams(fields),
    });
  } catch {
    throw new Error('Cannot reach the order server. Retry shortly.');
  }
  if (response.status === 401 && !forceRefresh) return post(firebaseUser, path, fields, true);
  if (!response.headers.get('content-type')?.includes('application/json')) {
    throw new Error('The order server is unavailable. Retry shortly.');
  }
  const result = await response.json();
  if (!response.ok) throw new Error(result.error || 'The command failed.');
  return result;
}

export async function connect(firebaseUser) {
  const user = await post(firebaseUser, '/api/firebase-session');
  return { user, firebaseUser };
}

export function command(current, action, fields = {}) {
  return post(current.firebaseUser, '/api/command', { action, ...fields });
}
