import { useEffect, useRef, useState } from 'react';
import CustomerView from '../components/CustomerView';
import Logo from '../components/Logo';
import ProfilePanel from '../components/ProfilePanel';
import SimulatorMap, { queueBand } from '../components/SimulatorMap';
import * as api from '../services/dashboardApi';
import { canExecute, loadWorkspace, workspaceActions } from '../services/dashboardWorkspace';
import '../styles/dashboard.css';

const roleTabs = {
  RESTAURANT: ['Restaurant', 'Orders'],
  OPERATOR: ['Orders', 'Map', 'History'],
  COURIER: ['Workspace', 'Map'],
};

const money = (value) =>
  new Intl.NumberFormat('en-CA', {
    style: 'currency',
    currency: 'CAD',
  }).format(value);

export default function DashboardPage({ firebaseUser, onLogout, onDeleteAccount, busy: authBusy, error: authError }) {
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deletePassword, setDeletePassword] = useState('');
  const [tab, setTab] = useState(null);
  const [search, setSearch] = useState('');
  const [showLabels, setShowLabels] = useState(true);
  const [session, setSession] = useState(null);
  const [restaurants, setRestaurants] = useState([]);
  const [orders, setOrders] = useState([]);
  const [queues, setQueues] = useState({});
  const [restaurantId, setRestaurantId] = useState('');
  const [newRestaurantName, setNewRestaurantName] = useState('');
  const [itemName, setItemName] = useState('');
  const [itemPrice, setItemPrice] = useState('');
  const [editingItemId, setEditingItemId] = useState('');
  const [reasons, setReasons] = useState({});
  const [logs, setLogs] = useState([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const lock = useRef(false);
  const logId = useRef(0);

  function log(action, success, message) {
    const entry = {
      id: ++logId.current,
      time: new Date().toLocaleTimeString(),
      action,
      success,
      message,
    };
    setLogs((current) => [...current, entry]);
  }

  async function send(current, action, fields = {}) {
    try {
      if (current.actions && !canExecute(current, action)) {
        throw new Error(`The current workspace does not support ${action}.`);
      }
      const result = await api.command(current, action, fields);
      log(action, true, result?.state ? `Order ${result.id.slice(0, 8)} → ${result.state}` : 'Completed');
      return result;
    } catch (failure) {
      log(action, false, failure.message);
      throw failure;
    }
  }

  async function refresh(current) {
    const { restaurants: visible, orders: visibleOrders, queues: nextQueues } = await loadWorkspace(current, send);
    setRestaurants(visible);
    setQueues(nextQueues);
    setRestaurantId((previous) =>
      visible.some((r) => r.id === previous)
        ? previous
        : current.user.role === 'RESTAURANT'
          ? visible[0]?.id || ''
          : '',
    );
    setOrders(visibleOrders);
  }

  async function run(task) {
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    setError('');
    try {
      await task();
    } catch (failure) {
      setError(failure.message);
    } finally {
      lock.current = false;
      setBusy(false);
    }
  }

  async function connectAccount() {
    await run(async () => {
      setSession(null);
      setTab(null);
      setSearch('');
      setRestaurants([]);
      setOrders([]);
      setQueues({});
      setRestaurantId('');
      setReasons({});
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

  function placeOrder(id, items, onPlaced) {
    run(async () => {
      const fields = { restaurantId: id };
      Object.entries(items).forEach(([itemId, quantity]) => {
        fields[`item.${itemId}`] = quantity;
      });
      await send(session, 'PLACE_ORDER', fields);
      onPlaced();
      await refresh(session);
    });
  }

  function transition(order, action) {
    run(async () => {
      await send(session, action, {
        orderId: order.id,
        reason: reasons[order.id] || '',
      });
      await refresh(session);
    });
  }

  function setRestaurantStatus(status) {
    run(async () => {
      await send(session, 'SET_RESTAURANT_STATUS', { restaurantId, status });
      await refresh(session);
    });
  }

  function createRestaurant(event) {
    event.preventDefault();
    run(async () => {
      const created = await send(session, 'CREATE_RESTAURANT', { name: newRestaurantName.trim() });
      setNewRestaurantName('');
      await refresh(session);
      setRestaurantId(created.id);
      setTab('Restaurant');
    });
  }

  function saveMenuItem(event) {
    event.preventDefault();
    run(async () => {
      await send(session, 'PUT_MENU_ITEM', {
        restaurantId,
        itemId: editingItemId || crypto.randomUUID(),
        name: itemName.trim(),
        price: Number(itemPrice).toFixed(2),
        itemStatus: editingItemId ? restaurant.menu.find((item) => item.id === editingItemId)?.status : 'AVAILABLE',
      });
      setEditingItemId('');
      setItemName('');
      setItemPrice('');
      await refresh(session);
    });
  }

  function changeItemStatus(item) {
    run(async () => {
      await send(session, 'PUT_MENU_ITEM', { restaurantId, itemId: item.id, name: item.name,
        price: Number(item.price).toFixed(2), itemStatus: item.status === 'AVAILABLE' ? 'SOLD_OUT' : 'AVAILABLE' });
      await refresh(session);
    });
  }

  function deleteMenuItem(item) {
    if (!window.confirm(`Delete ${item.name} from the menu?`)) return;
    run(async () => {
      await send(session, 'DELETE_MENU_ITEM', { restaurantId, itemId: item.id });
      await refresh(session);
    });
  }

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

  const restaurant = restaurants.find((r) => r.id === restaurantId);
  const role = session?.user.role;
  const displayedOrders =
    role === 'RESTAURANT' ? orders.filter((order) => order.restaurantId === restaurantId) : orders;
  const disabled = busy || authBusy;

  const normalise = (value) =>
    value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase();
  const filteredRestaurants = restaurants.filter((r) => normalise(r.name).includes(normalise(search.trim())));
  const hasSearch = search.trim().length > 0;
  const featuredRestaurant = restaurants.find((r) => r.id === 'sugar-shop');
  const restaurantOrders = orders.filter((o) => o.restaurantId === restaurantId);
  const tabs = roleTabs[role] || [];
  const activeTab = tabs.includes(tab) ? tab : tabs[0];
  const showSidebar = role === 'RESTAURANT';
  const tabLabel = (name) =>
    name === 'Orders' ? (role === 'OPERATOR' ? 'All orders' : 'Restaurant orders') : name;

  function selectRestaurant(id) {
    setRestaurantId((previous) => (previous === id ? '' : id));
    setTab(role === 'RESTAURANT' ? 'Restaurant' : 'Map');
  }

  function renderMenu() {
    if (!restaurant) return null;
    return (
      <section className="dash-card restaurant-details">
        <div className="dash-section-title">
          <h2>{restaurant?.name || 'Restaurant details'}</h2>
          {restaurant && (
            <span className={`status-tag status-${restaurant.status.toLowerCase()}`}>{restaurant.status}</span>
          )}
        </div>
        {!restaurant ? (
          <p className="dash-muted">Select a restaurant to browse its menu.</p>
        ) : (
          <>
            <div className="restaurant-metrics">
              <div>
                <strong>{restaurant.queueLength ?? '—'}</strong>
                <span>Queued</span>
              </div>
              <div>
                <strong>{restaurantOrders.filter((o) => o.state === 'Preparing').length}</strong>
                <span>Preparing*</span>
              </div>
              <div>
                <strong>{restaurantOrders.filter((o) => o.state === 'Ready').length}</strong>
                <span>Ready*</span>
              </div>
            </div>
            <p className="dash-muted">*Counts cover the orders visible to your account.</p>
            {canExecute(session, 'SET_RESTAURANT_STATUS') && (
              <div className="order-actions">
                {restaurant.status === 'OPEN' ? (
                  <button
                    className="btn btn-secondary"
                    disabled={disabled}
                    onClick={() => setRestaurantStatus('PAUSED')}
                  >
                    Pause restaurant
                  </button>
                ) : (
                  <button className="btn btn-primary" disabled={disabled} onClick={() => setRestaurantStatus('OPEN')}>
                    Reopen restaurant
                  </button>
                )}
                <p className="dash-muted">Pausing stops new orders. Orders already accepted can still be prepared.</p>
              </div>
            )}
            <div>
              <h3>Menu</h3>
              {!restaurant.menu.length && <p>The menu is empty.</p>}
              <ul className="menu-list">
                {restaurant.menu.map((item) => (
                  <li key={item.id}>
                    <div>
                      <strong>{item.name}</strong>
                      <p>
                        {money(item.price)} · {item.status === 'SOLD_OUT' ? 'Sold out' : 'Available'}
                      </p>
                    </div>
                    {role === 'RESTAURANT' && <div className="menu-owner-actions">
                      <button className="btn btn-secondary" type="button" disabled={disabled} onClick={() => { setEditingItemId(item.id); setItemName(item.name); setItemPrice(String(item.price)); }}>Edit</button>
                      <button className="btn btn-secondary" type="button" disabled={disabled} onClick={() => changeItemStatus(item)}>{item.status === 'AVAILABLE' ? 'Mark sold out' : 'Mark available'}</button>
                      <button className="btn btn-danger-outline" type="button" disabled={disabled} onClick={() => deleteMenuItem(item)}>Delete</button>
                    </div>}
                  </li>
                ))}
              </ul>
            </div>
            {role === 'RESTAURANT' && <form className="menu-editor" onSubmit={saveMenuItem}>
              <h3>{editingItemId ? 'Edit menu item' : 'Add menu item'}</h3>
              <label>Item name<input required maxLength="80" value={itemName} onChange={(e) => setItemName(e.target.value)} /></label>
              <label>Price (CAD)<input required type="number" min="0" step="0.01" value={itemPrice} onChange={(e) => setItemPrice(e.target.value)} /></label>
              <div className="menu-owner-actions"><button className="btn btn-primary" disabled={disabled}>{editingItemId ? 'Save item' : 'Add item'}</button>
                {editingItemId && <button className="btn btn-secondary" type="button" onClick={() => { setEditingItemId(''); setItemName(''); setItemPrice(''); }}>Cancel</button>}
              </div>
            </form>}
            {role === 'RESTAURANT' && (
              <button className="btn btn-primary" disabled={disabled} onClick={() => setTab('Orders')}>
                Manage restaurant orders
              </button>
            )}
          </>
        )}
      </section>
    );
  }

  function renderOrders() {
    return (
      <section className="dash-card">
        <div className="dash-section-title">
          <h2>{role === 'RESTAURANT' ? 'Restaurant orders' : role === 'OPERATOR' ? 'All orders' : 'Your orders'}</h2>
          <span className="dash-badge">{displayedOrders.length} orders</span>
        </div>
        {role === 'RESTAURANT' && (
          <p className="dash-muted">
            Prepare accepted orders in queue order. Finish the first before starting the next.
          </p>
        )}
        {!displayedOrders.length && (
          <p className="dash-empty">{!session ? 'Your orders load automatically after sign-in.' : 'No orders yet.'}</p>
        )}
        <div className="order-list">
          {displayedOrders.map((order) => (
            <article className="order-card" key={order.id}>
              <div className="dash-section-title">
                <h3>Order {order.id.slice(0, 8)}</h3>
                <span className="dash-badge">{order.state}</span>
              </div>
              <p>{restaurants.find((r) => r.id === order.restaurantId)?.name || order.restaurantId}</p>
              <ul>
                {order.lines.map((line) => (
                  <li key={line.item.id}>
                    {line.quantity} × {line.item.name}
                  </li>
                ))}
              </ul>
              <strong>{money(order.total)}</strong>
              {order.rejectionReason && <p>Reason: {order.rejectionReason}</p>}
              {role === 'OPERATOR' && ['Accepted', 'Preparing'].includes(order.state) && (
                <div className="order-actions">
                  <button
                    className="btn btn-secondary"
                    disabled={disabled}
                    onClick={() => transition(order, 'CANCEL_ORDER')}
                  >
                    Cancel order
                  </button>
                </div>
              )}
              {role === 'RESTAURANT' && (
                <div className="order-actions">
                  {order.state === 'Placed' && (
                    <>
                      <button
                        className="btn btn-primary"
                        disabled={disabled}
                        onClick={() => transition(order, 'ACCEPT_ORDER')}
                      >
                        Accept
                      </button>
                      <label>
                        Rejection reason
                        <input
                          value={reasons[order.id] || ''}
                          onChange={(e) =>
                            setReasons((current) => ({
                              ...current,
                              [order.id]: e.target.value,
                            }))
                          }
                        />
                      </label>
                      <button
                        className="btn btn-secondary"
                        disabled={disabled || !(reasons[order.id] || '').trim()}
                        onClick={() => transition(order, 'REJECT_ORDER')}
                      >
                        Reject
                      </button>
                    </>
                  )}
                  {order.state === 'Accepted' && (
                    <button
                      className="btn btn-primary"
                      disabled={disabled || queues[order.restaurantId]?.[0] !== order.id}
                      onClick={() => transition(order, 'START_PREPARING')}
                    >
                      Start preparing
                    </button>
                  )}
                  {order.state === 'Preparing' && (
                    <button
                      className="btn btn-primary"
                      disabled={disabled}
                      onClick={() => transition(order, 'MARK_READY')}
                    >
                      Mark ready
                    </button>
                  )}
                </div>
              )}
            </article>
          ))}
        </div>
      </section>
    );
  }

  return (
    <main className="dashboard">
      <header className="dash-header">
        <div>
          <Logo size={44} />
        </div>
        <div className="dash-account">
          <strong>{session ? `${session.user.username} · ${role}` : 'Loading your account...'}</strong>
          <button className="profile-button" type="button" aria-label="Account settings" aria-expanded={settingsOpen} onClick={() => setSettingsOpen(!settingsOpen)}>👤</button>
        </div>
      </header>
      {settingsOpen && <section className="dash-card account-settings" aria-label="Account settings">
        <div className="dash-section-title"><h2>Account settings</h2><button className="btn-link" type="button" onClick={() => { setSettingsOpen(false); setConfirmDelete(false); }}>Close</button></div>
        <button className="btn btn-primary" disabled={!session} onClick={() => setProfileOpen(true)}>Manage my profile</button>
        <button className="btn btn-secondary" disabled={disabled} onClick={signOut}>Sign out</button>
        {!confirmDelete ? <button className="btn btn-danger-outline" disabled={disabled} onClick={() => setConfirmDelete(true)}>Delete account</button> : <div className="delete-confirmation">
          <p>Delete your account permanently? You will lose access to this profile.</p>
          <label>Confirm password<input type="password" autoComplete="current-password" value={deletePassword} onChange={(e) => setDeletePassword(e.target.value)} /></label>
          <button className="btn btn-danger" disabled={disabled || !deletePassword} onClick={() => onDeleteAccount(deletePassword)}>Yes, delete account</button>
          <button className="btn btn-secondary" disabled={disabled} onClick={() => { setConfirmDelete(false); setDeletePassword(''); }}>Cancel</button>
        </div>}
      </section>}
      {profileOpen && <ProfilePanel session={session} send={send} disabled={disabled} onClose={() => setProfileOpen(false)} />}
      {(error || authError) && (
        <p className="form-alert" role="alert">
          {error || authError}
        </p>
      )}

      {role === 'CUSTOMER' && (
        <CustomerView
          restaurants={restaurants}
          orders={orders}
          disabled={disabled}
          money={money}
          onPlace={placeOrder}
          onCancel={(order) => transition(order, 'CANCEL_ORDER')}
          onRefresh={() => run(() => refresh(session))}
        />
      )}
      {session && role !== 'CUSTOMER' && (
        <nav className="module-tabs" role="tablist" aria-label="Workspace sections">
          {tabs.map((name, index) => (
            <button
              key={name}
              id={`module-tab-${index}`}
              type="button"
              role="tab"
              aria-selected={activeTab === name}
              aria-controls="workspace-panel"
              tabIndex={activeTab === name ? 0 : -1}
              className={activeTab === name ? 'active-tab' : ''}
              onClick={() => setTab(name)}
              onKeyDown={(e) => {
                let next;
                if (e.key === 'ArrowRight') next = (index + 1) % tabs.length;
                if (e.key === 'ArrowLeft') next = (index + tabs.length - 1) % tabs.length;
                if (e.key === 'Home') next = 0;
                if (e.key === 'End') next = tabs.length - 1;
                if (next !== undefined) {
                  e.preventDefault();
                  setTab(tabs[next]);
                  e.currentTarget.parentElement.children[next].focus();
                }
              }}
            >
              {tabLabel(name)}
            </button>
          ))}
        </nav>
      )}
      {role !== 'CUSTOMER' && (
        <>
          <div className="dash-toolbar">
            <p>
              <span className={`connection-dot ${session ? 'connected' : ''}`} />
              {busy
                ? 'Loading your workspace...'
                : session
                  ? 'Your workspace is connected'
                  : 'The order server is not connected.'}
            </p>
            <button
              className="btn btn-secondary"
              disabled={disabled}
              onClick={() => (session ? run(() => refresh(session)) : connectAccount())}
            >
              {busy ? 'Loading...' : session ? 'Refresh workspace' : 'Retry connection'}
            </button>
          </div>
          <div className={`simulator-workspace${showSidebar ? '' : ' workspace-full-width'}`}>
            {showSidebar && (
              <aside className="dash-card restaurant-sidebar">
                <h2>{role === 'RESTAURANT' ? 'Your restaurants' : 'Find a restaurant'}</h2>
                {role === 'RESTAURANT' && <form className="create-restaurant" onSubmit={createRestaurant}>
                  <label>Restaurant name<input required maxLength="80" value={newRestaurantName} onChange={(e) => setNewRestaurantName(e.target.value)} placeholder="Your restaurant name" /></label>
                  <button className="btn btn-primary" disabled={disabled}>Create restaurant</button>
                </form>}

                {!session ? (
                  <p className="dash-muted">Loading restaurants...</p>
                ) : hasSearch || role === 'RESTAURANT' ? (
                  <>
                    <p className="dash-muted">
                      {filteredRestaurants.length} restaurant
                      {filteredRestaurants.length === 1 ? '' : 's'} found
                    </p>

                    <div className="restaurant-list">
                      {filteredRestaurants.map((r) => (
                        <button
                          type="button"
                          key={r.id}
                          disabled={disabled}
                          className={`restaurant-option ${r.id === restaurantId ? 'selected-restaurant' : ''}`}
                          aria-pressed={r.id === restaurantId}
                          onClick={() => selectRestaurant(r.id)}
                        >
                          <span className={`queue-dot queue-${queueBand(r)}`} />
                          <span>
                            <strong>{r.name}</strong>
                            <small>{r.status.toLowerCase()}</small>
                          </span>
                        </button>
                      ))}
                    </div>

                    {!filteredRestaurants.length && <p className="dash-empty">{role === 'RESTAURANT' ? 'Create your first restaurant above.' : 'No restaurants match your search.'}</p>}
                  </>
                ) : (
                  <>
                    <h3>Restaurant of the week</h3>

                    {featuredRestaurant ? (
                      <button
                        type="button"
                        disabled={disabled}
                        className="restaurant-option"
                        onClick={() => selectRestaurant(featuredRestaurant.id)}
                      >
                        <span>
                          <strong>{featuredRestaurant.name}</strong>
                          <small>This week’s featured restaurant</small>
                        </span>
                      </button>
                    ) : (
                      <p className="dash-muted">No featured restaurant this week.</p>
                    )}
                  </>
                )}
              </aside>
            )}
            <section
              className="workspace-panel"
              id="workspace-panel"
              role={session ? 'tabpanel' : undefined}
              aria-labelledby={session ? `module-tab-${tabs.indexOf(activeTab)}` : undefined}
            >
              {activeTab === 'Map' && (
                <>
                  <div className="city-metrics">
                    <div>
                      <span>Restaurants</span>
                      <strong>{session ? restaurants.length : '—'}</strong>
                    </div>
                    <div>
                      <span>Open now</span>
                      <strong>{session ? restaurants.filter((r) => r.status === 'OPEN').length : '—'}</strong>
                    </div>
                    <div>
                      <span>Visible orders</span>
                      <strong>{session ? orders.length : '—'}</strong>
                    </div>
                    <div>
                      <span>Queued orders</span>
                      <strong>{session ? restaurants.reduce((sum, r) => sum + (r.queueLength || 0), 0) : '—'}</strong>
                    </div>
                  </div>
                  <section className="dash-card map-card">
                    <div className="dash-section-title">
                      <div>
                        <h2>City map</h2>
                        <p className="dash-muted">Seeded coordinates · select a restaurant marker</p>
                      </div>
                      <label className="checkbox-label">
                        <input type="checkbox" checked={showLabels} onChange={(e) => setShowLabels(e.target.checked)} />
                        Show labels
                      </label>
                    </div>
                    <SimulatorMap
                      restaurants={restaurants}
                      visibleRestaurants={filteredRestaurants}
                      selectedId={restaurantId}
                      onSelect={selectRestaurant}
                      showLabels={showLabels}
                      disabled={disabled}
                    />
                    <div className="map-legend">
                      <span>
                        <i className="queue-dot queue-short" />
                        Short: 0–2 queued
                      </span>
                      <span>
                        <i className="queue-dot queue-building" />
                        Building: 3–5
                      </span>
                      <span>
                        <i className="queue-dot queue-long" />
                        Long: 6+ or paused
                      </span>
                    </div>
                    <p className="dash-muted">
                      Schematic grid, not a live street map. Courier positions are not included in your current seed.
                    </p>
                  </section>

                  {renderMenu()}
                </>
              )}
              {activeTab === 'Restaurant' && renderMenu()}
              {activeTab === 'Orders' && renderOrders()}
              {activeTab === 'Workspace' && (
                <section className="dash-card">
                  <h2>Courier orders</h2>
                  <p>Courier assignments are not implemented yet.</p>
                </section>
              )}
              {activeTab === 'History' && (
                <section className="dash-card">
                  <div>
                    <h2>Order history</h2>
                    <p className="dash-muted">
                      Orders visible to your account. Amounts are item subtotals; billing fees and charges are not
                      implemented yet.
                    </p>
                  </div>
                  {!orders.length ? (
                    <p className="dash-empty">
                      {session ? 'No orders to display.' : 'Your history loads automatically after sign-in.'}
                    </p>
                  ) : (
                    <div className="history-table-wrap">
                      <table className="history-table">
                        <thead>
                          <tr>
                            <th>Order</th>
                            <th>Restaurant</th>
                            <th>State</th>
                            <th>Item subtotal</th>
                          </tr>
                        </thead>
                        <tbody>
                          {orders.map((o) => (
                            <tr key={o.id}>
                              <td>{o.id.slice(0, 8)}</td>
                              <td>{restaurants.find((r) => r.id === o.restaurantId)?.name || o.restaurantId}</td>
                              <td>{o.state}</td>
                              <td>{money(o.total)}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </section>
              )}
            </section>
          </div>
        </>
      )}
      <section className="dash-card console-card">
        <div className="dash-section-title">
          <div>
            <h2>Event & command console</h2>
            <p className="dash-muted">Command results, order state changes, and errors from this browser session.</p>
          </div>
          <span className="dash-badge">{logs.length} entries</span>
        </div>
        <div
          className="dash-console"
          role="log"
          aria-live="polite"
          aria-relevant="additions"
          aria-label="Command output"
        >
          {!logs.length && <p>Waiting for simulator commands...</p>}
          {logs.map((entry) => (
            <p key={entry.id} className={entry.success ? 'console-success' : 'console-error'}>
              [{entry.time}] {entry.success ? 'OK' : 'ERROR'} {entry.action}: {entry.message}
            </p>
          ))}
        </div>
      </section>
    </main>
  );
}
