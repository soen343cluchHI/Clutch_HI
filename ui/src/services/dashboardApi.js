const apiBase = () => import.meta.env.VITE_API_URL?.replace(/\/$/, '') || '';
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const GATEWAY_ERRORS = new Set([502, 503, 504]);
// Safe to send twice: they only read state or record the same sign-up again.
const repeatable = (path, fields) => path !== '/api/command' || /^(SESSION_INFO|ROLE_MENU|BROWSE_RESTAURANTS|LIST_ORDERS|CITY_MAP|LIST_LEDGER|COURIER_STATUS|COURIER_OFFER|PRICE_QUOTE|RESTAURANT_QUEUE|VIEW_PROFILE|VIEW_ORDER_SUMMARY|DASHBOARD|EVENT_LOG|COMMAND_LOG)$/.test(fields.action || '');

async function post(firebaseUser, path, fields = {}, forceRefresh = false, attempt = 0) {
  if (!firebaseUser) throw new Error('Sign in to use the dashboard.');
  const authorization = `Bearer ${await firebaseUser.getIdToken(forceRefresh)}`;
  const retry = async () => {
    await wait(1500 * (attempt + 1));
    return post(firebaseUser, path, fields, forceRefresh, attempt + 1);
  };
  let response;
  try {
    response = await fetch(`${apiBase()}${path}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        Authorization: authorization,
      },
      body: new URLSearchParams(fields),
    });
  } catch {
    // The request may or may not have arrived, so only repeat it when doing so cannot change anything twice.
    if (attempt < 2 && repeatable(path, fields)) return retry();
    throw new Error('Cannot reach the order server. Retry shortly.');
  }
  if (response.status === 401 && !forceRefresh) return post(firebaseUser, path, fields, true, attempt);
  if (!response.headers.get('content-type')?.includes('application/json')) {
    // A gateway error page (for example while the hosted server wakes up) means the command never ran.
    if (attempt < 3 && GATEWAY_ERRORS.has(response.status)) return retry();
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

// The backend binds enrollment to the signed token's UID, never a supplied UID.
export function registerAccount(firebaseUser, requestedRole, restaurantName = '') {
  return post(firebaseUser, '/api/register-account', {
    role: requestedRole.toUpperCase(),
    restaurantName: requestedRole === 'Restaurant' ? restaurantName.trim() : '',
  });
}
