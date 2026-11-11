import { useEffect, useRef, useState } from 'react';
import '@fontsource-variable/fraunces';
import ConsoleDrawer from '../components/ConsoleDrawer';
import CourierView from '../components/CourierView';
import CustomerView from '../components/CustomerView';
import Logo from '../components/Logo';
import OperatorView from '../components/OperatorView';
import ProfilePanel from '../components/ProfilePanel';
import RestaurantView from '../components/RestaurantView';
import * as api from '../services/dashboardApi';
import { canExecute, emptyWorkspace, loadWorkspace, workspaceActions } from '../services/dashboardWorkspace';
import '../styles/feed.css';

// Only the tabs (and so the commands) of the signed-in role are shown.
const roleTabs = {
  CUSTOMER: ['Home', 'Orders', 'Receipts'],
  RESTAURANT: ['Today', 'Menu', 'History'],
  COURIER: ['Shift', 'History'],
  OPERATOR: ['Live', 'Orders', 'Couriers', 'Ledger'],
};
const POLL_MS = 2000;

const money = (value) => new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' }).format(value);

function describe(action, result) {
  if (result?.state && result?.id) return `Order ${result.id.slice(0, 8)} → ${result.state}`;
  if (result?.status && typeof result?.id === 'number') return `Courier C${result.id} → ${result.status}${result.orderId ? ` (order ${result.orderId.slice(0, 8)})` : ''}`;
  if (typeof result === 'string') return result;
  if (action === 'CREATE_RESTAURANT' && result?.name) return `Created ${result.name}`;
  if (action === 'VIEW_ORDER_SUMMARY' && result?.totalCharged) return `Summary for ${result.orderId.slice(0, 8)} · charged ${money(result.totalCharged)}`;
  return 'Completed';
}

export default function DashboardPage({ firebaseUser, onLogout, onDeleteAccount, busy: authBusy, error: authError }) {
  const [session, setSession] = useState(null);
  const [tab, setTab] = useState(null);
  const [world, setWorld] = useState(emptyWorkspace);
  const [logs, setLogs] = useState([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deletePassword, setDeletePassword] = useState('');
  const [, setTick] = useState(0);
  const lock = useRef(false);
  const logId = useRef(0);
  const version = useRef(0);
  const pollError = useRef('');

  function log(action, success, message) {
    const entry = { id: ++logId.current, time: new Date().toLocaleTimeString(), action, success, message };
    setLogs((current) => [...current.slice(-299), entry]);
  }

  // Every user command is logged; background refreshes stay quiet unless they fail.
  async function send(current, action, fields = {}, { quiet = false } = {}) {
    try {
      if (current.actions && !canExecute(current, action)) throw new Error(`Your role cannot run ${action}.`);
      const result = await api.command(current, action, fields);
      if (!quiet) log(action, true, describe(action, result));
      return result;
    } catch (failure) {
      if (!quiet) log(action, false, failure.message);
      throw failure;
    }
  }

  async function refresh(current) {
    const mine = ++version.current;
    const next = await loadWorkspace(current, (c, action, fields) => send(c, action, fields, { quiet: true }));
    if (mine === version.current) setWorld(next);
  }

  async function run(task) {
    if (lock.current) return undefined;
    lock.current = true;
    setBusy(true);
    setError('');
    try {
      return await task();
    } catch (failure) {
      setError(failure.message);
      return undefined;
    } finally {
      lock.current = false;
      setBusy(false);
    }
  }

  // After a failed command the screen may be stale (another tab or role changed things), so reload it too.
  const act = (action, fields = {}, { refresh: reload = true } = {}) =>
    run(async () => {
      try {
        const result = await send(session, action, fields);
        if (reload) await refresh(session);
        return result;
      } catch (failure) {
        refresh(session).catch(() => {});
        throw failure;
      }
    });
  const ask = (action, fields = {}) => send(session, action, fields, { quiet: true });

  async function connectAccount() {
    await run(async () => {
      setSession(null);
      setWorld(emptyWorkspace);
      let current;
      try {
        current = await api.connect(firebaseUser);
        log('SESSION_INFO', true, `Authenticated as ${current.user.username} (${current.user.role})`);
      } catch (failure) {
        log('SESSION_INFO', false, failure.message);
        throw failure;
      }
      const menu = await send(current, 'ROLE_MENU');
      current = { ...current, actions: workspaceActions(menu) };
      setTab(roleTabs[current.user.role]?.[0] ?? null);
      setSession(current);
      await refresh(current);
    });
  }

  useEffect(() => {
    connectAccount();
  }, [firebaseUser]);

  // Live updates: couriers move and orders change while other roles act.
  useEffect(() => {
    if (!session) return undefined;
    const poll = setInterval(async () => {
      if (lock.current || document.hidden) return;
      try {
        await refresh(session);
        pollError.current = '';
      } catch (failure) {
        if (pollError.current !== failure.message) log('AUTO_REFRESH', false, failure.message);
        pollError.current = failure.message;
      }
    }, POLL_MS);
    const clock = setInterval(() => setTick((t) => t + 1), 1000);
    return () => {
      clearInterval(poll);
      clearInterval(clock);
    };
  }, [session]);

  async function signOut() {
    await run(async () => {
      if (session) {
        try {
          await send(session, 'LOGOUT');
        } catch {}
        setSession(null);
      }
      log('FIREBASE_SIGN_OUT', true, 'Sign-out requested');
      await onLogout();
    });
  }

  const role = session?.user.role;
  const tabs = roleTabs[role] || [];
  const activeTab = tabs.includes(tab) ? tab : tabs[0];
  const disabled = busy || authBusy;
  const shared = { tab: activeTab, session, disabled, money, act, ...world };
  const badge = (name) => {
    if (role === 'CUSTOMER' && name === 'Orders') return world.orders.filter((o) => ['Placed', 'Accepted', 'Preparing', 'Ready', 'PickedUp'].includes(o.state)).length;
    if (role === 'RESTAURANT' && name === 'Today') return world.orders.filter((o) => o.state === 'Placed').length;
    if (role === 'OPERATOR' && name === 'Live') return world.orders.filter((o) => o.state === 'Ready').length;
    if (role === 'COURIER' && name === 'Shift') return world.offer ? 1 : 0;
    return 0;
  };

  return (
    <main className="feed">
      <header className="feed-top">
        <Logo size={40} />
        {session && (
          <nav className="pill-tabs" aria-label="Sections">
            {tabs.map((name) => (
              <button key={name} type="button" className={activeTab === name ? 'on' : ''} aria-current={activeTab === name ? 'page' : undefined} onClick={() => setTab(name)}>
                {name}
                {badge(name) > 0 && <span className="tab-badge">{badge(name)}</span>}
              </button>
            ))}
          </nav>
        )}
        <div className="account">
          <span className={`live-dot${session ? ' on' : ''}`} title={session ? 'Live updates every 2 seconds' : 'Not connected'} />
          <button type="button" className="account-button" aria-expanded={menuOpen} onClick={() => setMenuOpen(!menuOpen)}>
            <span className="avatar">{(session?.user.username || '?').slice(0, 2).toUpperCase()}</span>
            <span className="account-name">
              {session ? session.user.username : 'Connecting…'}
              {role && <small>{role.toLowerCase()}</small>}
            </span>
          </button>
          {menuOpen && (
            <div className="account-menu" role="menu">
              <button type="button" role="menuitem" disabled={!session} onClick={() => { setProfileOpen(true); setMenuOpen(false); }}>
                Manage my profile
              </button>
              <button type="button" role="menuitem" disabled={disabled} onClick={signOut}>
                Sign out
              </button>
              {!confirmDelete ? (
                <button type="button" role="menuitem" className="danger-text" disabled={disabled} onClick={() => setConfirmDelete(true)}>
                  Delete account
                </button>
              ) : (
                <div className="delete-confirm">
                  <p>Delete your account permanently?</p>
                  <input type="password" aria-label="Confirm password" placeholder="Password" autoComplete="current-password" value={deletePassword} onChange={(e) => setDeletePassword(e.target.value)} />
                  <div className="row">
                    <button className="f-btn danger sm" disabled={disabled || !deletePassword} onClick={() => onDeleteAccount(deletePassword)}>
                      Delete
                    </button>
                    <button className="f-btn ghost sm" disabled={disabled} onClick={() => { setConfirmDelete(false); setDeletePassword(''); }}>
                      Cancel
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </header>

      {profileOpen && <ProfilePanel session={session} send={send} disabled={disabled} onClose={() => setProfileOpen(false)} />}
      {(error || authError) && (
        <div className="error-banner" role="alert">
          <span>{error || authError}</span>
          <button type="button" className="link-button" onClick={() => setError('')}>
            Dismiss
          </button>
        </div>
      )}

      <div className="feed-body">
        {!session && (
          <section className="panel narrow">
            <h2>{busy ? 'Connecting to SugarRush…' : 'The order server is not connected'}</h2>
            {!busy && (
              <button className="f-btn primary" onClick={connectAccount}>
                Retry connection
              </button>
            )}
          </section>
        )}
        {role === 'CUSTOMER' && <CustomerView {...shared} setTab={setTab} ask={ask} />}
        {role === 'RESTAURANT' && <RestaurantView {...shared} />}
        {role === 'OPERATOR' && <OperatorView {...shared} />}
        {role === 'COURIER' && <CourierView {...shared} />}
      </div>

      <ConsoleDrawer logs={logs} onClear={() => setLogs([])} />
    </main>
  );
}
