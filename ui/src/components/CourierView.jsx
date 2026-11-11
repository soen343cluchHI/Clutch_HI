import LiveMap, { secondsLeft } from './LiveMap';
import { stateLabel } from './OrderProgress';
import { LedgerTable } from './CustomerView';
import { distanceKm } from '../services/geo';

const statusText = {
  OFFLINE: 'You’re off duty',
  IDLE: 'On duty · waiting for an order',
  ASSIGNED: 'You have a delivery',
  CARRYING: 'Delivering an order',
};

export default function CourierView({ tab, courier, offer: polledOffer, restaurants, couriers, orders, origin, ledger, disabled, money, act }) {
  if (!courier) return <p className="empty-note">Loading your courier profile…</p>;
  // Only trust the offer if it is the order this courier holds right now (it can change between refreshes).
  const offer = polledOffer && polledOffer.id === courier.orderId ? polledOffer : null;
  const me = couriers.find((c) => c.mine) || { ...courier, arrivesInMs: 0 };
  const restaurant = offer && restaurants.find((r) => r.id === offer.restaurantId);
  const away = secondsLeft(me);
  const accepted = courier.offerAccepted;

  // R-DASH-05: each handoff button explains why it is disabled.
  const acceptBlocker = courier.status !== 'ASSIGNED' ? 'No offer to accept.' : accepted ? 'Offer already accepted.' : '';
  const pickupBlocker =
    courier.status !== 'ASSIGNED'
      ? courier.status === 'CARRYING'
        ? 'Already picked up.'
        : 'No order assigned to you.'
      : !accepted
        ? 'Accept the offer first.'
        : offer?.state !== 'Ready'
          ? 'The order is not ready yet.'
          : away
            ? `Arriving at the restaurant in ${away}s.`
            : '';
  const deliveryBlocker =
    courier.status !== 'CARRYING' ? 'Pick up the order first.' : away ? `Arriving at the customer in ${away}s.` : '';
  const offDutyBlocker = courier.status === 'IDLE' ? '' : 'Finish your delivery before going off duty.';

  function renderShift() {
    const step = courier.status === 'CARRYING' ? 3 : accepted ? 2 : courier.status === 'ASSIGNED' ? 1 : 0;
    return (
      <>
        <div className="hero-row">
          <div className="hero-card">
            <h1>{statusText[courier.status]}</h1>
            <p>
              Courier C{courier.id}
              {courier.status === 'OFFLINE'
                ? ' · go on duty to receive the nearest ready order.'
                : courier.status === 'IDLE'
                  ? ' · the dispatcher sends you the nearest ready order automatically.'
                  : away
                    ? ` · next stop in ${away}s`
                    : ' · you are at your stop'}
            </p>
            <div className="row">
              {courier.status === 'OFFLINE' ? (
                <button className="f-btn light" disabled={disabled} onClick={() => act('GO_ON_DUTY')}>
                  Go on duty
                </button>
              ) : (
                <button className="f-btn on-dark" disabled={disabled || Boolean(offDutyBlocker)} title={offDutyBlocker} onClick={() => act('GO_OFF_DUTY')}>
                  Go off duty
                </button>
              )}
              {offDutyBlocker && courier.status !== 'OFFLINE' && <span className="hero-note">{offDutyBlocker}</span>}
            </div>
          </div>
          <div className="next-card">
            <span className="eyebrow">Current delivery</span>
            {offer ? (
              <>
                <h2>{restaurant?.name || 'Restaurant'}</h2>
                {restaurant?.address && <p className="address-line">Pick up at {restaurant.address}</p>}
                <p className="mono">#{offer.id.slice(0, 8)} · {offer.lines.map((l) => `${l.quantity} × ${l.item.name}`).join(', ')}</p>
                <p className="muted">Deliver to {offer.deliveryAddress?.label}</p>
                {restaurant && offer.deliveryAddress && (
                  <p className="muted">{distanceKm(restaurant, offer.deliveryAddress).toFixed(1)} km from pickup to drop-off</p>
                )}
              </>
            ) : (
              <p className="muted">{courier.status === 'OFFLINE' ? 'Go on duty to receive offers.' : 'No delivery yet. Offers arrive automatically.'}</p>
            )}
          </div>
        </div>

        <div className="split">
          <section className="panel">
            <h2>Handoff</h2>
            <ol className="handoff">
              <li className={step > 1 ? 'done' : step === 1 ? 'now' : ''}>
                <div className="grow">
                  <strong>Accept the offer</strong>
                  <span className="muted">{acceptBlocker || 'You were picked as the nearest idle courier.'}</span>
                </div>
                <button className="f-btn primary sm" disabled={disabled || Boolean(acceptBlocker)} onClick={() => act('ACCEPT_DELIVERY_OFFER', { orderId: offer.id })}>
                  Accept
                </button>
              </li>
              <li className={step > 2 ? 'done' : step === 2 ? 'now' : ''}>
                <div className="grow">
                  <strong>Confirm pickup</strong>
                  <span className="muted">{pickupBlocker || 'You are at the restaurant.'}</span>
                </div>
                <button className="f-btn primary sm" disabled={disabled || Boolean(pickupBlocker)} onClick={() => act('CONFIRM_PICKUP', { orderId: offer.id })}>
                  Confirm pickup
                </button>
              </li>
              <li className={step === 3 ? 'now' : ''}>
                <div className="grow">
                  <strong>Confirm delivery</strong>
                  <span className="muted">{deliveryBlocker || 'You are at the customer’s address.'}</span>
                </div>
                <button className="f-btn primary sm" disabled={disabled || Boolean(deliveryBlocker)} onClick={() => act('CONFIRM_DELIVERY', { orderId: offer.id })}>
                  Confirm delivery
                </button>
              </li>
            </ol>
          </section>
          <section className="panel">
            <h2>Your route</h2>
            <LiveMap
              origin={origin}
              className="map-side"
              restaurants={restaurant ? [restaurant] : restaurants}
              couriers={[me]}
              drops={offer?.deliveryAddress ? [{ id: offer.id, ...offer.deliveryAddress, short: 'Drop-off' }] : []}
              showLabels={!restaurant}
            />
          </section>
        </div>
      </>
    );
  }

  function renderHistory() {
    return (
      <>
        <section className="panel">
          <h2>Deliveries you carried</h2>
          {!orders.length && <p className="empty-note">Orders appear here once you pick them up.</p>}
          {[...orders].reverse().map((order) => (
            <div className="list-row" key={order.id}>
              <strong className="mono">#{order.id.slice(0, 8)}</strong>
              <span className="grow muted">
                {restaurants.find((r) => r.id === order.restaurantId)?.name} → {order.deliveryAddress?.label}
              </span>
              <span className={`state-pill state-${order.state.toLowerCase()}`}>{stateLabel(order.state)}</span>
            </div>
          ))}
        </section>
        <LedgerTable entries={ledger} restaurants={restaurants} money={money} title="Ledger for your deliveries" />
      </>
    );
  }

  return tab === 'History' ? renderHistory() : renderShift();
}
