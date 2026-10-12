import { useRef, useState } from 'react';
import SimulatorMap, { queueBand } from '../components/SimulatorMap';
import * as api from '../services/dashboardApi';
import '../styles/dashboard.css';

const money = value => new Intl.NumberFormat('en-CA', {
  style: 'currency', currency: 'CAD',
}).format(value);

export default function DashboardPage({ email, onLogout, busy: authBusy, error: authError }) {
  const [tab, setTab] = useState('Map');
  const [search, setSearch] = useState('');
  const [showLabels, setShowLabels] = useState(true);
  const [showConnection, setShowConnection] = useState(false);
  const [session, setSession] = useState(null);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [restaurants, setRestaurants] = useState([]);
  const [orders, setOrders] = useState([]);
  const [queues, setQueues] = useState({});
  const [restaurantId, setRestaurantId] = useState('');
  const [quantities, setQuantities] = useState({});
  const [reasons, setReasons] = useState({});
  const [logs, setLogs] = useState([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const lock = useRef(false);
  const logId = useRef(0);

  function log(action, success, message) {
    const entry = { id: ++logId.current, time: new Date().toLocaleTimeString(), action, success, message };
    setLogs(current => [...current, entry]);
  }

  async function send(current, action, fields = {}) {
    try {
      const result = await api.command(current.token, action, fields);
      log(action, true, result?.state ? `Order ${result.id.slice(0, 8)} → ${result.state}` : 'Completed');
      return result;
    } catch (failure) {
      log(action, false, failure.message);
      throw failure;
    }
  }

  async function refresh(current) {
    const all = await send(current, 'BROWSE_RESTAURANTS');
    // Staff see their own restaurants. Java still verifies ownership on writes.
    const visible = current.user.role === 'RESTAURANT'
      ? all.filter(r => r.ownerId === current.user.id) : all;
    setRestaurants(visible);
    if (current.user.role === 'RESTAURANT') {
      const nextQueues = {};
      for (const r of visible) nextQueues[r.id] = await send(current, 'RESTAURANT_QUEUE', { restaurantId: r.id });
      setQueues(nextQueues);
    }
    setRestaurantId(previous => visible.some(r => r.id === previous) ? previous : visible[0]?.id || '');
    setOrders(current.user.role === 'COURIER' ? [] : await send(current, 'LIST_ORDERS'));
  }

  async function run(task) {
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    setError('');
    try { await task(); }
    catch (failure) { setError(failure.message); }
    finally { lock.current = false; setBusy(false); }
  }

  function signIn(event) {
    event.preventDefault();
    run(async () => {
      let current;
      try {
        current = await api.login(username.trim(), password);
        log('LOGIN', true, `Signed in as ${current.user.username}`);
      } catch (failure) {
        log('LOGIN', false, failure.message);
        throw failure;
      } finally { setPassword(''); }
      setSession(current);
      await refresh(current);
      setShowConnection(false);
    });
  }

  function place(event) {
    event.preventDefault();
    run(async () => {
      const selected = restaurant.menu.filter(item => Number(quantities[item.id]) > 0);
      if (!selected.length) {
        log('PLACE_ORDER', false, 'Choose at least one item.');
        throw new Error('Choose at least one item.');
      }
      const fields = { restaurantId };
      selected.forEach(item => { fields[`item.${item.id}`] = quantities[item.id]; });
      await send(session, 'PLACE_ORDER', fields);
      setQuantities({});
      await refresh(session);
      setTab('Orders');
    });
  }

  function transition(order, action) {
    run(async () => {
      await send(session, action, { orderId: order.id, reason: reasons[order.id] || '' });
      await refresh(session);
    });
  }

  function switchSession() {
    run(async () => {
      await send(session, 'LOGOUT');
      setSession(null);
      setShowConnection(true);
      setSearch('');
      setRestaurants([]);
      setOrders([]);
      setQuantities({});
      setReasons({});
    });
  }

  async function signOut() {
    await run(async () => {
      if (session) {
        try { await send(session, 'LOGOUT'); } catch {}
        setSession(null);
      }
      log('FIREBASE_SIGN_OUT', true, 'Sign-out requested');
      await onLogout();
    });
  }

  const restaurant = restaurants.find(r => r.id === restaurantId);
  const role = session?.user.role;
  const displayedOrders = role === 'RESTAURANT'
    ? orders.filter(order => order.restaurantId === restaurantId) : orders;
  const total = restaurant?.menu.reduce((sum, item) =>
    sum + Number(item.price) * (Number(quantities[item.id]) || 0), 0) || 0;
  const selected = restaurant?.menu.filter(item => Number(quantities[item.id]) > 0) || [];
  const canPlace = restaurant?.status === 'OPEN' && selected.length > 0
    && selected.every(item => item.status === 'AVAILABLE'
      && Number.isInteger(Number(quantities[item.id])));
  const disabled = busy || authBusy;

  const normalise = value => value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
  const filteredRestaurants = restaurants.filter(r => normalise(r.name).includes(normalise(search.trim())));
  const restaurantOrders = orders.filter(o => o.restaurantId === restaurantId);
  const tabs = ['Map', 'Orders', 'Delivery', 'Pricing & History'];

  function selectRestaurant(id) {
    setRestaurantId(id);
    setQuantities({});
  }

  function renderMenu() {
    return <section className="dash-card restaurant-details">
      <div className="dash-section-title"><h2>{restaurant?.name || 'Restaurant details'}</h2>
        {restaurant && <span className={`status-tag status-${restaurant.status.toLowerCase()}`}>{restaurant.status}</span>}
      </div>
      {!restaurant ? <p className="dash-muted">Select a restaurant to browse its menu.</p> : <>
        <div className="restaurant-metrics">
          <div><strong>{restaurant.queueLength ?? '—'}</strong><span>Queued</span></div>
          <div><strong>{restaurantOrders.filter(o => o.state === 'Preparing').length}</strong><span>Preparing*</span></div>
          <div><strong>{restaurantOrders.filter(o => o.state === 'Ready').length}</strong><span>Ready*</span></div>
        </div>
        <p className="dash-muted">*Counts cover the orders visible to your Java account.</p>
        <form onSubmit={place}>
          <h3>Menu</h3>
          {!restaurant.menu.length && <p>The menu is empty.</p>}
          <ul className="menu-list">{restaurant.menu.map(item => <li key={item.id}>
            <div><strong>{item.name}</strong><p>{money(item.price)} · {item.status === 'SOLD_OUT' ? 'Sold out' : 'Available'}</p></div>
            {role === 'CUSTOMER' && <label className="quantity">Qty<input type="number" min="0" max="99" step="1"
              value={quantities[item.id] ?? 0} disabled={disabled || item.status !== 'AVAILABLE' || restaurant.status !== 'OPEN'}
              onChange={e => setQuantities(current => ({ ...current, [item.id]: e.target.value }))} /></label>}
          </li>)}</ul>
          {role === 'CUSTOMER' && <div className="checkout"><strong>Item subtotal: {money(total)}</strong>
            <button className="btn btn-primary" type="submit" disabled={disabled || !canPlace}>Place order</button>
            {restaurant.status !== 'OPEN' && <p>This restaurant is not accepting new orders.</p>}
          </div>}
        </form>
        {role === 'RESTAURANT' && <button className="btn btn-primary" disabled={disabled} onClick={() => setTab('Orders')}>Manage restaurant orders</button>}
      </>}
    </section>;
  }

  function renderOrders() {
    return <section className="dash-card">
      <div className="dash-section-title"><h2>{role === 'RESTAURANT' ? 'Restaurant orders' : role === 'OPERATOR' ? 'All orders' : 'Your orders'}</h2>
        <span className="dash-badge">{displayedOrders.length} orders</span>
      </div>
      {role === 'RESTAURANT' && <p className="dash-muted">Prepare accepted orders in queue order. Finish the first before starting the next.</p>}
      {!displayedOrders.length && <p className="dash-empty">{!session ? 'Connect your Java account to load orders.' : 'No orders yet.'}</p>}
      <div className="order-list">{displayedOrders.map(order => <article className="order-card" key={order.id}>
        <div className="dash-section-title"><h3>Order {order.id.slice(0, 8)}</h3><span className="dash-badge">{order.state}</span></div>
        <p>{restaurants.find(r => r.id === order.restaurantId)?.name || order.restaurantId}</p>
        <ul>{order.lines.map(line => <li key={line.item.id}>{line.quantity} × {line.item.name}</li>)}</ul>
        <strong>{money(order.total)}</strong>
        {order.rejectionReason && <p>Reason: {order.rejectionReason}</p>}
        {role === 'RESTAURANT' && <div className="order-actions">
          {order.state === 'Placed' && <>
            <button className="btn btn-primary" disabled={disabled} onClick={() => transition(order, 'ACCEPT_ORDER')}>Accept</button>
            <label>Rejection reason<input value={reasons[order.id] || ''} onChange={e => setReasons(current => ({ ...current, [order.id]: e.target.value }))} /></label>
            <button className="btn btn-secondary" disabled={disabled || !(reasons[order.id] || '').trim()} onClick={() => transition(order, 'REJECT_ORDER')}>Reject</button>
          </>}
          {order.state === 'Accepted' && <button className="btn btn-primary" disabled={disabled || queues[order.restaurantId]?.[0] !== order.id} onClick={() => transition(order, 'START_PREPARING')}>Start preparing</button>}
          {order.state === 'Preparing' && <button className="btn btn-primary" disabled={disabled} onClick={() => transition(order, 'MARK_READY')}>Mark ready</button>}
        </div>}
      </article>)}</div>
    </section>;
  }

  return <main className="dashboard">
    <header className="dash-header">
      <div><p className="dash-brand">SugarRush <span>SIMULATOR</span></p><h1>City dashboard</h1><p className="dash-muted">Restaurants, orders, and activity in one workspace.</p></div>
      <div className="dash-account"><span>{email}</span>
        <strong>{session ? `${session.user.username} · ${role}` : 'Java account not connected'}</strong>
        <button className="btn btn-secondary" disabled={disabled} onClick={signOut}>Sign out</button>
      </div>
    </header>
    {(error || authError) && <p className="form-alert" role="alert">{error || authError}</p>}
    <div className="dash-toolbar">
      <p><span className={`connection-dot ${session ? 'connected' : ''}`} />{session ? 'Java order server connected' : 'Connect a Java demo account to load the city and use order controls.'}</p>
      {session ? <>
        <button className="btn btn-secondary" disabled={disabled} onClick={() => run(() => refresh(session))}>{busy ? 'Updating...' : 'Refresh city'}</button>
        <button className="btn btn-secondary" disabled={disabled} onClick={switchSession}>Switch Java account</button>
      </> : <button className="btn btn-primary" disabled={disabled} onClick={() => setShowConnection(current => !current)}>{showConnection ? 'Hide connection panel' : 'Connect demo account'}</button>}
    </div>
    {!session && showConnection && <section className="dash-card demo-login">
      <h2>Connect to the Java demo</h2><p>Your existing Firebase login opens this dashboard. Java currently uses separate demo accounts for order permissions.</p>
      <form className="connection-form" onSubmit={signIn}>
        <label>Java username<input required autoComplete="username" value={username} onChange={e => setUsername(e.target.value)} /></label>
        <label>Java password<input required type="password" autoComplete="current-password" value={password} onChange={e => setPassword(e.target.value)} /></label>
        <button className="btn btn-primary" disabled={disabled}>{busy ? 'Connecting...' : 'Open workspace'}</button>
      </form><p className="dash-muted">customer / customer-demo · restaurant / restaurant-demo · operator / operator-demo · courier / courier-demo</p>
    </section>}
    <nav className="module-tabs" role="tablist" aria-label="Simulator modules">
      {tabs.map((name, index) => <button key={name} id={`module-tab-${index}`} type="button" role="tab"
        aria-selected={tab === name} aria-controls="workspace-panel" tabIndex={tab === name ? 0 : -1}
        className={tab === name ? 'active-tab' : ''} onClick={() => setTab(name)}
        onKeyDown={e => {
          let next;
          if (e.key === 'ArrowRight') next = (index + 1) % tabs.length;
          if (e.key === 'ArrowLeft') next = (index + tabs.length - 1) % tabs.length;
          if (e.key === 'Home') next = 0;
          if (e.key === 'End') next = tabs.length - 1;
          if (next !== undefined) { e.preventDefault(); setTab(tabs[next]); e.currentTarget.parentElement.children[next].focus(); }
        }}>{name}</button>)}
    </nav>
    <div className="simulator-workspace">
      <aside className="dash-card restaurant-sidebar">
        <h2>Find a restaurant</h2>
        <label>Search restaurants<input type="search" placeholder="Search by restaurant name..." value={search} onChange={e => setSearch(e.target.value)} disabled={!session} /></label>
        <p className="dash-muted">{session ? `${filteredRestaurants.length} restaurant${filteredRestaurants.length === 1 ? '' : 's'} found` : 'Connect to load restaurants.'}</p>
        <div className="restaurant-list">{filteredRestaurants.map(r => <button type="button" key={r.id}
          disabled={disabled} className={`restaurant-option ${r.id === restaurantId ? 'selected-restaurant' : ''}`} aria-pressed={r.id === restaurantId}
          onClick={() => selectRestaurant(r.id)}>
          <span className={`queue-dot queue-${queueBand(r)}`} /><span><strong>{r.name}</strong><small>{r.status.toLowerCase()} · {r.queueLength ?? 0} queued</small></span>
        </button>)}</div>
        {session && !filteredRestaurants.length && <p className="dash-empty">No restaurants match your search.</p>}
        <div className="sidebar-note"><strong>Phase II workspace</strong><p>Use Map to browse menus and Orders to follow or prepare an order.</p></div>
      </aside>
      <section className="workspace-panel" id="workspace-panel" role="tabpanel" aria-labelledby={`module-tab-${tabs.indexOf(tab)}`}>
        {tab === 'Map' && <>
          <div className="city-metrics">
            <div><span>Restaurants</span><strong>{session ? restaurants.length : '—'}</strong></div>
            <div><span>Open now</span><strong>{session ? restaurants.filter(r => r.status === 'OPEN').length : '—'}</strong></div>
            <div><span>Visible orders</span><strong>{session ? orders.length : '—'}</strong></div>
            <div><span>Queued orders</span><strong>{session ? restaurants.reduce((sum, r) => sum + (r.queueLength || 0), 0) : '—'}</strong></div>
          </div>
          <section className="dash-card map-card"><div className="dash-section-title"><div><h2>City map</h2><p className="dash-muted">Seeded coordinates · select a restaurant marker</p></div>
            <label className="checkbox-label"><input type="checkbox" checked={showLabels} onChange={e => setShowLabels(e.target.checked)} />Show labels</label>
          </div>
          <SimulatorMap restaurants={restaurants} visibleRestaurants={filteredRestaurants} selectedId={restaurantId} onSelect={selectRestaurant} showLabels={showLabels} disabled={disabled} />
          <div className="map-legend"><span><i className="queue-dot queue-short" />Short: 0–2 queued</span><span><i className="queue-dot queue-building" />Building: 3–5</span><span><i className="queue-dot queue-long" />Long: 6+ or paused</span></div>
          <p className="dash-muted">Schematic grid, not a live street map. Courier positions are not included in your current seed.</p>
          </section>
          {renderMenu()}
        </>}
        {tab === 'Orders' && <>{role === 'COURIER' ? <section className="dash-card"><h2>Courier orders</h2><p>Courier assignments are not implemented yet.</p></section> : renderOrders()}{role === 'CUSTOMER' && renderMenu()}</>}
        {tab === 'Delivery' && <section className="dash-card module-placeholder"><span className="dash-badge">DLV · upcoming module</span><h2>Delivery workspace</h2><p>Courier assignment, pickup, and delivery controls will appear here when the delivery service is implemented.</p><p className="dash-muted">Phase II orders stop at Ready. This tab does not simulate completed deliveries.</p></section>}
        {tab === 'Pricing & History' && <section className="dash-card"><div><h2>Order history</h2><p className="dash-muted">Orders visible to your Java account. Amounts are item subtotals; billing fees and charges are not implemented yet.</p></div>
          {!orders.length ? <p className="dash-empty">{session ? 'No orders to display.' : 'Connect your Java account to load history.'}</p> : <div className="history-table-wrap"><table className="history-table"><thead><tr><th>Order</th><th>Restaurant</th><th>State</th><th>Item subtotal</th></tr></thead><tbody>{orders.map(o => <tr key={o.id}><td>{o.id.slice(0, 8)}</td><td>{restaurants.find(r => r.id === o.restaurantId)?.name || o.restaurantId}</td><td>{o.state}</td><td>{money(o.total)}</td></tr>)}</tbody></table></div>}
        </section>}
      </section>
    </div>
    <section className="dash-card console-card"><div className="dash-section-title"><div><h2>Event & command console</h2><p className="dash-muted">Command results, order state changes, and errors from this browser session.</p></div><span className="dash-badge">{logs.length} entries</span></div>
      <div className="dash-console" role="log" aria-live="polite" aria-relevant="additions" aria-label="Command output">
        {!logs.length && <p>Waiting for simulator commands...</p>}
        {logs.map(entry => <p key={entry.id} className={entry.success ? 'console-success' : 'console-error'}>[{entry.time}] {entry.success ? 'OK' : 'ERROR'} {entry.action}: {entry.message}</p>)}
      </div>
    </section>
  </main>;
}
