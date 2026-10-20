import { useState } from 'react';
import RestaurantCover from './RestaurantCover';
import MenuItemImage from './MenuItemImage';
import { queueBand } from './SimulatorMap';
import '../styles/customer.css';

const activeStates = ['Placed', 'Accepted', 'Preparing', 'Ready'];

const stateMessages = {
  Placed: 'Waiting for the restaurant to accept your order.',
  Accepted: 'The restaurant accepted your order. It will start soon.',
  Preparing: 'The kitchen is preparing your order.',
  Ready: 'Your order is ready and waiting for a courier.',
};

const busyLabels = {
  short: 'Not busy',
  building: 'Getting busy',
  long: 'Very busy',
};

const emptyCart = { restaurantId: '', items: {} };

const normalise = (value) =>
  value
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase();

export default function CustomerView({ restaurants, orders, disabled, money, onPlace, onCancel, onRefresh }) {
  const [view, setView] = useState('browse');
  const [search, setSearch] = useState('');
  const [openId, setOpenId] = useState('');
  const [cart, setCart] = useState(emptyCart);

  const open = restaurants.find((r) => r.id === openId);
  const currentView = view === 'restaurant' && !open ? 'browse' : view;
  const filtered = restaurants.filter((r) => normalise(r.name).includes(normalise(search.trim())));
  const cartRestaurant = restaurants.find((r) => r.id === cart.restaurantId);
  const cartLines = cartRestaurant ? cartRestaurant.menu.filter((item) => cart.items[item.id] > 0) : [];
  const cartCount = cartLines.reduce((sum, item) => sum + cart.items[item.id], 0);
  const cartTotal = cartLines.reduce((sum, item) => sum + Number(item.price) * cart.items[item.id], 0);
  const canPlace =
    cartRestaurant?.status === 'OPEN' && cartCount > 0 && cartLines.every((item) => item.status === 'AVAILABLE');
  const newestFirst = [...orders].reverse();
  const activeOrders = newestFirst.filter((order) => activeStates.includes(order.state));
  const pastOrders = newestFirst.filter((order) => !activeStates.includes(order.state));
  const restaurantName = (id) => restaurants.find((r) => r.id === id)?.name || id;
  const coverIndex = (id) => restaurants.findIndex((r) => r.id === id);

  function openRestaurant(id) {
    setOpenId(id);
    setView('restaurant');
  }

  function changeQuantity(restaurant, item, change) {
    if (cartCount > 0 && cart.restaurantId !== restaurant.id) {
      if (!window.confirm(`Start a new order from ${restaurant.name}? Your cart from ${cartRestaurant.name} will be cleared.`))
        return;
      setCart({ restaurantId: restaurant.id, items: { [item.id]: 1 } });
      return;
    }
    const quantity = Math.max(0, Math.min(99, (cart.items[item.id] || 0) + change));
    setCart({
      restaurantId: restaurant.id,
      items: { ...(cart.restaurantId === restaurant.id ? cart.items : {}), [item.id]: quantity },
    });
  }

  function checkout() {
    const items = Object.fromEntries(cartLines.map((item) => [item.id, cart.items[item.id]]));
    onPlace(cart.restaurantId, items, () => {
      setCart(emptyCart);
      setView('orders');
    });
  }

  function renderStepper(restaurant, item, quantity) {
    return (
      <div className="stepper">
        <button
          type="button"
          aria-label={`Remove one ${item.name}`}
          disabled={disabled}
          onClick={() => changeQuantity(restaurant, item, -1)}
        >
          −
        </button>
        <span>{quantity}</span>
        <button
          type="button"
          aria-label={`Add one ${item.name}`}
          disabled={disabled || item.status !== 'AVAILABLE' || quantity >= 99}
          onClick={() => changeQuantity(restaurant, item, 1)}
        >
          +
        </button>
      </div>
    );
  }

  function renderBrowse() {
    return (
      <section className="customer-browse">
        <header className="browse-heading">
          <h1>What are you craving?</h1>
          <p>Fresh desserts from Montreal’s best kitchens.</p>
        </header>
        <label className="customer-search">
          <span className="visually-hidden">Search restaurants</span>
          <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
            <circle cx="11" cy="11" r="7" fill="none" stroke="currentColor" strokeWidth="2" />
            <path d="m20 20-3.5-3.5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
          </svg>
          <input
            type="search"
            placeholder="Search restaurants"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </label>
        <h2>{search.trim() ? `Results for “${search.trim()}”` : 'All restaurants'}</h2>
        {!filtered.length && (
          <p className="dash-empty">
            {restaurants.length ? 'No restaurants match your search.' : 'No restaurants are available yet.'}
          </p>
        )}
        <div className="restaurant-grid">
          {filtered.map((r) => (
            <button
              type="button"
              key={r.id}
              className={`restaurant-card${r.status === 'OPEN' ? '' : ' restaurant-unavailable'}`}
              onClick={() => openRestaurant(r.id)}
            >
              <span className="restaurant-photo">
                <RestaurantCover name={r.name} index={coverIndex(r.id)} />
                {r.status !== 'OPEN' && <span className="photo-status">{r.status === 'PAUSED' ? 'Paused' : 'Closed'}</span>}
              </span>
              <span className="restaurant-card-body">
                <small>
                  {r.status === 'OPEN' ? busyLabels[queueBand(r)] : 'Not accepting orders'} · {r.menu.length} item
                  {r.menu.length === 1 ? '' : 's'}
                </small>
              </span>
            </button>
          ))}
        </div>
      </section>
    );
  }

  function renderCart() {
    return (
      <aside className="dash-card cart-panel">
        <h2>Your order</h2>
        {!cartCount ? (
          <p className="dash-muted">Add items from the menu to start an order.</p>
        ) : (
          <>
            <p className="dash-muted">From {cartRestaurant.name}</p>
            <ul className="cart-lines">
              {cartLines.map((item) => (
                <li key={item.id}>
                  <div>
                    <strong>{item.name}</strong>
                    <small>
                      {item.status === 'AVAILABLE' ? money(Number(item.price) * cart.items[item.id]) : 'Sold out'}
                    </small>
                  </div>
                  {renderStepper(cartRestaurant, item, cart.items[item.id])}
                </li>
              ))}
            </ul>
            <div className="cart-total">
              <span>Subtotal</span>
              <strong>{money(cartTotal)}</strong>
            </div>
            {cartRestaurant.status !== 'OPEN' && (
              <p className="form-alert">{cartRestaurant.name} is not accepting orders right now.</p>
            )}
            {cartLines.some((item) => item.status !== 'AVAILABLE') && (
              <p className="form-alert">Remove sold-out items to place your order.</p>
            )}
            <button className="btn btn-primary" disabled={disabled || !canPlace} onClick={checkout}>
              Place order · {money(cartTotal)}
            </button>
            <button className="btn-link" type="button" disabled={disabled} onClick={() => setCart(emptyCart)}>
              Clear cart
            </button>
          </>
        )}
      </aside>
    );
  }

  function renderRestaurant() {
    const inCart = cart.restaurantId === open.id ? cart.items : {};
    return (
      <div className="restaurant-page">
        <section className="restaurant-main">
          <header className="restaurant-hero">
            <RestaurantCover name={open.name} index={coverIndex(open.id)} wide />
            <button className="back-button" type="button" aria-label="All restaurants" onClick={() => setView('browse')}>
              ←
            </button>
          </header>
          <div className="restaurant-summary">
            <h2 className="visually-hidden">{open.name}</h2>
            <p>
              {open.status === 'OPEN'
                ? `${busyLabels[queueBand(open)]} · ${open.queueLength} order${open.queueLength === 1 ? '' : 's'} in the kitchen`
                : 'Not accepting orders right now. You can still look at the menu.'}
            </p>
            <span className={`status-tag status-${open.status.toLowerCase()}`}>{open.status}</span>
          </div>
          <h3>Menu</h3>
          {!open.menu.length && <p className="dash-empty">This restaurant has no menu items yet.</p>}
          <div className="menu-grid">
            {open.menu.map((item) => (
              <article
                key={item.id}
                className={`menu-card${item.status === 'AVAILABLE' ? '' : ' menu-card-sold-out'}`}
              >
                <MenuItemImage item={item} />
                <div>
                  <strong>{item.name}</strong>
                  {item.description && <p>{item.description}</p>}
                  <p>{money(item.price)}</p>
                  {item.status !== 'AVAILABLE' && <span className="sold-out-tag">Sold out</span>}
                </div>
                {inCart[item.id] > 0 ? (
                  renderStepper(open, item, inCart[item.id])
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
              </article>
            ))}
          </div>
        </section>
        {renderCart()}
      </div>
    );
  }

  function renderOrders() {
    return (
      <section className="customer-orders">
        <div className="dash-section-title">
          <h2>Your orders</h2>
          <button className="btn btn-secondary" disabled={disabled} onClick={onRefresh}>
            Refresh status
          </button>
        </div>
        {!orders.length && (
          <div className="dash-card orders-empty">
            <p>You haven’t placed any orders yet.</p>
            <button className="btn btn-primary" onClick={() => setView('browse')}>
              Browse restaurants
            </button>
          </div>
        )}
        {activeOrders.length > 0 && (
          <>
            <h3>In progress</h3>
            <div className="order-list">
              {activeOrders.map((order) => (
                <article className="dash-card tracking-card" key={order.id}>
                  <div className="dash-section-title">
                    <div>
                      <h3>{restaurantName(order.restaurantId)}</h3>
                      <p className="dash-muted">
                        Order {order.id.slice(0, 8)} · {money(order.total)}
                      </p>
                    </div>
                    <span className="dash-badge">{order.state}</span>
                  </div>
                  <ol className="order-progress">
                    {activeStates.map((state, index) => (
                      <li
                        key={state}
                        className={index <= activeStates.indexOf(order.state) ? 'step-done' : ''}
                        aria-current={state === order.state ? 'step' : undefined}
                      >
                        {state}
                      </li>
                    ))}
                  </ol>
                  <p>{stateMessages[order.state]}</p>
                  <ul className="order-items">
                    {order.lines.map((line) => (
                      <li key={line.item.id}>
                        {line.quantity} × {line.item.name}
                      </li>
                    ))}
                  </ul>
                  {order.state === 'Placed' && (
                    <button className="btn btn-secondary" disabled={disabled} onClick={() => onCancel(order)}>
                      Cancel order
                    </button>
                  )}
                </article>
              ))}
            </div>
          </>
        )}
        {pastOrders.length > 0 && (
          <>
            <h3>Past orders</h3>
            <div className="dash-card past-orders">
              {pastOrders.map((order) => (
                <div className="past-order" key={order.id}>
                  <div>
                    <strong>{restaurantName(order.restaurantId)}</strong>
                    <p className="dash-muted">
                      {order.lines.map((line) => `${line.quantity} × ${line.item.name}`).join(', ')}
                    </p>
                    {order.rejectionReason && <p className="dash-muted">Reason: {order.rejectionReason}</p>}
                  </div>
                  <div className="past-order-meta">
                    <strong>{money(order.total)}</strong>
                    <span className={`status-tag ${order.state === 'Delivered' ? 'status-open' : 'status-closed'}`}>
                      {order.state}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </>
        )}
      </section>
    );
  }

  return (
    <div className="customer-view">
      <div className="customer-nav">
        <div className="customer-tabs">
          <button
            type="button"
            className={currentView === 'orders' ? '' : 'active-tab'}
            aria-pressed={currentView !== 'orders'}
            onClick={() => setView('browse')}
          >
            Restaurants
          </button>
          <button
            type="button"
            className={currentView === 'orders' ? 'active-tab' : ''}
            aria-pressed={currentView === 'orders'}
            onClick={() => setView('orders')}
          >
            Orders
            {activeOrders.length > 0 && <span className="tab-count">{activeOrders.length}</span>}
          </button>
        </div>
        {cartCount > 0 && currentView !== 'restaurant' && (
          <button type="button" className="cart-pill" onClick={() => openRestaurant(cart.restaurantId)}>
            View cart · {cartCount} item{cartCount === 1 ? '' : 's'} · {money(cartTotal)}
          </button>
        )}
      </div>
      {currentView === 'browse' && renderBrowse()}
      {currentView === 'restaurant' && renderRestaurant()}
      {currentView === 'orders' && renderOrders()}
    </div>
  );
}
