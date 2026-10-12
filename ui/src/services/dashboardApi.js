async function post(path, fields, token) {
  const response = await fetch(path, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: new URLSearchParams(fields),
  });
  const result = await response.json();
  if (!response.ok) throw new Error(result.error || 'The command failed.');
  return result;
}

export function login(username, password) {
  return post('/api/login', { username, password });
}

export function command(token, action, fields = {}) {
  return post('/api/command', { action, ...fields }, token);
}
