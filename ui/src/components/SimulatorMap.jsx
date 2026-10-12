export function queueBand(restaurant) {
  if (restaurant.status === 'PAUSED' || restaurant.queueLength >= 6) return 'long';
  if (restaurant.queueLength >= 3) return 'building';
  return 'short';
}

export default function SimulatorMap({ restaurants, visibleRestaurants, selectedId, onSelect, showLabels, disabled }) {
  if (!restaurants.length) return <div className="map-empty">No restaurant coordinates are loaded.</div>;
  if (!visibleRestaurants.length) return <div className="map-empty">No restaurants match your search.</div>;
  const xs = restaurants.map(r => r.x);
  const ys = restaurants.map(r => r.y);
  const minX = Math.min(...xs), maxX = Math.max(...xs);
  const minY = Math.min(...ys), maxY = Math.max(...ys);
  const point = r => ({
    x: maxX === minX ? 450 : 130 + (r.x - minX) / (maxX - minX) * 640,
    y: maxY === minY ? 250 : 110 + (maxY - r.y) / (maxY - minY) * 280,
  });

  return (
    <svg className="simulator-map" viewBox="0 0 900 500" role="group" aria-label="Restaurant locations on the seeded city grid">
      <defs>
        <pattern id="city-grid" width="90" height="70" patternUnits="userSpaceOnUse">
          <rect width="90" height="70" fill="var(--map-land)" />
          <rect x="10" y="10" width="64" height="44" rx="7" fill="var(--map-block)" />
          <path d="M 0 65 H 90 M 85 0 V 70" stroke="var(--map-road)" strokeWidth="10" />
        </pattern>
      </defs>
      <rect width="900" height="500" rx="12" fill="url(#city-grid)" />
      <path d="M 0 280 C 170 230 310 350 460 280 S 760 200 900 260" fill="none" stroke="var(--map-water)" strokeWidth="28" opacity=".55" />
      <text x="22" y="30" className="map-caption">SEEDED CITY GRID</text>
      {visibleRestaurants.map(r => {
        const { x, y } = point(r);
        const selected = r.id === selectedId;
        const band = queueBand(r);
        return (
          <g key={r.id} transform={`translate(${x},${y})`} className={`map-marker marker-${band}`}
            role="button" tabIndex={disabled ? -1 : 0} aria-disabled={Boolean(disabled)} aria-pressed={selected}
            aria-label={`${r.name}, ${r.status}, ${r.queueLength ?? 0} queued orders`}
            onClick={() => { if (!disabled) onSelect(r.id); }}
            onKeyDown={event => {
              if (!disabled && (event.key === 'Enter' || event.key === ' ')) {
                event.preventDefault(); onSelect(r.id);
              }
            }}>
            <title>{`${r.name} · ${r.status} · ${r.queueLength ?? 0} queued · (${r.x}, ${r.y})`}</title>
            {selected && <circle r="31" className="marker-selection" />}
            <circle r="22" className="marker-body" />
            <path d="M -9 5 V -4 H 9 V 5 Z M -12 -4 L -8 -10 H 8 L 12 -4 Z" fill="none" stroke="white" strokeWidth="2" />
            <circle cx="17" cy="-17" r="11" className="marker-count" />
            <text x="17" y="-13" textAnchor="middle" className="marker-count-text">{r.queueLength ?? 0}</text>
            {showLabels && <g className="marker-label">
              <rect x="-112" y="34" width="224" height="30" rx="8" />
              <text y="54" textAnchor="middle">{r.name.length > 22 ? r.name.slice(0, 21) + '…' : r.name} · {r.queueLength ?? 0} queued</text>
            </g>}
          </g>
        );
      })}
    </svg>
  );
}
