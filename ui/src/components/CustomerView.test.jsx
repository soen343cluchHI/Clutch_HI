// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import CustomerView from './CustomerView';

vi.mock('./LiveMap', () => ({
  default: () => <div data-testid="map" />,
  secondsLeft: () => 0,
}));

vi.mock('./AddressPicker', () => ({
  default: ({ value, onChange }) => (
    <button type="button" onClick={() => onChange({ label: 'Home', x: 1.25, y: -0.5 })}>
      {value ? value.label : 'Choose test address'}
    </button>
  ),
}));

const quote = {
  subtotal: '8.50',
  deliveryFee: '3.00',
  serviceFee: '1.00',
  smallOrderSurcharge: '2.00',
  smallOrderThreshold: '15.00',
  total: '14.50',
};

function restaurant(status = 'OPEN') {
  return {
    id: 'shop', name: 'Cake Shop', status, address: 'Main Street', x: 0, y: 0, queueLength: 0,
    menu: [
      { id: 'cake', name: 'Cake', price: '8.50', status: 'AVAILABLE' },
      { id: 'pie', name: 'Pie', price: '5.00', status: 'SOLD_OUT' },
    ],
  };
}

function setup(status = 'OPEN') {
  const props = {
    tab: 'Home',
    setTab: vi.fn(),
    session: { user: { username: 'buyer@example.com' } },
    restaurants: [restaurant(status)],
    orders: [],
    couriers: [],
    origin: { latitude: 45.5, longitude: -73.5 },
    ledger: [],
    disabled: false,
    money: (value) => `$${Number(value).toFixed(2)}`,
    act: vi.fn().mockResolvedValue({ id: 'placed-order' }),
    ask: vi.fn().mockResolvedValue(quote),
  };
  render(<CustomerView {...props} />);
  fireEvent.click(screen.getByRole('button', { name: 'View menu' + (status === 'OPEN' ? '' : ' (paused)') }));
  return props;
}

beforeEach(() => localStorage.clear());
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('CustomerView checkout characterization', () => {
  it('keeps checkout blocked until an item and address are selected, then submits the same fields as the quote', async () => {
    const props = setup();
    expect(screen.getByText('Add at least one item.')).toBeTruthy();
    expect(screen.getByRole('button', { name: /Place order/ }).disabled).toBe(true);

    fireEvent.click(screen.getByRole('button', { name: 'Add Cake' }));
    expect(screen.getByText('Choose a delivery address.')).toBeTruthy();
    await waitFor(() => expect(props.ask).toHaveBeenCalledWith('PRICE_QUOTE', {
      restaurantId: 'shop', 'item.cake': 1,
    }));
    await screen.findByText('$14.50');
    expect(screen.getByRole('button', { name: /Place order/ }).disabled).toBe(true);

    fireEvent.click(screen.getByRole('button', { name: 'Choose test address' }));
    const place = screen.getByRole('button', { name: 'Place order · $14.50' });
    expect(place.disabled).toBe(false);
    fireEvent.click(place);
    await waitFor(() => expect(props.act).toHaveBeenCalledWith('PLACE_ORDER', {
      restaurantId: 'shop', address: 'Home', addressX: 1.25, addressY: -0.5, 'item.cake': 1,
    }));
    await waitFor(() => expect(props.setTab).toHaveBeenCalledWith('Orders'));
    expect(screen.getByRole('button', { name: 'View menu' })).toBeTruthy();
  });

  it('clearing the cart restores the empty-cart blocker', async () => {
    const props = setup();
    fireEvent.click(screen.getByRole('button', { name: 'Add Cake' }));
    await waitFor(() => expect(props.ask).toHaveBeenCalledTimes(1));
    fireEvent.click(screen.getByRole('button', { name: 'Clear cart' }));
    expect(screen.getByText('Your cart is empty.')).toBeTruthy();
    expect(screen.getByText('Add at least one item.')).toBeTruthy();
    expect(screen.getByRole('button', { name: /Place order/ }).disabled).toBe(true);
    expect(props.act).not.toHaveBeenCalled();
  });

  it('does not let the customer add an item from a paused restaurant', () => {
    const props = setup('PAUSED');
    expect(screen.getByRole('button', { name: 'Add Cake' }).disabled).toBe(true);
    expect(screen.getByRole('button', { name: 'Add Pie' }).disabled).toBe(true);
    expect(screen.getByRole('button', { name: /Place order/ }).disabled).toBe(true);
    expect(props.ask).not.toHaveBeenCalled();
  });
});
