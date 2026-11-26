import { useEffect, useRef } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { montrealOrigin, toLatLng } from '../services/geo';
import { bandRanges } from '../services/queueBand';
import { animateCouriers, courierLabels, syncCouriers, syncDrops, syncRestaurants } from '../services/mapMarkers';

export { courierPosition, secondsLeft } from '../services/mapMarkers';

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
      animateCouriers(markers.current, list, o, performance.now());
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
    syncRestaurants(markers.current.restaurants, layers.current.restaurants, restaurants, center, selectedId, showLabels, latest);
  }, [restaurants, selectedId, showLabels, center.latitude, center.longitude]);

  useEffect(() => {
    if (!map.current) return;
    syncCouriers(markers.current, layers.current, couriers, center);
  }, [couriers, center.latitude, center.longitude]);

  useEffect(() => {
    if (!map.current) return;
    syncDrops(markers.current.drops, layers.current.drops, drops, center);
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
