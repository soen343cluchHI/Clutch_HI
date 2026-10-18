const supportedActions = new Set([
  'SESSION_INFO',
  'LOGOUT',
  'ROLE_MENU',
  'BROWSE_RESTAURANTS',
  'LIST_ORDERS',
  'PLACE_ORDER',
  'CANCEL_ORDER',
  'RESTAURANT_QUEUE',
  'ACCEPT_ORDER',
  'REJECT_ORDER',
  'START_PREPARING',
  'MARK_READY',
  'SET_RESTAURANT_STATUS',
  'DASHBOARD',
  'EVENT_LOG',
  'COMMAND_LOG',
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

export async function loadWorkspace(current, send) {
  const all = canExecute(current, 'BROWSE_RESTAURANTS') ? await send(current, 'BROWSE_RESTAURANTS') : [];
  const restaurants =
    current.user.role === 'RESTAURANT' ? all.filter((restaurant) => restaurant.ownerId === current.user.id) : all;
  const queues = {};
  if (current.user.role === 'RESTAURANT' && canExecute(current, 'RESTAURANT_QUEUE')) {
    for (const restaurant of restaurants) {
      queues[restaurant.id] = await send(current, 'RESTAURANT_QUEUE', {
        restaurantId: restaurant.id,
      });
    }
  }
  const orders = canExecute(current, 'LIST_ORDERS') ? await send(current, 'LIST_ORDERS') : [];
  return { restaurants, queues, orders };
}
