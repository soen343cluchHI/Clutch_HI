// Local development shortcuts for real Firebase test accounts. Credentials are read from ui/.env.local
// (ignored by git), for example:
// VITE_DEMO_ACCOUNTS='[{"label":"Courier","email":"courier@example.com","password":"secret"}]'
const roles = ['Customer', 'Restaurant', 'Courier', 'Operator'];

// The DEV ternary folds to '' in production builds, so credentials are never bundled even if set.
const raw = import.meta.env.DEV ? import.meta.env.VITE_DEMO_ACCOUNTS : '';

function configured() {
  try {
    const list = JSON.parse(raw || '[]');
    return Array.isArray(list) ? list.filter((account) => account?.label && account?.email && account?.password) : [];
  } catch {
    return [];
  }
}

const accounts = configured();

// Without credentials the buttons still show (disabled) so it is clear where to add them.
export const demoAccounts = !import.meta.env.DEV ? [] : accounts.length ? accounts : roles.map((label) => ({ label }));
