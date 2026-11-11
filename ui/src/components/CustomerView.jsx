import { useEffect, useState } from 'react';
import AddressPicker from './AddressPicker';
import LiveMap, { secondsLeft } from './LiveMap';
import MenuItemImage from './MenuItemImage';
import OrderProgress, { stateLabel } from './OrderProgress';
import RestaurantCover from './RestaurantCover';
import { distanceKm, reverseAddress, toKm } from '../services/geo';
import { bandLabels, queueBand } from '../services/queueBand';

const activeStates = ['Placed', 'Accepted', 'Preparing', 'Ready', 'PickedUp'];
const emptyCart = { restaurantId: '', items: {} };
const stateMessages = {
  Placed: 'Waiting for the restaurant to accept your order.',
  Accepted: 'Accepted! The kitchen will start soon.',
  Preparing: 'The kitchen is preparing your order.',
  Ready: 'Ready — a courier is heading to the restaurant.',
  PickedUp: 'Your courier is on the way to you.',
};

const normalise = (value) =>
  value
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase();

function loadAddress() {
  try {
    return JSON.parse(localStorage.getItem('sugarrush.address')) || null;
  } catch {
    return null;
  }
}

export default function CustomerView({ tab, setTab, session, restaurants, orders, couriers, origin, ledger, disabled, money, act, ask }) {
  const [search, setSearch] = useState('');
  const [openId, setOpenId] = useState('');
  const [cart, setCart] = useState(emptyCart);
  const [address, setAddress] = useState(loadAddress);
  const [quote, setQuote] = useState(null);
  const [summary, setSummary] = useState(null);

  const open = restaurants.find((r) => r.id === openId);
  const cartRestaurant = restaurants.find((r) => r.id === cart.restaurantId);
  const cartLines = cartRestaurant ? cartRestaurant.menu.filter((item) => cart.items[item.id] > 0) : [];
  const cartCount = cartLines.reduce((sum, item) => sum + cart.items[item.id], 0);
  const newestFirst = [...orders].reverse();
  const active = newestFirst.filter((o) => activeStates.includes(o.state));
  const past = newestFirst.filter((o) => !activeStates.includes(o.state));
  const restaurantName = (id) => restaurants.find((r) => r.id === id)?.name || 'Restaurant';
  const name = session.user.username.split('@')[0];
  const filtered = restaurants.filter((r) => normalise(r.name).includes(normalise(search.trim())));

  // R-DASH-04: Place Order is only enabled when every rule below passes; the first failing rule is shown.
  const blocker = !cartCount
    ? 'Add at least one item.'
    : cartRestaurant.status !== 'OPEN'
      ? `${cartRestaurant.name} is paused and cannot take orders.`
      : cartLines.some((item) => item.status !== 'AVAILABLE')
        ? 'Remove sold-out items first.'
        : !address
          ? 'Choose a delivery address.'
          : '';

  useEffect(() => {
    try {
      if (address) localStorage.setItem('sugarrush.address', JSON.stringify(address));
    } catch {}
  }, [address]);

  // The fare comes from the backend pricing rules, so the preview always matches the charge (R-PRC-01).
  const cartKey = JSON.stringify(cart);
  useEffect(() => {
    if (!cartCount || cartLines.some((item) => item.status !== 'AVAILABLE')) {
      setQuote(null);
      return;
    }
    let current = true;
    const fields = { restaurantId: cart.restaurantId };
    cartLines.forEach((item) => (fields[`item.${item.id}`] = cart.items[item.id]));
    ask('PRICE_QUOTE', fields)
      .then((result) => current && setQuote(result))
      .catch(() => current && setQuote(null));
    return () => {
      current = false;
    };
  }, [cartKey, restaurants]);

  function changeQuantity(restaurant, item, change) {
    if (cartCount > 0 && cart.restaurantId !== restaurant.id) {
      if (!window.confirm(`Start a new order from ${restaurant.name}? Your cart from ${cartRestaurant.name} will be cleared.`)) return;
      setCart({ restaurantId: restaurant.id, items: { [item.id]: 1 } });
      return;
    }
    const quantity = Math.max(0, Math.min(99, (cart.items[item.id] || 0) + change));
    setCart({ restaurantId: restaurant.id, items: { ...(cart.restaurantId === restaurant.id ? cart.items : {}), [item.id]: quantity } });
  }

  async function placeOrder() {
    const fields = { restaurantId: cart.restaurantId, address: address.label, addressX: address.x, addressY: address.y };
    cartLines.forEach((item) => (fields[`item.${item.id}`] = cart.items[item.id]));
    const placed = await act('PLACE_ORDER', fields);
    if (placed) {
      setCart(emptyCart);
      setOpenId('');
      setTab('Orders');
    }
  }

  async function pickOnMap(latitude, longitude) {
    const label = await reverseAddress(latitude, longitude);
    setAddress({ label, ...toKm(origin, latitude, longitude) });
  }

  async function showSummary(orderId) {
    const result = await act('VIEW_ORDER_SUMMARY', { orderId }, { refresh: false });
    if (result) setSummary(result);
  }

  const courierFor = (order) => couriers.find((c) => c.orderId === order.id);
  const dropFor = (order) =>
    order.deliveryAddress ? [{ id: order.id, ...order.deliveryAddress, short: 'You' }] : [];

  function trackerMap(order, className) {
    const restaurant = restaurants.find((r) => r.id === order.restaurantId);
    const courier = courierFor(order);
    return (
      <LiveMap
        origin={origin}
        className={className}
        restaurants={restaurant ? [restaurant] : []}
        couriers={courier ? [courier] : []}
        drops={dropFor(order)}
        showLabels={false}
        legend={false}
      />
    );
  }

  function eta(order) {
    const courier = courierFor(order);
    if (!courier) return order.state === 'Ready' ? 'Finding the nearest courier…' : stateMessages[order.state];
    const seconds = secondsLeft(courier);
    if (order.state === 'PickedUp')
      return seconds ? `Courier C${courier.id} arrives in about ${seconds}s` : `Courier C${courier.id} is at your door`;
    return seconds ? `Courier C${courier.id} reaches the restaurant in ${seconds}s` : `Courier C${courier.id} is at the restaurant`;
  }

  function renderHero() {
    const tracked = active[0];
    return (
      <div className="hero-row">
        <div className="hero-card">
          <h1>Hungry, {name}?</h1>
          <p>
            {restaurants.filter((r) => r.status === 'OPEN').length} kitchens are open in Montreal. Green means a short wait.
          </p>
          <label className="hero-search">
            <span className="visually-hidden">Search restaurants</span>
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
              <circle cx="11" cy="11" r="7" />
              <path d="m20 20-3.5-3.5" />
            </svg>
            <input
              type="search"
              placeholder="Search restaurants…"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setOpenId('');
              }}
            />
          </label>
        </div>
        <div className="tracker-card">
          {tracked ? (
            <>
              <div className="row between">
                <div>
                  <strong>{tracked.state === 'PickedUp' ? 'Your order is on its way' : 'Your order is in progress'}</strong>
                  <p className="muted">
                    {restaurantName(tracked.restaurantId)} · {eta(tracked)}
                  </p>
                </div>
                <span className={`state-pill state-${tracked.state.toLowerCase()}`}>{stateLabel(tracked.state)}</span>
              </div>
              {trackerMap(tracked, 'map-tracker')}
              <OrderProgress state={tracked.state} compact />
            </>
          ) : (
            <div className="tracker-empty">
              <span className="eyebrow">How it works</span>
              <ol>
                <li>Pick a restaurant on the map or below.</li>
                <li>Add items and choose your Montreal address.</li>
                <li>Track your courier live until it reaches your door.</li>
              </ol>
            </div>
          )}
        </div>
      </div>
    );
  }

  function renderRestaurants() {
    return (
      <>
        <h2 className="section-title">{search.trim() ? `Results for “${search.trim()}”` : 'Restaurants'}</h2>
        {!filtered.length && (
          <p className="empty-note">{restaurants.length ? 'No restaurants match your search.' : 'No restaurants are available yet.'}</p>
        )}
        <div className="card-grid">
          {filtered.map((r, index) => {
            const band = queueBand(r);
            return (
              <article key={r.id} className={`restaurant-tile${r.status === 'OPEN' ? '' : ' off'}`}>
                <div className="tile-art">
                  <RestaurantCover name={r.name} index={index} />
                  <span className={`band-chip band-${band}`}>
                    <i />
                    {r.status === 'OPEN' ? `${r.queueLength} in queue` : 'Paused'}
                  </span>
                </div>
                <strong>{r.name}</strong>
                {r.address && <span className="address-line">{r.address}</span>}
                <span className="muted">
                  {r.status === 'OPEN' ? bandLabels[band] : 'Not taking orders'} · {r.menu.length} item{r.menu.length === 1 ? '' : 's'}
                </span>
                <button type="button" className="f-btn soft block sm" onClick={() => setOpenId(r.id)}>
                  {r.status === 'OPEN' ? 'View menu' : 'View menu (paused)'}
                </button>
              </article>
            );
          })}
        </div>
      </>
    );
  }

  function renderMenu() {
    const inCart = cart.restaurantId === open.id ? cart.items : {};
    const band = queueBand(open);
    const restaurantPoint = { x: open.x, y: open.y };
    return (
      <div className="menu-layout">
        <section className="panel">
          <div className="row between">
            <button type="button" className="link-button" onClick={() => setOpenId('')}>
              ← All restaurants
            </button>
            <span className={`band-chip band-${band}`}>
              <i />
              {open.status === 'OPEN' ? `${bandLabels[band]} · ${open.queueLength} ahead` : 'Paused'}
            </span>
          </div>
          <h2>{open.name}</h2>
          {open.address && <span className="address-line">{open.address}</span>}
          {open.status !== 'OPEN' && <p className="warn-note">This restaurant is paused. You can browse, but ordering is disabled.</p>}
          {!open.menu.length && <p className="empty-note">This restaurant has no menu items yet.</p>}
          <ul className="menu-items">
            {open.menu.map((item) => (
              <li key={item.id} className={item.status === 'AVAILABLE' ? '' : 'off'}>
                <MenuItemImage item={item} />
                <div className="grow">
                  <strong>{item.name}</strong>
                  {item.description && <span className="muted">{item.description}</span>}
                  <span className="price">
                    {money(item.price)}
                    {item.status !== 'AVAILABLE' && <em className="sold-out"> · Sold out</em>}
                  </span>
                </div>
                {inCart[item.id] > 0 ? (
                  <div className="stepper">
                    <button type="button" aria-label={`Remove one ${item.name}`} disabled={disabled} onClick={() => changeQuantity(open, item, -1)}>
                      −
                    </button>
                    <span>{inCart[item.id]}</span>
                    <button
                      type="button"
                      aria-label={`Add one ${item.name}`}
                      disabled={disabled || item.status !== 'AVAILABLE' || inCart[item.id] >= 99}
                      onClick={() => changeQuantity(open, item, 1)}
                    >
                      +
                    </button>
                  </div>
                ) : (
                  <button
                    type="button"
                    className="add-button"
                    aria-label={`Add ${item.name}`}
                    disabled={disabled || item.status !== 'AVAILABLE' || open.status !== 'OPEN'}
                    onClick={() => changeQuantity(open, item, 1)}
                  >
                    +
                  </button>
                )}
              </li>
            ))}
          </ul>
        </section>
        <aside className="panel checkout">
          <h3>Your order{cartRestaurant ? ` · ${cartRestaurant.name}` : ''}</h3>
          {cartLines.map((item) => (
            <div className="line" key={item.id}>
              <span>
                {cart.items[item.id]} × {item.name}
              </span>
              <span>{money(Number(item.price) * cart.items[item.id])}</span>
            </div>
          ))}
          {!cartCount && <p className="muted">Your cart is empty.</p>}
          <h4>Deliver to</h4>
          <AddressPicker origin={origin} value={address} onChange={setAddress} disabled={disabled} />
          <LiveMap
            origin={origin}
            className="map-checkout"
            restaurants={[open]}
            drops={address ? [{ id: 'home', ...address, short: 'You' }] : []}
            onPick={disabled ? undefined : pickOnMap}
            showLabels={false}
            legend={false}
          />
          {address && <p className="hint">{distanceKm(restaurantPoint, address).toFixed(1)} km from {open.name} (straight line)</p>}
          {quote && (
            <div className="fare">
              <div className="line">
                <span>Subtotal</span>
                <span>{money(quote.subtotal)}</span>
              </div>
              <div className="line">
                <span>Delivery fee</span>
                <span>{money(quote.deliveryFee)}</span>
              </div>
              <div className="line">
                <span>Service fee</span>
                <span>{money(quote.serviceFee)}</span>
              </div>
              {Number(quote.smallOrderSurcharge) > 0 && (
                <div className="line warn">
                  <span>Small-order surcharge (under {money(quote.smallOrderThreshold)})</span>
                  <span>{money(quote.smallOrderSurcharge)}</span>
                </div>
              )}
              <div className="line total">
                <span>Total</span>
                <span>{money(quote.total)}</span>
              </div>
            </div>
          )}
          <button className="f-btn primary block" disabled={disabled || Boolean(blocker)} onClick={placeOrder}>
            Place order{quote && !blocker ? ` · ${money(quote.total)}` : ''}
          </button>
          {blocker && <p className="hint reason">{blocker}</p>}
          {cartCount > 0 && (
            <button type="button" className="link-button" disabled={disabled} onClick={() => setCart(emptyCart)}>
              Clear cart
            </button>
          )}
        </aside>
      </div>
    );
  }

  function renderHome() {
    return (
      <>
        {renderHero()}
        {open ? renderMenu() : renderRestaurants()}
        {!open && (
          <section className="panel">
            <div className="row between">
              <h2>Montreal map</h2>
              <span className="muted">Select a restaurant marker to open its menu</span>
            </div>
            <LiveMap
              origin={origin}
              className="map-wide"
              restaurants={filtered}
              couriers={couriers}
              drops={address ? [{ id: 'home', ...address, short: 'You' }] : []}
              selectedId={openId}
              onSelectRestaurant={setOpenId}
            />
          </section>
        )}
      </>
    );
  }

  function renderOrders() {
    return (
      <>
        <h2 className="section-title">Orders in progress</h2>
        {!active.length && (
          <div className="panel empty-panel">
            <p>No orders in progress.</p>
            <button className="f-btn primary" onClick={() => setTab('Home')}>
              Browse restaurants
            </button>
          </div>
        )}
        {active.map((order) => (
          <article className="panel order-track" key={order.id}>
            <div className="track-info">
              <div className="row between">
                <div>
                  <h3>{restaurantName(order.restaurantId)}</h3>
                  <span className="muted">
                    Order {order.id.slice(0, 8)} · {money(order.total)}
                  </span>
                </div>
                <span className={`state-pill state-${order.state.toLowerCase()}`}>{stateLabel(order.state)}</span>
              </div>
              <OrderProgress state={order.state} />
              <p className="track-eta">{eta(order)}</p>
              <p className="muted">Deliver to {order.deliveryAddress?.label}</p>
              <ul className="plain-list">
                {order.lines.map((line) => (
                  <li key={line.item.id}>
                    {line.quantity} × {line.item.name}
                  </li>
                ))}
              </ul>
              {order.state === 'Placed' && (
                <button className="f-btn ghost sm" disabled={disabled} onClick={() => act('CANCEL_ORDER', { orderId: order.id })}>
                  Cancel order
                </button>
              )}
            </div>
            {trackerMap(order, 'map-track')}
          </article>
        ))}
        {past.length > 0 && (
          <>
            <h2 className="section-title">Past orders</h2>
            <div className="panel">
              {past.map((order) => (
                <div className="past-row" key={order.id}>
                  <div className="grow">
                    <strong>{restaurantName(order.restaurantId)}</strong>
                    <span className="muted">{order.lines.map((l) => `${l.quantity} × ${l.item.name}`).join(', ')}</span>
                    {order.rejectionReason && <span className="muted">Reason: {order.rejectionReason}</span>}
                  </div>
                  <strong>{money(order.total)}</strong>
                  <span className={`state-pill state-${order.state.toLowerCase()}`}>{stateLabel(order.state)}</span>
                  {['Delivered', 'Refunded'].includes(order.state) && (
                    <button className="f-btn ghost sm" disabled={disabled} onClick={() => showSummary(order.id)}>
                      Summary
                    </button>
                  )}
                </div>
              ))}
            </div>
          </>
        )}
        {summary && <SummaryCard summary={summary} money={money} onClose={() => setSummary(null)} />}
      </>
    );
  }

  return (
    <>
      {tab === 'Home' && renderHome()}
      {tab === 'Orders' && renderOrders()}
      {tab === 'Receipts' && <LedgerTable entries={ledger} restaurants={restaurants} money={money} title="Your receipts" />}
    </>
  );
}

export function SummaryCard({ summary, money, onClose }) {
  return (
    <section className="panel summary-card" aria-label="Order summary">
      <div className="row between">
        <h3>Order summary · {summary.restaurantName}</h3>
        <button type="button" className="link-button" onClick={onClose}>
          Close
        </button>
      </div>
      {summary.lines.map((line) => (
        <div className="line" key={line.item.id}>
          <span>
            {line.quantity} × {line.item.name}
          </span>
          <span>{money(Number(line.item.price) * line.quantity)}</span>
        </div>
      ))}
      <div className="line">
        <span>Subtotal</span>
        <span>{money(summary.subtotal)}</span>
      </div>
      <div className="line total">
        <span>Total charged</span>
        <span>{money(summary.totalCharged)}</span>
      </div>
      <p className="muted">Delivered {new Date(summary.deliveredAt).toLocaleString()}</p>
    </section>
  );
}

const typeLabels = { ORDER_STATE: 'Status change', CHARGE: 'Charge', NO_CHARGE: 'No charge', REFUND: 'Refund' };

export function LedgerTable({ entries, restaurants, money, title, actions }) {
  const rows = [...entries].reverse();
  return (
    <section className="panel">
      <div className="row between">
        <h2>{title}</h2>
        <span className="muted">{entries.length} ledger entries</span>
      </div>
      {!rows.length ? (
        <p className="empty-note">No ledger entries yet. Charges appear here once an order is delivered.</p>
      ) : (
        <div className="table-wrap">
          <table className="f-table">
            <thead>
              <tr>
                <th>Order</th>
                <th>Restaurant</th>
                <th>Entry</th>
                <th>State</th>
                <th>Placed</th>
                <th>Recorded</th>
                <th className="num">Amount</th>
                {actions && <th />}
              </tr>
            </thead>
            <tbody>
              {rows.map((entry) => (
                <tr key={entry.entryId} className={`entry-${entry.type.toLowerCase()}`}>
                  <td className="mono">{entry.orderId.slice(0, 8)}</td>
                  <td>{restaurants.find((r) => r.id === entry.restaurantId)?.name || entry.restaurantId}</td>
                  <td>{typeLabels[entry.type] || entry.type}</td>
                  <td>{stateLabel(entry.state)}</td>
                  <td>{new Date(entry.placedAt).toLocaleTimeString()}</td>
                  <td>{new Date(entry.recordedAt).toLocaleTimeString()}</td>
                  <td className="num">{entry.type === 'REFUND' ? `−${money(entry.totalCharged)}` : money(entry.totalCharged)}</td>
                  {actions && <td>{actions(entry)}</td>}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
