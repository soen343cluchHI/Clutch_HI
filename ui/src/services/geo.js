// Backend coordinates are kilometres east (x) and north (y) of the city origin sent with CITY_MAP.
const KM_PER_DEGREE = 111.32;

const kmPerLongitudeDegree = (origin) => KM_PER_DEGREE * Math.cos((origin.latitude * Math.PI) / 180);

export const montrealOrigin = { latitude: 45.5017, longitude: -73.5673 };

export function toLatLng(origin, x, y) {
  return [origin.latitude + y / KM_PER_DEGREE, origin.longitude + x / kmPerLongitudeDegree(origin)];
}

export function toKm(origin, latitude, longitude) {
  return {
    x: Number(((longitude - origin.longitude) * kmPerLongitudeDegree(origin)).toFixed(4)),
    y: Number(((latitude - origin.latitude) * KM_PER_DEGREE).toFixed(4)),
  };
}

export const distanceKm = (a, b) => Math.hypot(a.x - b.x, a.y - b.y);

// Quick picks keep the demo working even if the address search service is slow.
export const presetAddresses = [
  { label: 'Concordia University, 1455 De Maisonneuve Blvd W', latitude: 45.4972, longitude: -73.579 },
  { label: 'McGill University, 845 Sherbrooke St W', latitude: 45.5048, longitude: -73.5772 },
  { label: 'Old Port, 333 de la Commune St W', latitude: 45.5013, longitude: -73.5545 },
  { label: 'Jean-Talon Market, 7070 Henri-Julien Ave', latitude: 45.5363, longitude: -73.6147 },
  { label: 'Olympic Stadium, 4545 Pierre-De Coubertin Ave', latitude: 45.5579, longitude: -73.5515 },
];

// OpenStreetMap Nominatim, limited to the island of Montreal. Called on submit only (1 request/second policy).
const NOMINATIM = 'https://nominatim.openstreetmap.org';
const montrealBox = '-73.98,45.71,-73.47,45.40';

const shortLabel = (place) => {
  const a = place.address || {};
  const street = [a.house_number, a.road].filter(Boolean).join(' ');
  const name = place.name && place.name !== a.road ? place.name : '';
  const area = a.neighbourhood || a.suburb || a.city_district || a.city || '';
  return [name, street, area].filter(Boolean).join(', ') || place.display_name.split(',').slice(0, 3).join(',');
};

export async function searchAddress(query, signal) {
  const params = new URLSearchParams({
    q: query,
    format: 'jsonv2',
    addressdetails: '1',
    limit: '5',
    countrycodes: 'ca',
    viewbox: montrealBox,
    bounded: '1',
  });
  const response = await fetch(`${NOMINATIM}/search?${params}`, { signal, headers: { Accept: 'application/json' } });
  if (!response.ok) throw new Error('Address search is unavailable. Pick a suggested address or tap the map.');
  const places = await response.json();
  return places.map((place) => ({
    label: shortLabel(place),
    latitude: Number(place.lat),
    longitude: Number(place.lon),
  }));
}

export async function reverseAddress(latitude, longitude) {
  const params = new URLSearchParams({ lat: latitude, lon: longitude, format: 'jsonv2', addressdetails: '1', zoom: '18' });
  try {
    const response = await fetch(`${NOMINATIM}/reverse?${params}`, { headers: { Accept: 'application/json' } });
    if (!response.ok) throw new Error();
    return shortLabel(await response.json());
  } catch {
    return `Pinned location (${latitude.toFixed(4)}, ${longitude.toFixed(4)})`;
  }
}
