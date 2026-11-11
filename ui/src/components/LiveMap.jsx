import { useEffect, useRef } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { montrealOrigin, toLatLng } from '../services/geo';
import { bandRanges, queueBand } from '../services/queueBand';

const escape = (text) =>
  String(text).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

const storeIcon =
  '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linejoin="round"><path d="M4 10v10h16V10M3 10l2-6h14l2 6H3Z"/><path d="M10 20v-5h4v5"/></svg>';
const scooterIcon =
  '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><circle cx="6" cy="17" r="3"/><circle cx="18" cy="17" r="3"/><path d="M6 17h7l3-9h3M13 17l-2-6H7"/></svg>';
const homeIcon =
  '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linejoin="round"><path d="M4 11 12 4l8 7v9h-5v-6H9v6H4v-9Z"/></svg>';

const courierLabels = { IDLE: 'Idle', ASSIGNED: 'Assigned', CARRYING: 'Carrying', OFFLINE: 'Off duty' };

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

export default function LiveMap({
  origin,
  restaurants = [],
  couriers = [],
  drops = [],
  selectedId = '',
  onSelectRestaurant,
  onPick,
  showLabels = true,
  className = '',
  emptyText = 'No restaurants or on-duty couriers yet. They appear here as soon as a store opens or a courier starts a shift.',
  legend = true,
}) {
  const container = useRef(null);
  const map = useRef(null);
  const layers = useRef(null);
  const markers = useRef({ restaurants: new Map(), couriers: new Map(), drops: new Map(), routes: new Map() });
  const fitted = useRef('');
  const latest = useRef({});
  const center = origin || montrealOrigin;
  latest.current = { origin: center, couriers, onPick, onSelectRestaurant };

  useEffect(() => {
    const instance = L.map(container.current, { zoomControl: false, attributionControl: true }).setView(
      [center.latitude, center.longitude],
      13,
    );
    L.control.zoom({ position: 'topright' }).addTo(instance);
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    }).addTo(instance);
    layers.current = {
      routes: L.layerGroup().addTo(instance),
      drops: L.layerGroup().addTo(instance),
      restaurants: L.layerGroup().addTo(instance),
      couriers: L.layerGroup().addTo(instance),
    };
    instance.on('click', (event) => latest.current.onPick?.(event.latlng.lat, event.latlng.lng));
    map.current = instance;
    const resize = new ResizeObserver(() => instance.invalidateSize());
    resize.observe(container.current);

    let frame;
    const animate = () => {
      const { origin: o, couriers: list } = latest.current;
      const now = performance.now();
      for (const courier of list) {
        const marker = markers.current.couriers.get(courier.id);
        if (!marker) continue;
        const position = courierPosition(courier, now);
        marker.setLatLng(toLatLng(o, position.x, position.y));
        const route = markers.current.routes.get(courier.id);
        if (route) route.setLatLngs([toLatLng(o, position.x, position.y), toLatLng(o, courier.targetX, courier.targetY)]);
      }
      frame = requestAnimationFrame(animate);
    };
    frame = requestAnimationFrame(animate);
    return () => {
      cancelAnimationFrame(frame);
      resize.disconnect();
      instance.remove();
      map.current = null;
    };
  }, []);

  useEffect(() => {
    if (map.current) map.current.getContainer().style.cursor = onPick ? 'crosshair' : '';
  }, [onPick]);

  // Restaurants: update markers in place so the map does not flicker on every poll.
  useEffect(() => {
    if (!map.current) return;
    sync(markers.current.restaurants, layers.current.restaurants, restaurants, (r) => ({
      position: toLatLng(center, r.x, r.y),
      html: restaurantHtml(r, r.id === selectedId, showLabels),
      tooltip: `<b>${escape(r.name)}</b><br>${r.address ? `${escape(r.address)}<br>` : ''}${r.status === 'OPEN' ? `${r.queueLength ?? 0} in queue` : 'Paused'}`,
      onClick: () => latest.current.onSelectRestaurant?.(r.id),
      zIndex: r.id === selectedId ? 500 : 0,
    }));
  }, [restaurants, selectedId, showLabels, center.latitude, center.longitude]);

  useEffect(() => {
    if (!map.current) return;
    sync(markers.current.couriers, layers.current.couriers, couriers, (c) => {
      const position = courierPosition(c);
      return {
        position: toLatLng(center, position.x, position.y),
        html: courierHtml(c),
        tooltip: `<b>Courier C${c.id}</b><br>${courierLabels[c.status]}${c.mine ? ' · you' : ''}`,
        zIndex: 1000,
      };
    });
    const routes = markers.current.routes;
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
        L.polyline([toLatLng(center, position.x, position.y), toLatLng(center, c.targetX, c.targetY)], {
          className: `route route-${c.status.toLowerCase()}`,
          weight: 4,
          dashArray: '2 9',
          lineCap: 'round',
        }).addTo(layers.current.routes),
      );
    }
  }, [couriers, center.latitude, center.longitude]);

  useEffect(() => {
    if (!map.current) return;
    sync(markers.current.drops, layers.current.drops, drops, (d) => ({
      position: toLatLng(center, d.x, d.y),
      html: dropHtml(d),
      tooltip: `<b>Drop-off</b><br>${escape(d.label)}`,
      zIndex: 800,
    }));
  }, [drops, center.latitude, center.longitude]);

  // R-DASH: fit the view to the dataset bounds whenever the set of places changes (not on every move).
  const placesKey = [...restaurants.map((r) => r.id), ...drops.map((d) => d.id), couriers.length].join('|');
  useEffect(() => {
    if (!map.current || fitted.current === placesKey) return;
    fitted.current = placesKey;
    fit();
  }, [placesKey]);

  function fit() {
    const points = [
      ...restaurants.map((r) => toLatLng(center, r.x, r.y)),
      ...couriers.map((c) => toLatLng(center, c.x, c.y)),
      ...drops.map((d) => toLatLng(center, d.x, d.y)),
    ];
    if (!points.length) map.current.setView([center.latitude, center.longitude], 12);
    else if (points.length === 1) map.current.setView(points[0], 15);
    else map.current.fitBounds(L.latLngBounds(points).pad(0.25), { maxZoom: 16 });
  }

  const empty = !restaurants.length && !couriers.length && !drops.length;

  return (
    <div className={`live-map ${className}`}>
      <div ref={container} className="live-map-canvas" role="region" aria-label="City map of Montreal" />
      {empty && (
        <div className="map-empty-overlay" role="status">
          <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" strokeWidth="2" strokeLinejoin="round">
            <path d="M9 4 3 6v14l6-2 6 2 6-2V4l-6 2-6-2Z" />
            <path d="M9 4v14M15 6v14" />
          </svg>
          <strong>The map is empty</strong>
          <span>{emptyText}</span>
        </div>
      )}
      <button type="button" className="map-fit" onClick={() => map.current && fit()} aria-label="Fit map to all places">
        Fit
      </button>
      {onPick && <span className="map-hint">Tap the map to set the delivery point</span>}
      {legend && (
        <div className="map-legend" aria-label="Map legend">
          {['short', 'building', 'long'].map((band) => (
            <span key={band} title={bandRanges[band]}>
              <i className={`dot band-${band}`} />
              {bandRanges[band]}
            </span>
          ))}
          <span className="legend-sep" />
          {['idle', 'assigned', 'carrying'].map((state) => (
            <span key={state}>
              <i className={`ring courier-${state}`} />
              {courierLabels[state.toUpperCase()]}
            </span>
          ))}
        </div>
      )}
    </div>
  );
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
