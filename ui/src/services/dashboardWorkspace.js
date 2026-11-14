import { montrealOrigin } from './geo';

const supportedActions = new Set([
  'SESSION_INFO',
  'LOGOUT',
  'ROLE_MENU',
  'BROWSE_RESTAURANTS',
  'LIST_ORDERS',
  'PLACE_ORDER',
  'PRICE_QUOTE',
  'CANCEL_ORDER',
  'RESTAURANT_QUEUE',
  'ACCEPT_ORDER',
  'REJECT_ORDER',
  'START_PREPARING',
  'MARK_READY',
  'SET_RESTAURANT_STATUS',
  'CREATE_RESTAURANT',
  'PUT_MENU_ITEM',
  'DELETE_MENU_ITEM',
  'DASHBOARD',
  'EVENT_LOG',
  'COMMAND_LOG',
  'VIEW_PROFILE',
  'UPDATE_PROFILE',
  'CITY_MAP',
  'COURIER_STATUS',
  'COURIER_OFFER',
  'GO_ON_DUTY',
  'GO_OFF_DUTY',
  'ACCEPT_DELIVERY_OFFER',
  'CONFIRM_PICKUP',
  'CONFIRM_DELIVERY',
  'REASSIGN_ORDER',
  'REFUND_ORDER',
  'VIEW_ORDER_SUMMARY',
  'LIST_LEDGER',
  'RESET_SYSTEM',
  'SET_RESTAURANT_LOCATION',
]);

export function workspaceActions(menu) {
  if (!Array.isArray(menu) || menu.some((action) => typeof action !== 'string')) {
    throw new Error('The server returned an invalid role menu. Retry connecting.');
  }
  return menu.filter((action) => supportedActions.has(action));
}

export function canExecute(current, action) {
  return Boolean(current?.actions?.includes(action));
}

export const emptyWorkspace = {
  restaurants: [],
  orders: [],
  queues: {},
  origin: montrealOrigin,
  couriers: [],
  ledger: [],
  courier: null,
  offer: null,
  loaded: false,
};

// Loads everything the signed-in role is allowed to see, in parallel.
export async function loadWorkspace(current, send) {
  const when = (action, fields, fallback) => (canExecute(current, action) ? send(current, action, fields) : Promise.resolve(fallback));
  const [all, orders, map, ledger, courier, offer] = await Promise.all([
    when('BROWSE_RESTAURANTS', {}, []),
    when('LIST_ORDERS', {}, []),
    when('CITY_MAP', {}, null),
    when('LIST_LEDGER', {}, []),
    when('COURIER_STATUS', {}, null),
    when('COURIER_OFFER', {}, null),
  ]);
  const receivedAt = performance.now();
  const restaurants = current.user.role === 'RESTAURANT' ? all.filter((r) => r.ownerId === current.user.id) : all;
  const queues = {};
  if (current.user.role === 'RESTAURANT' && canExecute(current, 'RESTAURANT_QUEUE')) {
    const snapshots = await Promise.all(restaurants.map((r) => send(current, 'RESTAURANT_QUEUE', { restaurantId: r.id })));
    restaurants.forEach((r, index) => (queues[r.id] = snapshots[index]));
  }
  return {
    restaurants,
    orders,
    queues,
    origin: map?.origin || montrealOrigin,
    couriers: (map?.couriers || []).map((c) => ({ ...c, receivedAt })),
    ledger,
    courier,
    offer,
    loaded: true,
  };
}
