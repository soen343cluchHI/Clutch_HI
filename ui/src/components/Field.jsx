import { useId, useState } from 'react';

export default function Field({ label, hint, type = 'text', ...input }) {
  const id = useId();
  const [visible, setVisible] = useState(false);
  const isPassword = type === 'password';

  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <div className="field-control">
        <input id={id} type={isPassword && visible ? 'text' : type} aria-describedby={hint ? `${id}-hint` : undefined} {...input} />
        {isPassword && (
          <button type="button" className="field-toggle" aria-controls={id} onClick={() => setVisible(!visible)}>
            {visible ? 'Hide' : 'Show'}
          </button>
        )}
      </div>
      {hint && <p id={`${id}-hint`} className="field-hint">{hint}</p>}
    </div>
  );
}
