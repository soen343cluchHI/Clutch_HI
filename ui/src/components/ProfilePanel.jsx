import { useEffect, useRef, useState } from 'react';

const fieldLabels = {
  displayName: 'Full name',
  phone: 'Phone number',
  deliveryAddress: 'Delivery address',
  vehicle: 'Vehicle',
};

const vehicles = ['Bike', 'Scooter', 'Car', 'On foot'];

const roleNames = {
  CUSTOMER: 'Customer',
  RESTAURANT: 'Restaurant owner',
  COURIER: 'Courier',
  OPERATOR: 'Platform operator',
};

export default function ProfilePanel({ session, send, disabled, onClose }) {
  const dialog = useRef(null);
  const [profile, setProfile] = useState(null);
  const [draft, setDraft] = useState({});
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    dialog.current.showModal();
  }, []);

  useEffect(() => {
    if (!session) return;
    send(session, 'VIEW_PROFILE')
      .then(setProfile)
      .catch((failure) => setError(failure.message));
  }, [session]);

  function startEditing() {
    setDraft(Object.fromEntries(profile.editableFields.map((field) => [field, profile.profile[field]])));
    setEditing(true);
    setMessage('');
    setError('');
  }

  async function save(event) {
    event.preventDefault();
    setSaving(true);
    setError('');
    try {
      const fields = Object.fromEntries(Object.entries(draft).map(([field, value]) => [`profile.${field}`, value]));
      setProfile(await send(session, 'UPDATE_PROFILE', fields));
      setEditing(false);
      setMessage('Your profile was saved.');
    } catch (failure) {
      setError(failure.message);
    } finally {
      setSaving(false);
    }
  }

  const changed = Object.entries(draft).some(([field, value]) => value !== profile?.profile[field]);

  function content() {
    if (!profile) return <p className="dash-muted">{error || 'Loading your profile...'}</p>;
    if (editing) return form();
    return details();
  }

  function form() {
    return (
      <form className="profile-form" onSubmit={save}>
        {profile.editableFields.map((field) => (
          <label key={field}>
            {fieldLabels[field]}
            {field === 'vehicle' ? (
              <select value={draft.vehicle} onChange={(e) => setDraft({ ...draft, vehicle: e.target.value })}>
                <option value="">Not set</option>
                {vehicles.map((vehicle) => (
                  <option key={vehicle}>{vehicle}</option>
                ))}
              </select>
            ) : (
              <input
                type={field === 'phone' ? 'tel' : 'text'}
                maxLength={field === 'deliveryAddress' ? 160 : field === 'phone' ? 20 : 80}
                autoComplete={field === 'displayName' ? 'name' : field === 'phone' ? 'tel' : 'street-address'}
                value={draft[field]}
                onChange={(e) => setDraft({ ...draft, [field]: e.target.value })}
              />
            )}
          </label>
        ))}
        {error && <p className="form-alert">{error}</p>}
        <div className="profile-actions">
          <button className="btn btn-primary" disabled={disabled || saving || !changed}>
            {saving ? 'Saving…' : 'Save changes'}
          </button>
          <button className="btn btn-secondary" type="button" disabled={saving} onClick={() => setEditing(false)}>
            Cancel
          </button>
        </div>
      </form>
    );
  }

  function details() {
    return (
      <div className="profile-view">
        <dl className="details">
          <div>
            <dt>Email</dt>
            <dd>{profile.email}</dd>
          </div>
          <div>
            <dt>Account type</dt>
            <dd>{roleNames[profile.role] || profile.role}</dd>
          </div>
          {profile.editableFields.map((field) => (
            <div key={field}>
              <dt>{fieldLabels[field]}</dt>
              <dd className={profile.profile[field] ? '' : 'profile-empty'}>{profile.profile[field] || 'Not set'}</dd>
            </div>
          ))}
        </dl>
        {message && <p className="form-success">{message}</p>}
        {error && <p className="form-alert">{error}</p>}
        <button className="btn btn-primary" disabled={disabled} onClick={startEditing}>
          Edit profile
        </button>
      </div>
    );
  }

  return (
    <dialog
      ref={dialog}
      className="profile-dialog"
      aria-labelledby="profile-dialog-title"
      onClose={onClose}
      onClick={(e) => e.target === dialog.current && dialog.current.close()}
    >
      <div className="dash-section-title">
        <h2 id="profile-dialog-title">My profile</h2>
        <button className="btn-link" type="button" onClick={() => dialog.current.close()}>
          Close
        </button>
      </div>
      {content()}
    </dialog>
  );
}
