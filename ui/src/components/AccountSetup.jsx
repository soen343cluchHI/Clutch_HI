import { useState } from 'react';

const types = ['Customer', 'Restaurant', 'Courier', 'Operator'];

// Shown to an account that never chose an account type (created before roles existed, or a sign-up that stopped
// halfway). The choice is saved once and cannot be changed afterwards.
export default function AccountSetup({ disabled, onChoose }) {
  const [role, setRole] = useState('Customer');
  const [name, setName] = useState('');

  return (
    <section className="panel setup-panel">
      <h2>Finish setting up your account</h2>
      <p className="muted">
        Your account has no account type yet, so it works as a customer account. Choose a type to finish setting up. You
        can’t change it afterwards.
      </p>
      <form
        className="stack"
        onSubmit={(event) => {
          event.preventDefault();
          onChoose(role, role === 'Restaurant' ? name.trim() : '');
        }}
      >
        <div className="row wrap" role="radiogroup" aria-label="Account type">
          {types.map((type) => (
            <label key={type} className={`setup-choice${role === type ? ' on' : ''}`}>
              <input type="radio" name="accountType" value={type} checked={role === type} onChange={() => setRole(type)} />
              {type}
            </label>
          ))}
        </div>
        {role === 'Restaurant' && (
          <input required maxLength="80" aria-label="Restaurant name" placeholder="Restaurant name" value={name} onChange={(e) => setName(e.target.value)} />
        )}
        <div>
          <button className="f-btn primary" disabled={disabled}>
            Save account type
          </button>
        </div>
      </form>
    </section>
  );
}
