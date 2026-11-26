// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import LiveMap, { courierPosition, secondsLeft } from './LiveMap';

const leaflet = vi.hoisted(() => ({ maps: [], markers: [], lines: [] }));

vi.mock('leaflet', () => {
  function marker(position, options) {
    const entry = {
      position, options, removed: false, handlers: {},
      bindTooltip(content) { this.tooltip = content; return this; },
      addTo(layer) { this.layer = layer; return this; },
      on(name, handler) { this.handlers[name] = handler; return this; },
      off(name) { delete this.handlers[name]; return this; },
      setLatLng(value) { this.position = value; return this; },
      setZIndexOffset(value) { this.zIndex = value; return this; },
      setTooltipContent(value) { this.tooltip = value; return this; },
      setIcon(value) { this.options.icon = value; return this; },
      remove() { this.removed = true; },
    };
    leaflet.markers.push(entry);
    return entry;
  }
  function polyline(points, options) {
    const entry = {
      points, options, removed: false,
      addTo(layer) { this.layer = layer; return this; },
      setLatLngs(value) { this.points = value; return this; },
      remove() { this.removed = true; },
    };
    leaflet.lines.push(entry);
    return entry;
  }
  function map(container) {
    const entry = {
      container, handlers: {}, setViews: [], fitted: [], removed: false,
      setView(center, zoom) { this.setViews.push({ center, zoom }); return this; },
      fitBounds(bounds, options) { this.fitted.push({ bounds, options }); return this; },
      on(name, handler) { this.handlers[name] = handler; return this; },
      getContainer() { return this.container; },
      invalidateSize() {},
      remove() { this.removed = true; },
    };
    leaflet.maps.push(entry);
    return entry;
  }
  return {
    default: {
      map, marker, polyline,
      divIcon: (options) => options,
      control: { zoom: () => ({ addTo() {} }) },
      tileLayer: () => ({ addTo() {} }),
      layerGroup: () => ({ addTo() { return this; } }),
      latLngBounds: (points) => ({ points, pad() { return this; } }),
      DomEvent: { stopPropagation() {} },
    },
  };
});

const origin = { latitude: 45.5, longitude: -73.5 };
const shop = { id: 'shop', name: 'Cake & Co', address: '<Main>', status: 'OPEN', queueLength: 2, x: 1, y: 2 };

beforeEach(() => {
  leaflet.maps.length = 0;
  leaflet.markers.length = 0;
  leaflet.lines.length = 0;
  vi.stubGlobal('ResizeObserver', class {
    observe() {}
    disconnect() {}
  });
  vi.stubGlobal('requestAnimationFrame', () => 1);
  vi.stubGlobal('cancelAnimationFrame', () => {});
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('LiveMap characterization', () => {
  it('reuses restaurant markers when data changes and removes them when absent', () => {
    const onSelectRestaurant = vi.fn();
    const { rerender } = render(<LiveMap origin={origin} restaurants={[shop]} selectedId="shop"
      onSelectRestaurant={onSelectRestaurant} />);
    const marker = leaflet.markers[0];
    expect(leaflet.markers).toHaveLength(1);
    expect(marker.tooltip).toContain('Cake &amp; Co');
    expect(marker.tooltip).toContain('&lt;Main&gt;');
    expect(marker.options.icon.html).toContain('pin-selected');
    marker.handlers.click({});
    expect(onSelectRestaurant).toHaveBeenCalledWith('shop');

    rerender(<LiveMap origin={origin} restaurants={[{ ...shop, status: 'PAUSED', x: 3 }]} />);
    expect(leaflet.markers).toHaveLength(1);
    expect(marker.removed).toBe(false);
    expect(marker.tooltip).toContain('Paused');
    expect(marker.options.icon.html).not.toContain('pin-selected');
    expect(marker.position).not.toEqual([origin.latitude + 2 / 111.32, origin.longitude + 1 / (111.32 * Math.cos(origin.latitude * Math.PI / 180))]);

    rerender(<LiveMap origin={origin} restaurants={[]} />);
    expect(marker.removed).toBe(true);
    expect(screen.getByRole('status').textContent).toContain('The map is empty');
  });

  it('creates and removes courier routes with their markers', () => {
    const courier = { id: 7, x: 0, y: 0, targetX: 2, targetY: 3, status: 'CARRYING', orderId: 'order', mine: false,
      arrivesInMs: 5000, receivedAt: performance.now() };
    const { rerender } = render(<LiveMap origin={origin} couriers={[courier]} />);
    expect(leaflet.markers).toHaveLength(1);
    expect(leaflet.lines).toHaveLength(1);
    expect(leaflet.lines[0].options.className).toBe('route route-carrying');
    expect(leaflet.markers[0].tooltip).toContain('Courier C7');

    rerender(<LiveMap origin={origin} couriers={[]} />);
    expect(leaflet.markers[0].removed).toBe(true);
    expect(leaflet.lines[0].removed).toBe(true);
  });

  it('passes map picks through, updates cursor, and cleans up the map', () => {
    const onPick = vi.fn();
    const { rerender, unmount } = render(<LiveMap origin={origin} onPick={onPick} />);
    const map = leaflet.maps[0];
    expect(screen.getByText('The map is empty')).toBeTruthy();
    expect(map.container.style.cursor).toBe('crosshair');
    map.handlers.click({ latlng: { lat: 45.51, lng: -73.52 } });
    expect(onPick).toHaveBeenCalledWith(45.51, -73.52);
    fireEvent.click(screen.getByRole('button', { name: 'Fit map to all places' }));
    expect(map.setViews.at(-1).zoom).toBe(12);
    rerender(<LiveMap origin={origin} />);
    expect(map.container.style.cursor).toBe('');
    unmount();
    expect(map.removed).toBe(true);
  });

  it('preserves the courier interpolation and countdown rules', () => {
    const courier = { x: 0, y: 0, targetX: 4, targetY: 2, receivedAt: 1000, arrivesInMs: 4000 };
    expect(courierPosition(courier, 3000)).toEqual({ x: 2, y: 1 });
    expect(courierPosition(courier, 6000)).toEqual({ x: 4, y: 2 });
    expect(secondsLeft(courier, 3000)).toBe(2);
    expect(secondsLeft(courier, 6000)).toBe(0);
  });
});
