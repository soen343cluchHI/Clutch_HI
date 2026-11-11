import LiveMap, { secondsLeft } from './LiveMap';
import { stateLabel } from './OrderProgress';
import { LedgerTable } from './CustomerView';
import { bandLabels, queueBand } from '../services/queueBand';

const courierStates = { IDLE: 'Idle', ASSIGNED: 'Assigned', CARRYING: 'Carrying' };

export default function OperatorView({ tab, restaurants, orders, couriers, origin, ledger, disabled, money, act }) {
  const restaurantName = (id) => restaurants.find((r) => r.id === id)?.name || 'Restaurant';
  const courierFor = (orderId) => couriers.find((c) => c.orderId === orderId);
  const ready = orders.filter((o) => o.state === 'Ready');
  const stalled = ready.filter((o) => courierFor(o.id));
  const waiting = ready.filter((o) => !courierFor(o.id));
  const active = orders.filter((o) => ['Placed', 'Accepted', 'Preparing', 'Ready', 'PickedUp'].includes(o.state));
  const refundable = new Set(orders.filter((o) => o.state === 'Delivered').map((o) => o.id));
  const attention = stalled.length + waiting.length;

  async function reset() {
    if (window.confirm('Reset the whole simulation to its seeded state? All orders, ledger entries and courier positions are cleared.'))
      await act('RESET_SYSTEM');
  }

  const refundButton = (orderId) =>
    refundable.has(orderId) && (
      <button
        className="f-btn ghost sm"
        disabled={disabled}
        onClick={() => window.confirm('Refund this delivered order? A separate refund entry is added to the ledger.') && act('REFUND_ORDER', { orderId })}
      >
        Refund
      </button>
    );

  function orderActions(order) {
    const courier = courierFor(order.id);
    return (
      <>
        {['Accepted', 'Preparing'].includes(order.state) && (
          <button className="f-btn ghost sm" disabled={disabled} onClick={() => act('CANCEL_ORDER', { orderId: order.id })}>
            Cancel
          </button>
        )}
        {order.state === 'Ready' && (
          <button
            className="f-btn primary sm"
            disabled={disabled || !courier}
            title={courier ? 'Move this order to the nearest other idle courier' : 'No courier holds this order yet; it is waiting in the dispatch queue'}
            onClick={() => act('REASSIGN_ORDER', { orderId: order.id })}
          >
            Reassign
          </button>
        )}
        {refundButton(order.id)}
      </>
    );
  }

  function renderLive() {
    return (
      <>
        <div className="page-head">
          <div>
            <h1>{attention ? `${attention} order${attention === 1 ? '' : 's'} need${attention === 1 ? 's' : ''} you` : 'Everything’s running'}</h1>
            <p className="muted">
              {restaurants.length} restaurants · {couriers.length} courier{couriers.length === 1 ? '' : 's'} on duty · {active.length} active orders
            </p>
          </div>
          <button className="f-btn danger" disabled={disabled} onClick={reset}>
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round">
              <path d="M3 12a9 9 0 1 0 3-6.7L3 8" />
              <path d="M3 3v5h5" />
            </svg>
            Reset simulation
          </button>
        </div>
        <div className="operator-grid">
          <section className="panel">
            <LiveMap
              origin={origin}
              className="map-big"
              restaurants={restaurants}
              couriers={couriers}
              drops={active.filter((o) => o.deliveryAddress).map((o) => ({ id: o.id, ...o.deliveryAddress, short: `#${o.id.slice(0, 4)}` }))}
            />
          </section>
          <div className="stack">
            <section className={`panel${attention ? ' attention' : ''}`}>
              <span className="eyebrow">Needs attention</span>
              {!attention && <p className="muted">No ready orders are waiting on a courier.</p>}
              {stalled.map((order) => {
                const courier = courierFor(order.id);
                return (
                  <div className="attention-row" key={order.id}>
                    <div className="grow">
                      <strong className="mono">#{order.id.slice(0, 8)}</strong>
                      <span className="muted">
                        {restaurantName(order.restaurantId)} · courier C{courier.id}{' '}
                        {courier.offerAccepted ? 'accepted but has not picked up' : 'has not accepted yet'}
                        {secondsLeft(courier) ? ` · ${secondsLeft(courier)}s away` : ''}
                      </span>
                    </div>
                    <button className="f-btn primary sm" disabled={disabled} onClick={() => act('REASSIGN_ORDER', { orderId: order.id })}>
                      Reassign
                    </button>
                  </div>
                );
              })}
              {waiting.map((order) => (
                <div className="attention-row" key={order.id}>
                  <div className="grow">
                    <strong className="mono">#{order.id.slice(0, 8)}</strong>
                    <span className="muted">{restaurantName(order.restaurantId)} · no idle courier, queued for dispatch</span>
                  </div>
                </div>
              ))}
            </section>
            <section className="panel">
              <h3>Restaurants</h3>
              {!restaurants.length && <p className="empty-note">No restaurants yet.</p>}
              {restaurants.map((r) => {
                const band = queueBand(r);
                return (
                  <div className="list-row" key={r.id}>
                    <i className={`dot band-${band}`} title={bandLabels[band]} />
                    <div className="grow">
                      <strong>{r.name}</strong>
                      {r.address && <span className="muted">{r.address}</span>}
                    </div>
                    <span className="muted">{r.status === 'OPEN' ? `${r.queueLength} queued` : 'Paused'}</span>
                    <button
                      className="f-btn ghost sm"
                      disabled={disabled}
                      onClick={() => act('SET_RESTAURANT_STATUS', { restaurantId: r.id, status: r.status === 'OPEN' ? 'PAUSED' : 'OPEN' })}
                    >
                      {r.status === 'OPEN' ? 'Pause' : 'Reopen'}
                    </button>
                  </div>
                );
              })}
            </section>
            <section className="panel">
              <h3>Recently delivered</h3>
              {!orders.some((o) => ['Delivered', 'Refunded'].includes(o.state)) && <p className="empty-note">Nothing delivered yet.</p>}
              {[...orders]
                .reverse()
                .filter((o) => ['Delivered', 'Refunded'].includes(o.state))
                .slice(0, 5)
                .map((o) => (
                  <div className="list-row" key={o.id}>
                    <strong className="mono">#{o.id.slice(0, 8)}</strong>
                    <span className="grow muted">{restaurantName(o.restaurantId)}</span>
                    <span className={o.state === 'Refunded' ? 'negative' : ''}>{money(o.total)}</span>
                    {refundButton(o.id) || <span className="state-pill state-refunded">Refunded</span>}
                  </div>
                ))}
            </section>
          </div>
        </div>
      </>
    );
  }

  function renderOrders() {
    return (
      <section className="panel">
        <div className="row between">
          <h2>All orders</h2>
          <span className="muted">{orders.length} orders</span>
        </div>
        {!orders.length ? (
          <p className="empty-note">No orders yet.</p>
        ) : (
          <div className="table-wrap">
            <table className="f-table">
              <thead>
                <tr>
                  <th>Order</th>
                  <th>Restaurant</th>
                  <th>Items</th>
                  <th>Deliver to</th>
                  <th>Courier</th>
                  <th>State</th>
                  <th className="num">Total</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {[...orders].reverse().map((order) => {
                  const courier = courierFor(order.id);
                  return (
                    <tr key={order.id}>
                      <td className="mono">{order.id.slice(0, 8)}</td>
                      <td>{restaurantName(order.restaurantId)}</td>
                      <td>{order.lines.map((l) => `${l.quantity} × ${l.item.name}`).join(', ')}</td>
                      <td>{order.deliveryAddress?.label}</td>
                      <td>{courier ? `C${courier.id}` : '—'}</td>
                      <td>
                        <span className={`state-pill state-${order.state.toLowerCase()}`}>{stateLabel(order.state)}</span>
                        {order.rejectionReason && <span className="muted block">{order.rejectionReason}</span>}
                      </td>
                      <td className="num">{money(order.total)}</td>
                      <td className="actions">{orderActions(order)}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>
    );
  }

  function renderCouriers() {
    return (
      <div className="split">
        <section className="panel">
          <h2>Couriers on duty</h2>
          {!couriers.length && <p className="empty-note">No courier is on duty. Ready orders wait in the dispatch queue until one starts a shift.</p>}
          {couriers.map((c) => (
            <div className="list-row" key={c.id}>
              <i className={`ring courier-${c.status.toLowerCase()}`} />
              <strong>C{c.id}</strong>
              <span className="grow">{courierStates[c.status]}</span>
              <span className="muted mono">{c.orderId ? `#${c.orderId.slice(0, 8)}` : '—'}</span>
              <span className="muted">{secondsLeft(c) ? `${secondsLeft(c)}s to stop` : 'stopped'}</span>
            </div>
          ))}
        </section>
        <section className="panel">
          <LiveMap origin={origin} className="map-side" restaurants={restaurants} couriers={couriers} showLabels={false} />
        </section>
      </div>
    );
  }

  return (
    <>
      {tab === 'Live' && renderLive()}
      {tab === 'Orders' && renderOrders()}
      {tab === 'Couriers' && renderCouriers()}
      {tab === 'Ledger' && (
        <LedgerTable entries={ledger} restaurants={restaurants} money={money} title="Billing ledger" actions={(entry) => entry.type === 'CHARGE' && refundButton(entry.orderId)} />
      )}
    </>
  );
}
