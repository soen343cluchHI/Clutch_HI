import AddressPicker from './AddressPicker';
import LiveMap from './LiveMap';
import { distanceKm } from '../services/geo';

export default function CheckoutPanel({
  restaurant, cart, cartLines, cartCount, address, onAddressChange, quote, promoCode,
  onPromoChange, promoError, disabled, blocker, money, origin, onPick, onPlace, onClear,
}) {
  return (
    <aside className="panel checkout">
      <h3>Your order{restaurant ? ` · ${restaurant.name}` : ''}</h3>
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
      <AddressPicker origin={origin} value={address} onChange={onAddressChange} disabled={disabled} />
      <LiveMap
        origin={origin}
        className="map-checkout"
        restaurants={[restaurant]}
        drops={address ? [{ id: 'home', ...address, short: 'You' }] : []}
        onPick={disabled ? undefined : onPick}
        showLabels={false}
        legend={false}
      />
      {address && <p className="hint">{distanceKm({ x: restaurant.x, y: restaurant.y }, address).toFixed(1)} km from {restaurant.name} (straight line)</p>}
      <label className="promo-field">
        <span>Promo code</span>
        <input
          type="text"
          value={promoCode}
          onChange={(event) => onPromoChange(event.target.value)}
          placeholder="Enter a code"
          maxLength={32}
          disabled={disabled}
        />
      </label>
      {promoError && <p className="hint reason" role="alert">{promoError}</p>}
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
          {quote.promoCode && (
            <div className="line">
              <span>Promo {quote.promoCode}</span>
              <span>−{money(quote.promoDiscount)}</span>
            </div>
          )}
          <div className="line total">
            <span>Total</span>
            <span>{money(quote.total)}</span>
          </div>
        </div>
      )}
      <button className="f-btn primary block" disabled={disabled || Boolean(blocker)} onClick={onPlace}>
        Place order{quote && !blocker ? ` · ${money(quote.total)}` : ''}
      </button>
      {blocker && <p className="hint reason">{blocker}</p>}
      {cartCount > 0 && (
        <button type="button" className="link-button" disabled={disabled} onClick={onClear}>
          Clear cart
        </button>
      )}
    </aside>
  );
}
