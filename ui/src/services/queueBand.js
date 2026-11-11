// R-DASH-03: three queue-colour bands for restaurants.
export function queueBand(restaurant) {
  if (restaurant.status !== 'OPEN' || restaurant.queueLength >= 6) return 'long';
  if (restaurant.queueLength >= 3) return 'building';
  return 'short';
}

export const bandLabels = {
  short: 'Short queue',
  building: 'Queue building',
  long: 'Long queue',
};

export const bandRanges = {
  short: '0–2 queued',
  building: '3–5 queued',
  long: '6+ queued or paused',
};
