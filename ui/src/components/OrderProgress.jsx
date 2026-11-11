const steps = ['Placed', 'Accepted', 'Preparing', 'Ready', 'PickedUp', 'Delivered'];
const labels = { PickedUp: 'Picked up' };
const terminal = {
  Rejected: 'The restaurant rejected this order.',
  Cancelled: 'This order was cancelled.',
  Refunded: 'This order was delivered and then refunded.',
};

export const stateLabel = (state) => labels[state] || state;

export default function OrderProgress({ state, compact = false }) {
  if (terminal[state] && state !== 'Refunded') return <p className="progress-note">{terminal[state]}</p>;
  const reached = state === 'Refunded' ? steps.length - 1 : steps.indexOf(state);
  return (
    <ol className={`order-progress${compact ? ' compact' : ''}`} aria-label={`Order status: ${stateLabel(state)}`}>
      {steps.map((step, index) => (
        <li
          key={step}
          className={index < reached || state === 'Delivered' || state === 'Refunded' ? 'done' : index === reached ? 'now' : ''}
          aria-current={index === reached ? 'step' : undefined}
        >
          <b />
          <span>{stateLabel(step)}</span>
        </li>
      ))}
    </ol>
  );
}
