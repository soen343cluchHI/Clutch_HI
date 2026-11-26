import L from 'leaflet';
import { toLatLng } from './geo';
import { queueBand } from './queueBand';

const escape = (text) =>
  String(text).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

const storeIcon =
  '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linejoin="round"><path d="M4 10v10h16V10M3 10l2-6h14l2 6H3Z"/><path d="M10 20v-5h4v5"/></svg>';
const scooterIcon =
  '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><circle cx="6" cy="17" r="3"/><circle cx="18" cy="17" r="3"/><path d="M6 17h7l3-9h3M13 17l-2-6H7"/></svg>';
const homeIcon =
  '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linejoin="round"><path d="M4 11 12 4l8 7v9h-5v-6H9v6H4v-9Z"/></svg>';

export const courierLabels = { IDLE: 'Idle', ASSIGNED: 'Assigned', CARRYING: 'Carrying', OFFLINE: 'Off duty' };

// Same straight-line interpolation as the backend, so markers glide between polls.
export function courierPosition(courier, now = performance.now()) {
  if (!courier.arrivesInMs) return { x: courier.x, y: courier.y };
  const progress = Math.min(1, Math.max(0, (now - courier.receivedAt) / courier.arrivesInMs));
  return {
    x: courier.x + (courier.targetX - courier.x) * progress,
    y: courier.y + (courier.targetY - courier.y) * progress,
  };
}

export function secondsLeft(courier, now = performance.now()) {
  if (!courier?.arrivesInMs) return 0;
  return Math.max(0, Math.ceil((courier.arrivesInMs - (now - courier.receivedAt)) / 1000));
}

function restaurantHtml(r, selected, showLabels) {
  const paused = r.status !== 'OPEN';
  return `<div class="pin pin-store band-${queueBand(r)}${selected ? ' pin-selected' : ''}">
    ${storeIcon}<b class="pin-count">${paused ? 'II' : r.queueLength ?? 0}</b></div>
    ${showLabels ? `<span class="pin-label">${escape(r.name)}</span>` : ''}`;
}

function courierHtml(c) {
  return `<div class="pin pin-courier courier-${c.status.toLowerCase()}${c.mine ? ' pin-mine' : ''}">${scooterIcon}</div>
    <span class="pin-tag">C${c.id}</span>`;
}

function dropHtml(d) {
  return `<div class="pin pin-home">${homeIcon}</div><span class="pin-label">${escape(d.short || 'Drop-off')}</span>`;
}

const icon = (html, size = 40) =>
  L.divIcon({ className: 'pin-wrap', html, iconSize: [size, size], iconAnchor: [size / 2, size / 2] });

export function animateCouriers(markers, couriers, origin, now) {
  for (const courier of couriers) {
    const marker = markers.couriers.get(courier.id);
    if (!marker) continue;
    const position = courierPosition(courier, now);
    marker.setLatLng(toLatLng(origin, position.x, position.y));
    const route = markers.routes.get(courier.id);
    if (route) route.setLatLngs([toLatLng(origin, position.x, position.y), toLatLng(origin, courier.targetX, courier.targetY)]);
  }
}

export function syncRestaurants(store, layer, restaurants, origin, selectedId, showLabels, latest) {
  sync(store, layer, restaurants, (r) => ({
    position: toLatLng(origin, r.x, r.y),
    html: restaurantHtml(r, r.id === selectedId, showLabels),
    tooltip: `<b>${escape(r.name)}</b><br>${r.address ? `${escape(r.address)}<br>` : ''}${r.status === 'OPEN' ? `${r.queueLength ?? 0} in queue` : 'Paused'}`,
    onClick: () => latest.current.onSelectRestaurant?.(r.id),
    zIndex: r.id === selectedId ? 500 : 0,
  }));
}

export function syncCouriers(markers, layers, couriers, origin) {
  sync(markers.couriers, layers.couriers, couriers, (c) => {
    const position = courierPosition(c);
    return {
      position: toLatLng(origin, position.x, position.y),
      html: courierHtml(c),
      tooltip: `<b>Courier C${c.id}</b><br>${courierLabels[c.status]}${c.mine ? ' · you' : ''}`,
      zIndex: 1000,
    };
  });
  const routes = markers.routes;
  const moving = couriers.filter((c) => c.arrivesInMs > 0 && (c.orderId || c.mine));
  for (const [id, line] of routes) {
    if (!moving.some((c) => c.id === id)) {
      line.remove();
      routes.delete(id);
    }
  }
  for (const c of moving) {
    if (routes.has(c.id)) continue;
    const position = courierPosition(c);
    routes.set(
      c.id,
      L.polyline([toLatLng(origin, position.x, position.y), toLatLng(origin, c.targetX, c.targetY)], {
        className: `route route-${c.status.toLowerCase()}`,
        weight: 4,
        dashArray: '2 9',
        lineCap: 'round',
      }).addTo(layers.routes),
    );
  }
}

export function syncDrops(store, layer, drops, origin) {
  sync(store, layer, drops, (d) => ({
    position: toLatLng(origin, d.x, d.y),
    html: dropHtml(d),
    tooltip: `<b>Drop-off</b><br>${escape(d.label)}`,
    zIndex: 800,
  }));
}

function sync(store, layer, items, describe) {
  const ids = new Set(items.map((item) => item.id));
  for (const [id, entry] of store) {
    if (!ids.has(id)) {
      entry.marker.remove();
      store.delete(id);
    }
  }
  for (const item of items) {
    const next = describe(item);
    const entry = store.get(item.id);
    if (!entry) {
      const marker = L.marker(next.position, { icon: icon(next.html), zIndexOffset: next.zIndex || 0, keyboard: !!next.onClick })
        .bindTooltip(next.tooltip, { direction: 'top', offset: [0, -18] })
        .addTo(layer);
      if (next.onClick) marker.on('click', (event) => {
        L.DomEvent.stopPropagation(event);
        next.onClick();
      });
      store.set(item.id, { marker, html: next.html, onClick: next.onClick });
      continue;
    }
    entry.marker.setLatLng(next.position);
    entry.marker.setZIndexOffset(next.zIndex || 0);
    entry.marker.setTooltipContent(next.tooltip);
    if (entry.html !== next.html) {
      entry.marker.setIcon(icon(next.html));
      entry.html = next.html;
    }
    if (next.onClick && entry.onClick !== next.onClick) {
      entry.marker.off('click');
      entry.marker.on('click', (event) => {
        L.DomEvent.stopPropagation(event);
        next.onClick();
      });
      entry.onClick = next.onClick;
    }
  }
}
