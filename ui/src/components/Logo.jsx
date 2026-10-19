const sprinkles = [
  { x: 17, y: 15, rotate: -30, color: '#e2557f' },
  { x: 29, y: 14, rotate: 25, color: '#6b3f2e' },
  { x: 33, y: 24, rotate: 80, color: '#e2557f' },
  { x: 28, y: 33, rotate: -20, color: '#6fb58e' },
  { x: 17, y: 32, rotate: 40, color: '#6b3f2e' },
  { x: 13, y: 23, rotate: 100, color: '#6fb58e' },
];

export default function Logo({ size = 40 }) {
  return (
    <span className="logo">
      <svg className="logo-mark" width={size} height={size} viewBox="0 0 48 48" aria-hidden="true">
        <rect width="48" height="48" rx="14" fill="var(--accent)" />
        <g transform="translate(24 24) scale(1.2) translate(-24 -24)">
          <circle cx="24" cy="24" r="15" fill="#e9b673" />
          <path
            d="M24 11c3 0 4 1.5 6.5 2.3 2.4.8 4.6 2.4 5 5 .4 2.5 1.8 3.4 1.8 5.7s-1.6 3.6-2.2 6c-.6 2.4-2.3 3.9-4.8 4.6-2.5.7-3.4 2.4-6.3 2.4s-4-1.7-6.4-2.5c-2.4-.8-4.3-2.3-4.9-4.7-.6-2.4-2-3.5-2-5.8s1.5-3.3 2-5.8c.5-2.5 2.6-4 5-4.8C20 12.5 21 11 24 11Z"
            fill="#fff6ee"
          />
          <circle cx="24" cy="24" r="5" fill="var(--accent)" />
          {sprinkles.map((sprinkle) => (
            <rect
              key={`${sprinkle.x}-${sprinkle.y}`}
              x={sprinkle.x - 2.5}
              y={sprinkle.y - 1}
              width="5"
              height="2"
              rx="1"
              fill={sprinkle.color}
              transform={`rotate(${sprinkle.rotate} ${sprinkle.x} ${sprinkle.y})`}
            />
          ))}
        </g>
      </svg>
      <span className="logo-name" translate="no">
        SugarRush
      </span>
    </span>
  );
}
