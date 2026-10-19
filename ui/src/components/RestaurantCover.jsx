import { useId } from 'react';

const sprinkleColors = ['#f25c8a', '#ffd166', '#5cc8f2', '#8d6bf2', '#ffffff'];

const sprinkles = (width, top, height, count) =>
  Array.from({ length: Math.round((count * width) / 400) }, (_, index) => ({
    x: (index * 97) % width,
    y: top + ((index * 61 + (index % 3) * 23) % height),
    rotate: (index * 47) % 180,
    color: sprinkleColors[index % sprinkleColors.length],
  }));

const sprinkleRect = (sprinkle, width, height, color) => (
  <rect
    key={`${sprinkle.x}-${sprinkle.y}`}
    x={sprinkle.x}
    y={sprinkle.y}
    width={width}
    height={height}
    rx={height / 2}
    fill={color || sprinkle.color}
    transform={`rotate(${sprinkle.rotate} ${sprinkle.x + width / 2} ${sprinkle.y + height / 2})`}
  />
);

const themes = [
  {
    name: 'strawberry',
    draw: (id, width) => (
      <>
        <defs>
          <pattern id={id} width="36" height="36" patternUnits="userSpaceOnUse">
            <circle cx="9" cy="9" r="5" fill="#fff5f8" />
            <circle cx="27" cy="27" r="5" fill="#fff5f8" />
          </pattern>
        </defs>
        <rect width={width} height="300" fill="#f7a8c4" />
        <rect width={width} height="300" fill={`url(#${id})`} opacity="0.7" />
      </>
    ),
  },
  {
    name: 'chocolate',
    draw: (id, width) => (
      <>
        <defs>
          <pattern id={id} width="80" height="300" patternUnits="userSpaceOnUse">
            <path
              d="M0 0H80V50H70C68 50 68 52 68 56V72C68 77 64 80 60 80S52 77 52 72V56C52 52 52 50 50 50H32C30 50 30 52 30 56V95C30 101 26 105 20 105S10 101 10 95V56C10 52 10 50 8 50H0Z"
              fill="#f6e3cf"
            />
          </pattern>
        </defs>
        <rect width={width} height="300" fill="#5a3226" />
        <rect width={width} height="300" fill={`url(#${id})`} />
        {sprinkles(width, 8, 34, 14).map((sprinkle) =>
          sprinkleRect(sprinkle, 12, 4, sprinkle.color === '#ffffff' ? '#f25c8a' : null),
        )}
      </>
    ),
  },
  {
    name: 'mint',
    draw: (id, width) => (
      <>
        <rect width={width} height="300" fill="#bfe8d6" />
        {sprinkles(width, 0, 290, 34).map((sprinkle) => sprinkleRect(sprinkle, 14, 5))}
      </>
    ),
  },
  {
    name: 'lemon',
    draw: (id, width) => (
      <>
        <defs>
          <pattern id={id} width="44" height="44" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
            <rect width="22" height="44" fill="#ffe066" />
          </pattern>
        </defs>
        <rect width={width} height="300" fill="#fff4b8" />
        <rect width={width} height="300" fill={`url(#${id})`} />
      </>
    ),
  },
  {
    name: 'blueberry',
    draw: (id, width) => (
      <>
        <defs>
          <pattern id={id} width="80" height="32" patternUnits="userSpaceOnUse">
            <path d="M0 16c10-12 30-12 40 0s30 12 40 0" fill="none" stroke="#f3f4ff" strokeWidth="5" strokeLinecap="round" />
          </pattern>
        </defs>
        <rect width={width} height="300" fill="#c9cffd" />
        <rect width={width} height="300" fill={`url(#${id})`} opacity="0.8" />
      </>
    ),
  },
  {
    name: 'caramel',
    draw: (id, width) => (
      <>
        <defs>
          <pattern id={`${id}stars`} width="60" height="60" patternUnits="userSpaceOnUse">
            <path d="m30 18 3.5 8 8.5.8-6.4 5.6 1.9 8.4-7.5-4.4-7.5 4.4 1.9-8.4-6.4-5.6 8.5-.8Z" fill="#fff3e3" opacity="0.75" />
          </pattern>
          <pattern id={`${id}scallop`} width="40" height="40" patternUnits="userSpaceOnUse">
            <circle cx="20" cy="20" r="20" fill="#fff3e3" />
          </pattern>
        </defs>
        <rect width={width} height="300" fill="#f5bf86" />
        <rect width={width} height="240" fill={`url(#${id}stars)`} />
        <rect y="240" width={width} height="40" fill={`url(#${id}scallop)`} />
        <rect y="260" width={width} height="40" fill="#fff3e3" />
      </>
    ),
  },
  {
    name: 'raspberry',
    draw: (id, width) => (
      <>
        <defs>
          <pattern id={id} width="40" height="40" patternUnits="userSpaceOnUse">
            <rect width="20" height="40" fill="#ffffff" opacity="0.14" />
            <rect width="40" height="20" fill="#ffffff" opacity="0.14" />
          </pattern>
        </defs>
        <rect width={width} height="300" fill="#c2416b" />
        <rect width={width} height="300" fill={`url(#${id})`} />
      </>
    ),
  },
  {
    name: 'vanilla',
    draw: (id, width) => (
      <>
        <defs>
          <pattern id={id} width="70" height="70" patternUnits="userSpaceOnUse">
            <path d="M18 24c-4-5-11-1-8 5l8 7 8-7c3-6-4-10-8-5Z" fill="#f4a3bd" />
            <circle cx="52" cy="50" r="4" fill="#ffd166" />
            <circle cx="50" cy="16" r="2.5" fill="#c2416b" />
            <circle cx="16" cy="58" r="2.5" fill="#8d6bf2" />
          </pattern>
        </defs>
        <rect width={width} height="300" fill="#fff4e4" />
        <rect width={width} height="300" fill={`url(#${id})`} />
      </>
    ),
  },
];

export default function RestaurantCover({ name, index, wide = false }) {
  const id = `cover${useId().replace(/[^a-zA-Z0-9]/g, '')}`;
  const theme = themes[Math.max(0, index) % themes.length];
  const width = wide ? 1000 : 400;
  return (
    <span className={`restaurant-cover cover-${theme.name}`}>
      <svg viewBox={`0 0 ${width} 300`} preserveAspectRatio="xMidYMid slice" aria-hidden="true">
        {theme.draw(id, width)}
      </svg>
      <span className="cover-name">{name}</span>
    </span>
  );
}
