import { useState } from 'react';
import AddressPicker from './AddressPicker';
import LiveMap, { secondsLeft } from './LiveMap';
import MenuItemImage from './MenuItemImage';
import { stateLabel } from './OrderProgress';
import { LedgerTable } from './CustomerView';
import { reverseAddress, toKm } from '../services/geo';
import { bandLabels, queueBand } from '../services/queueBand';

const emptyItem = { id: '', name: '', price: '', description: '', imageUrl: '' };

export default function RestaurantView({ tab, session, restaurants, orders, queues, couriers, origin, ledger, loaded, disabled, money, act }) {
  const [restaurantId, setRestaurantId] = useState('');
  const [reasons, setReasons] = useState({});
  const [draft, setDraft] = useState(emptyItem);
  const [newName, setNewName] = useState('');
  const [newSpot, setNewSpot] = useState(null);

  const restaurant = restaurants.find((r) => r.id === restaurantId) || restaurants[0];
  const mine = restaurant ? orders.filter((o) => o.restaurantId === restaurant.id) : [];
  const queue = restaurant ? queues[restaurant.id] || [] : [];
  const placed = mine.filter((o) => o.state === 'Placed');
  const kitchen = mine.filter((o) => ['Accepted', 'Preparing', 'Ready', 'PickedUp'].includes(o.state));
  const preparing = mine.find((o) => o.state === 'Preparing');
  const head = mine.find((o) => o.id === queue[0]);
  const next = preparing || head;
  const name = session.user.username.split('@')[0];

  async function createRestaurant(event) {
    event.preventDefault();
    const created = await act('CREATE_RESTAURANT', { name: newName.trim() });
    if (created) {
      setNewName('');
      setRestaurantId(created.id);
    }
  }

  async function saveItem(event) {
    event.preventDefault();
    const existing = restaurant.menu.find((item) => item.id === draft.id);
    const saved = await act('PUT_MENU_ITEM', {
      restaurantId: restaurant.id,
      itemId: draft.id || crypto.randomUUID(),
      name: draft.name.trim(),
      price: Number(draft.price).toFixed(2),
      description: draft.description.trim(),
      imageUrl: draft.imageUrl.trim(),
      itemStatus: existing?.status || 'AVAILABLE',
    });
    if (saved) setDraft(emptyItem);
  }

  const toggleSoldOut = (item) =>
    act('PUT_MENU_ITEM', {
      restaurantId: restaurant.id,
      itemId: item.id,
      name: item.name,
      price: Number(item.price).toFixed(2),
      description: item.description || '',
      imageUrl: item.imageUrl || '',
      itemStatus: item.status === 'AVAILABLE' ? 'SOLD_OUT' : 'AVAILABLE',
    });

  function courierNote(order) {
    if (order.state === 'PickedUp') return 'Out for delivery';
    if (order.state !== 'Ready') return '';
    const courier = couriers.find((c) => c.orderId === order.id);
    if (!courier) return 'No idle courier yet — waiting in the dispatch queue';
    const seconds = secondsLeft(courier);
    return seconds ? `Courier C${courier.id} arrives in ${seconds}s` : `Courier C${courier.id} is here for pickup`;
  }

  // Wait for the first load, so an owner is never offered to create a restaurant they already have.
  if (!restaurant && !loaded) return <p className="empty-note">Loading your restaurant…</p>;
  if (!restaurant)
    return (
      <section className="panel narrow">
        <h2>Create your restaurant</h2>
        <p className="muted">
          Your account doesn’t own a restaurant yet (an operator reset removes every restaurant). Name it to start taking
          orders; you can set its address and menu next.
        </p>
        <CreateForm value={newName} onChange={setNewName} onSubmit={createRestaurant} disabled={disabled} />
      </section>
    );

  const band = queueBand(restaurant);
  const selector = restaurants.length > 1 && (
    <select className="store-select" value={restaurant.id} onChange={(e) => setRestaurantId(e.target.value)} aria-label="Restaurant">
      {restaurants.map((r) => (
        <option key={r.id} value={r.id}>
          {r.name}
        </option>
      ))}
    </select>
  );

  function renderToday() {
    return (
      <>
        <div className="hero-row">
          <div className="hero-card">
            <h1>Hello, {restaurant.name}</h1>
            {restaurant.address && <p className="hero-address">{restaurant.address}</p>}
            <p>
              {placed.length} new order{placed.length === 1 ? '' : 's'} · {queue.length} in your queue · signed in as {name}
            </p>
            <div className="row wrap">
              {selector}
              <span className={`band-chip on-dark band-${band}`}>
                <i />
                {restaurant.status === 'OPEN' ? `${bandLabels[band]} · ${restaurant.queueLength} queued` : 'Paused'}
              </span>
              <button
                className={`f-btn sm ${restaurant.status === 'OPEN' ? 'on-dark' : 'light'}`}
                disabled={disabled}
                onClick={() => act('SET_RESTAURANT_STATUS', { restaurantId: restaurant.id, status: restaurant.status === 'OPEN' ? 'PAUSED' : 'OPEN' })}
              >
                {restaurant.status === 'OPEN' ? 'Pause new orders' : 'Reopen restaurant'}
              </button>
            </div>
          </div>
          <div className="next-card">
            <span className="eyebrow">Up next</span>
            {next ? (
              <>
                <h2 className="mono">#{next.id.slice(0, 8)}</h2>
                <p>{next.lines.map((l) => `${l.quantity} × ${l.item.name}`).join(', ')}</p>
                {next.state === 'Preparing' ? (
                  <button className="f-btn primary" disabled={disabled} onClick={() => act('MARK_READY', { orderId: next.id })}>
                    Mark ready for pickup
                  </button>
                ) : (
                  <button className="f-btn primary" disabled={disabled} onClick={() => act('START_PREPARING', { orderId: next.id })}>
                    Start preparing
                  </button>
                )}
                {queue.length > 1 && <span className="hint">{queue.length - 1} more accepted order{queue.length === 2 ? '' : 's'} after this one</span>}
              </>
            ) : (
              <p className="muted">Nothing to prepare. Accepted orders line up here in queue order.</p>
            )}
          </div>
        </div>

        <h2 className="section-title">New orders</h2>
        {!placed.length && <p className="empty-note">No new orders right now.</p>}
        <div className="card-grid two">
          {placed.map((order) => {
            const reason = reasons[order.id] || '';
            return (
              <article className="panel order-card" key={order.id}>
                <div className="row between">
                  <strong className="mono">#{order.id.slice(0, 8)}</strong>
                  <span className="muted">{money(order.total)}</span>
                </div>
                <p>{order.lines.map((l) => `${l.quantity} × ${l.item.name}`).join(' · ')}</p>
                <p className="muted">Deliver to {order.deliveryAddress?.label}</p>
                <div className="row">
                  <button className="f-btn primary sm" disabled={disabled} onClick={() => act('ACCEPT_ORDER', { orderId: order.id })}>
                    Accept
                  </button>
                  <input
                    className="grow"
                    aria-label="Reason for rejection"
                    placeholder="Reason for rejection…"
                    value={reason}
                    onChange={(e) => setReasons({ ...reasons, [order.id]: e.target.value })}
                  />
                  <button
                    className="f-btn ghost sm"
                    disabled={disabled || !reason.trim()}
                    title={reason.trim() ? '' : 'Type a reason first'}
                    onClick={() => act('REJECT_ORDER', { orderId: order.id, reason })}
                  >
                    Reject
                  </button>
                </div>
              </article>
            );
          })}
        </div>

        <div className="split">
          <section className="panel">
            <h2>In the kitchen</h2>
            {!kitchen.length && <p className="empty-note">Accepted orders appear here.</p>}
            {kitchen.map((order) => {
              const position = queue.indexOf(order.id);
              const locked = order.state === 'Accepted' && position > 0;
              return (
                <div className="kitchen-row" key={order.id}>
                  <span className={`state-pill state-${order.state.toLowerCase()}`}>{stateLabel(order.state)}</span>
                  <strong className="mono">#{order.id.slice(0, 8)}</strong>
                  <span className="grow muted">
                    {order.state === 'Accepted' ? `#${position + 1} in queue` : courierNote(order) || order.lines.map((l) => `${l.quantity} × ${l.item.name}`).join(', ')}
                  </span>
                  {order.state === 'Accepted' && (
                    <button
                      className="f-btn primary sm"
                      disabled={disabled || locked || Boolean(preparing)}
                      title={locked ? 'Prepare orders in queue order' : preparing ? 'Finish the current order first' : ''}
                      onClick={() => act('START_PREPARING', { orderId: order.id })}
                    >
                      {locked || preparing ? 'Waiting' : 'Start'}
                    </button>
                  )}
                  {order.state === 'Preparing' && (
                    <button className="f-btn primary sm" disabled={disabled} onClick={() => act('MARK_READY', { orderId: order.id })}>
                      Mark ready
                    </button>
                  )}
                </div>
              );
            })}
          </section>
          <section className="panel">
            <h2>Couriers near you</h2>
            <LiveMap
              origin={origin}
              className="map-side"
              restaurants={[restaurant]}
              couriers={couriers}
              drops={kitchen.filter((o) => o.deliveryAddress).map((o) => ({ id: o.id, ...o.deliveryAddress, short: `#${o.id.slice(0, 4)}` }))}
              showLabels={false}
            />
          </section>
        </div>
      </>
    );
  }

  async function moveTo(spot) {
    const moved = await act('SET_RESTAURANT_LOCATION', { restaurantId: restaurant.id, address: spot.label, addressX: spot.x, addressY: spot.y });
    if (moved) setNewSpot(null);
  }

  function renderLocation() {
    return (
      <section className="panel">
        <h3>Restaurant address</h3>
        <p className="address-line">{restaurant.address || 'No address yet'}</p>
        <p className="hint">Couriers are sent here for pickup and the map shows your store at this address.</p>
        <AddressPicker origin={origin} value={newSpot} onChange={setNewSpot} disabled={disabled} />
        <LiveMap
          origin={origin}
          className="map-checkout"
          restaurants={[restaurant]}
          drops={newSpot ? [{ id: 'new-spot', ...newSpot, short: 'New address' }] : []}
          onPick={disabled ? undefined : async (latitude, longitude) => setNewSpot({ label: await reverseAddress(latitude, longitude), ...toKm(origin, latitude, longitude) })}
          showLabels={false}
          legend={false}
        />
        <button className="f-btn primary" disabled={disabled || !newSpot} onClick={() => moveTo(newSpot)}>
          {newSpot ? `Move restaurant to ${newSpot.label}` : 'Choose a new address to move'}
        </button>
      </section>
    );
  }

  function renderMenu() {
    return (
      <div className="split">
        <section className="panel">
          <div className="row between">
            <h2>{restaurant.name} · Menu</h2>
            {selector}
          </div>
          {!restaurant.menu.length && <p className="empty-note">Your menu is empty. Add the first item.</p>}
          <ul className="menu-items">
            {restaurant.menu.map((item) => (
              <li key={item.id} className={item.status === 'AVAILABLE' ? '' : 'off'}>
                <MenuItemImage item={item} />
                <div className="grow">
                  <strong>{item.name}</strong>
                  <span className="price">
                    {money(item.price)}
                    {item.status !== 'AVAILABLE' && <em className="sold-out"> · Sold out</em>}
                  </span>
                </div>
                <label className="switch">
                  <input type="checkbox" checked={item.status === 'AVAILABLE'} disabled={disabled} onChange={() => toggleSoldOut(item)} />
                  <span>{item.status === 'AVAILABLE' ? 'Available' : 'Sold out'}</span>
                </label>
                <button
                  className="f-btn ghost sm"
                  disabled={disabled}
                  onClick={() => setDraft({ id: item.id, name: item.name, price: String(item.price), description: item.description || '', imageUrl: item.imageUrl || '' })}
                >
                  Edit
                </button>
                <button
                  className="f-btn danger-ghost sm"
                  disabled={disabled}
                  onClick={() => window.confirm(`Delete ${item.name} from the menu?`) && act('DELETE_MENU_ITEM', { restaurantId: restaurant.id, itemId: item.id })}
                >
                  Delete
                </button>
              </li>
            ))}
          </ul>
        </section>
        <div className="stack">
          {renderLocation()}
          <form className="panel form" onSubmit={saveItem}>
            <h3>{draft.id ? 'Edit menu item' : 'Add menu item'}</h3>
            <label>
              Item name
              <input required maxLength="80" value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
            </label>
            <label>
              Price (CAD)
              <input required type="number" min="0" step="0.01" value={draft.price} onChange={(e) => setDraft({ ...draft, price: e.target.value })} />
            </label>
            <label>
              Description (optional)
              <textarea maxLength="500" value={draft.description} onChange={(e) => setDraft({ ...draft, description: e.target.value })} />
            </label>
            <label>
              Image URL (optional)
              <input type="url" maxLength="2048" value={draft.imageUrl} onChange={(e) => setDraft({ ...draft, imageUrl: e.target.value })} />
            </label>
            <div className="row">
              <button className="f-btn primary" disabled={disabled}>
                {draft.id ? 'Save item' : 'Add item'}
              </button>
              {draft.id && (
                <button type="button" className="f-btn ghost" onClick={() => setDraft(emptyItem)}>
                  Cancel
                </button>
              )}
            </div>
          </form>
          <section className="panel">
            <h3>Open another restaurant</h3>
            <CreateForm value={newName} onChange={setNewName} onSubmit={createRestaurant} disabled={disabled} />
          </section>
        </div>
      </div>
    );
  }

  return (
    <>
      {tab === 'Today' && renderToday()}
      {tab === 'Menu' && renderMenu()}
      {tab === 'History' && <LedgerTable entries={ledger} restaurants={restaurants} money={money} title="Orders and charges" />}
    </>
  );
}

function CreateForm({ value, onChange, onSubmit, disabled }) {
  return (
    <form className="row" onSubmit={onSubmit}>
      <input className="grow" required maxLength="80" aria-label="Restaurant name" placeholder="Restaurant name" value={value} onChange={(e) => onChange(e.target.value)} />
      <button className="f-btn primary sm" disabled={disabled}>
        Create
      </button>
    </form>
  );
}
